package vn.campvivo.order.returns;

/**
 * Khách vừa gửi yêu cầu trả hàng (PENDING). Listener xử lý SAU commit (D-15) — vd. thông báo `RETURN_NEW` cho nhân viên (D-42).
 */
public record ReturnRequestCreatedEvent(Long returnId, Long orderId, String orderCode, Long customerId, String reasonLabel) {
}
