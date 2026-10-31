package vn.campvivo.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Cổng thanh toán online (architecture.md "Thanh toán mở rộng được", D-08). Thêm MoMo = thêm một bean implement
 * interface này + giá trị PaymentMethod + endpoint callback gọi PaymentResultService.handlePaymentResult —
 * không sửa OrderService.
 */
public interface PaymentGateway {

    PaymentMethod method();

    /** URL chuyển khách sang cổng. Lỗi cấu hình/ký → BusinessException(PAYMENT_GATEWAY_ERROR). */
    String createPaymentUrl(PaymentRequest request);

    /** Kiểm chữ ký và đọc tham số cổng gửi về (Return URL hoặc IPN). Không ném lỗi khi sai chữ ký. */
    PaymentCallback parseCallback(Map<String, String> params);

    /** `txnRef` = mã đơn (duy nhất); `expiresAt` = hạn thanh toán (BR-3). */
    record PaymentRequest(String txnRef, BigDecimal amount, String orderInfo, String clientIp,
                          OffsetDateTime createdAt, OffsetDateTime expiresAt) {}

    /**
     * Kết quả cổng gửi về. `amount` đã quy về VND; `payload` = tham số đã bỏ chữ ký (lưu vào payments.payload).
     * `success` chỉ có nghĩa khi `signatureValid`.
     */
    record PaymentCallback(boolean signatureValid, String txnRef, BigDecimal amount, boolean success,
                           String transactionNo, String responseCode, Map<String, String> payload) {}
}
