package vn.campvivo.order;

import java.math.BigDecimal;

/** Khớp response 201 của POST /orders trong docs/02-ky-thuat/api.md. */
public record OrderResponse(
        Long orderId,
        String orderCode,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        String status,
        String paymentUrl
) {
    public static OrderResponse of(Order order, String paymentUrl) {
        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getStatus().name(),
                paymentUrl
        );
    }
}
