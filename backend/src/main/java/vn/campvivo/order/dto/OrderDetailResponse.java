package vn.campvivo.order.dto;

import vn.campvivo.order.Order;
import vn.campvivo.order.OrderItem;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.order.OrderStatusHistory;
import vn.campvivo.payment.Payment;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * `OrderDetail` (api.md 4.5) + thông tin vận chuyển GHN (`shipping`, D-27/D-28). Review/trả hàng chưa có module nên
 * `canReview`/`canReturn` = false, `returnRequest` = null.
 */
public record OrderDetailResponse(
        Long id,
        String orderCode,
        OrderStatus status,
        PaymentMethod paymentMethod,
        BigDecimal finalAmount,
        int itemCount,
        String firstItemName,
        String firstItemImageUrl,
        OffsetDateTime createdAt,
        List<Item> items,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        String voucherCode,
        String recipientName,
        String recipientPhone,
        String shippingAddress,
        String note,
        PaymentInfo payment,
        Shipping shipping,
        List<History> history,
        Object returnRequest,
        boolean canCancel,
        boolean canReturn,
        OffsetDateTime completedAt
) {
    public record Item(String type, Long itemId, String name, String imageUrl, BigDecimal originalPrice,
                       BigDecimal unitPrice, int quantity, BigDecimal lineTotal, boolean canReview) {}

    public record PaymentInfo(PaymentMethod method, PaymentStatus status, OffsetDateTime paidAt) {}

    /** `trackingCode` = mã vận đơn GHN (null = chưa tạo); `error` = lỗi lần tạo gần nhất. */
    public record Shipping(String carrier, Integer districtId, String wardCode, String trackingCode, String error) {}

    public record History(OrderStatus oldStatus, OrderStatus newStatus, String note, OffsetDateTime createdAt) {}

    public static OrderDetailResponse from(Order o, Payment payment) {
        List<Item> items = o.getItems().stream().map(OrderDetailResponse::item).toList();
        OrderItem first = o.getItems().isEmpty() ? null : o.getItems().get(0);
        PaymentInfo paymentInfo = payment == null
                ? new PaymentInfo(o.getPaymentMethod(), PaymentStatus.PENDING, null)
                : new PaymentInfo(payment.getMethod(), payment.getStatus(), payment.getPaidAt());
        return new OrderDetailResponse(
                o.getId(), o.getOrderCode(), o.getStatus(), o.getPaymentMethod(), o.getFinalAmount(),
                o.totalQuantity(),
                first == null ? null : first.getItemName(),
                first == null ? null : first.getItemImageUrl(),
                o.getCreatedAt(), items, o.getSubtotal(), o.getDiscountAmount(), o.getShippingFee(), o.getVoucherCode(),
                o.getRecipientName(), o.getRecipientPhone(), o.getShippingAddress(), o.getNote(), paymentInfo,
                new Shipping("GHN", o.getShippingDistrictId(), o.getShippingWardCode(),
                        o.getShippingOrderCode(), o.getShippingOrderError()),
                o.getHistory().stream().map(OrderDetailResponse::history).toList(),
                null,
                o.getStatus() == OrderStatus.PENDING,
                false,
                o.getCompletedAt());
    }

    private static Item item(OrderItem i) {
        return new Item(i.isProduct() ? "PRODUCT" : "COMBO", i.isProduct() ? i.getProductId() : i.getComboId(),
                i.getItemName(), i.getItemImageUrl(), i.getOriginalPrice(), i.getUnitPrice(), i.getQuantity(),
                i.getLineTotal(), false);
    }

    private static History history(OrderStatusHistory h) {
        return new History(h.getOldStatus(), h.getNewStatus(), h.getNote(), h.getCreatedAt());
    }
}
