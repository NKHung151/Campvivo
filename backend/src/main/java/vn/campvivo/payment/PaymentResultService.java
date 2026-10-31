package vn.campvivo.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.order.Order;
import vn.campvivo.order.OrderStateService;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.payment.PaymentGateway.PaymentCallback;
import vn.campvivo.payment.PaymentResult.Outcome;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Xử lý kết quả thanh toán DÙNG CHUNG cho Return URL và IPN (BR-12, D-28), theo đúng thứ tự VNPay yêu cầu:
 * 1. kiểm chữ ký → 2. tìm đơn theo mã (vnp_TxnRef) và khoá dòng `orders` → 3. số tiền khớp `payments.amount`
 * → 4. thanh toán đã xử lý (≠ PENDING) thì bỏ qua (idempotent) → 5. ghi nhận:
 *    thành công (ResponseCode = TransactionStatus = "00") → payment SUCCESS, đơn PENDING → CONFIRMED (trừ kho); sự kiện
 *    CONFIRMED kích hoạt tạo vận đơn GHN sau commit (OrderShipmentListener);
 *    thất bại → payment FAILED, đơn → PAYMENT_FAILED (trả giữ chỗ).
 * Return URL và IPN có thể về cùng lúc: khoá đơn khiến chúng chạy lần lượt, lần sau thấy ALREADY_PROCESSED.
 */
@Service
public class PaymentResultService {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultService.class);

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final OrderStateService orderStateService;
    private final Clock clock;

    public PaymentResultService(PaymentService paymentService, PaymentRepository paymentRepository,
                                OrderStateService orderStateService, Clock clock) {
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        this.orderStateService = orderStateService;
        this.clock = clock;
    }

    @Transactional
    public PaymentResult handlePaymentResult(PaymentMethod method, Map<String, String> params) {
        PaymentCallback callback = paymentService.gateway(method).parseCallback(params);
        if (!callback.signatureValid()) {
            log.warn("Callback {} sai chữ ký (txnRef={})", method, callback.txnRef());
            return PaymentResult.of(Outcome.INVALID_SIGNATURE);
        }

        Optional<Order> locked = orderStateService.lockByCode(callback.txnRef());
        Optional<Payment> found = locked.flatMap(o -> paymentRepository.findByOrderId(o.getId()))
                .filter(p -> p.getMethod() == method);
        if (found.isEmpty()) {
            log.warn("Callback {} cho đơn không tồn tại: {}", method, callback.txnRef());
            return PaymentResult.of(Outcome.ORDER_NOT_FOUND);
        }
        Order order = locked.get();
        Payment payment = found.get();

        if (callback.amount() == null || callback.amount().compareTo(payment.getAmount()) != 0) {
            log.warn("Callback {} sai số tiền cho đơn {}", method, order.getOrderCode());
            return result(Outcome.AMOUNT_MISMATCH, order, payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return result(Outcome.ALREADY_PROCESSED, order, payment);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (callback.success()) {
            payment.markSuccess(callback.transactionNo(), callback.payload(), now);
            if (order.getStatus() == OrderStatus.PENDING) {
                orderStateService.apply(order, OrderStatus.CONFIRMED, null,
                        "Đã thanh toán " + method + " (mã giao dịch " + callback.transactionNo() + ")");
            } else {
                // KI-01: tiền đã trừ nhưng đơn đã huỷ do quá hạn — hoàn tiền thủ công
                log.warn("Đơn {} đã {} nhưng {} báo thanh toán thành công (GD {}) — cần hoàn tiền thủ công",
                        order.getOrderCode(), order.getStatus(), method, callback.transactionNo());
            }
            return result(Outcome.SUCCESS, order, payment);
        }

        payment.markFailed(callback.payload(), now);
        if (order.getStatus() == OrderStatus.PENDING) {
            orderStateService.apply(order, OrderStatus.PAYMENT_FAILED, null,
                    "Thanh toán " + method + " không thành công (mã " + callback.responseCode() + ")");
        }
        return result(Outcome.FAILED, order, payment);
    }

    private static PaymentResult result(Outcome outcome, Order order, Payment payment) {
        return new PaymentResult(outcome, order.getId(), order.getOrderCode(), order.getStatus(), payment.getStatus());
    }
}
