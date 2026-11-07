package vn.campvivo.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bản ghi thanh toán của đơn (database.md 3.12, 5.3) và chọn cổng theo phương thức (D-08).
 * Không tự đổi trạng thái đơn — kết quả cổng xử lý ở PaymentResultService.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final Map<PaymentMethod, PaymentGateway> gateways = new EnumMap<>(PaymentMethod.class);
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository, List<PaymentGateway> gateways, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.clock = clock;
        gateways.forEach(g -> this.gateways.put(g.method(), g));
    }

    /** Tạo cùng đơn (PENDING), số tiền = final_amount. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment createFor(Long orderId, PaymentMethod method, BigDecimal amount) {
        return paymentRepository.save(new Payment(orderId, method, amount, OffsetDateTime.now(clock)));
    }

    /** Phương thức có cổng online (VNPay) hay thanh toán khi nhận hàng (COD). */
    public boolean requiresGateway(PaymentMethod method) {
        return method != PaymentMethod.COD;
    }

    public PaymentGateway gateway(PaymentMethod method) {
        PaymentGateway gateway = gateways.get(method);
        if (gateway == null) {
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
        return gateway;
    }

    public String createPaymentUrl(PaymentMethod method, PaymentGateway.PaymentRequest request) {
        return gateway(method).createPaymentUrl(request);
    }

    /** Đơn COD COMPLETED: đã thu tiền (database.md 5.1). Gọi khi đã khoá đơn. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markCodCollected(Long orderId) {
        paymentRepository.findByOrderId(orderId)
                .filter(p -> p.getMethod() == PaymentMethod.COD && p.getStatus() == PaymentStatus.PENDING)
                .ifPresent(p -> p.markSuccess(null, null, OffsetDateTime.now(clock)));
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    /** Trạng thái thanh toán của nhiều đơn (danh sách đơn admin). */
    @Transactional(readOnly = true)
    public Map<Long, PaymentStatus> statusesByOrderIds(Collection<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PaymentStatus> out = new HashMap<>();
        paymentRepository.findByOrderIdIn(orderIds).forEach(p -> out.put(p.getOrderId(), p.getStatus()));
        return out;
    }
}
