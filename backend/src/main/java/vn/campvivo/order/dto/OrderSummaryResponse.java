package vn.campvivo.order.dto;

import vn.campvivo.order.Order;
import vn.campvivo.order.OrderItem;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.payment.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** `OrderSummary` (api.md 4.5) — một dòng trong danh sách đơn của khách. */
public record OrderSummaryResponse(
        Long id,
        String orderCode,
        OrderStatus status,
        PaymentMethod paymentMethod,
        BigDecimal finalAmount,
        int itemCount,
        String firstItemName,
        String firstItemImageUrl,
        OffsetDateTime createdAt
) {
    public static OrderSummaryResponse from(Order o) {
        OrderItem first = o.getItems().isEmpty() ? null : o.getItems().get(0);
        return new OrderSummaryResponse(o.getId(), o.getOrderCode(), o.getStatus(), o.getPaymentMethod(), o.getFinalAmount(),
                o.totalQuantity(), first == null ? null : first.getItemName(), first == null ? null : first.getItemImageUrl(),
                o.getCreatedAt());
    }
}
