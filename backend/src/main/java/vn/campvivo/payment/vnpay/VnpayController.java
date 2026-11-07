package vn.campvivo.payment.vnpay;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.payment.PaymentResult;
import vn.campvivo.payment.PaymentResultService;
import vn.campvivo.payment.PaymentStatus;

import java.util.Map;

/**
 * Callback VNPay (api.md 4.5, D-28) — công khai, mọi tham số được kiểm chữ ký trong handlePaymentResult.
 * - GET /api/vnpay/return: trang frontend /payment/vnpay-return chuyển nguyên query `vnp_*` VNPay trả về.
 * - GET /api/vnpay/ipn: VNPay gọi server-to-server (khai báo URL ở cổng merchant), trả {RspCode, Message}.
 * Cả hai dùng chung một hàm nên gọi lặp, gọi cùng lúc đều an toàn.
 */
@RestController
@RequestMapping("/api/vnpay")
public class VnpayController {

    private static final Logger log = LoggerFactory.getLogger(VnpayController.class);

    private final PaymentResultService paymentResultService;

    public VnpayController(PaymentResultService paymentResultService) {
        this.paymentResultService = paymentResultService;
    }

    /** Kết quả cho trang return. `outcome` để frontend chọn thông báo; trạng thái đơn đọc ở `orderStatus`. */
    public record ReturnResponse(PaymentResult.Outcome outcome, Long orderId, String orderCode,
                                 OrderStatus orderStatus, PaymentStatus paymentStatus) {}

    /** Định dạng phản hồi IPN theo tài liệu VNPay. */
    public record IpnResponse(@JsonProperty("RspCode") String rspCode, @JsonProperty("Message") String message) {}

    @GetMapping("/return")
    public ApiResponse<ReturnResponse> vnpayReturn(@RequestParam Map<String, String> params) {
        PaymentResult r = paymentResultService.handlePaymentResult(PaymentMethod.VNPAY, params);
        return ApiResponse.of(new ReturnResponse(r.outcome(), r.orderId(), r.orderCode(), r.orderStatus(), r.paymentStatus()));
    }

    @GetMapping("/ipn")
    public IpnResponse ipn(@RequestParam Map<String, String> params) {
        try {
            return switch (paymentResultService.handlePaymentResult(PaymentMethod.VNPAY, params).outcome()) {
                case SUCCESS, FAILED -> new IpnResponse("00", "Confirm Success");
                case ALREADY_PROCESSED -> new IpnResponse("02", "Order already confirmed");
                case ORDER_NOT_FOUND -> new IpnResponse("01", "Order not found");
                case AMOUNT_MISMATCH -> new IpnResponse("04", "Invalid amount");
                case INVALID_SIGNATURE -> new IpnResponse("97", "Invalid signature");
            };
        } catch (RuntimeException e) {
            log.error("Lỗi xử lý IPN VNPay", e);
            return new IpnResponse("99", "Unknown error");
        }
    }
}
