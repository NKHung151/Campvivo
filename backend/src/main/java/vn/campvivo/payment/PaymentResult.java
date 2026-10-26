package vn.campvivo.payment;

import vn.campvivo.order.OrderStatus;

/**
 * Kết quả xử lý callback của cổng thanh toán (PaymentResultService.handlePaymentResult). Đơn không xác định được
 * (sai chữ ký, không tìm thấy) thì các trường đơn/thanh toán null.
 */
public record PaymentResult(Outcome outcome, Long orderId, String orderCode, OrderStatus orderStatus,
                            PaymentStatus paymentStatus) {

    public enum Outcome {
        /** Thanh toán thành công, vừa ghi nhận lần này. */
        SUCCESS,
        /** Khách huỷ / thanh toán lỗi, vừa ghi nhận lần này. */
        FAILED,
        /** Đã xử lý trước đó (IPN và Return URL cùng về, hoặc VNPay gọi lại) — không làm gì thêm. */
        ALREADY_PROCESSED,
        INVALID_SIGNATURE,
        ORDER_NOT_FOUND,
        AMOUNT_MISMATCH
    }

    public static PaymentResult of(Outcome outcome) {
        return new PaymentResult(outcome, null, null, null, null);
    }
}
