package vn.campvivo.order.event;

import vn.campvivo.order.OrderStatus;

/**
 * Phát mỗi lần đơn đổi trạng thái, kể cả lúc tạo (oldStatus = null). Listener xử lý side-effect SAU commit
 * (@TransactionalEventListener AFTER_COMMIT — D-15): tạo vận đơn GHN khi CONFIRMED; sau này thông báo, email.
 * `actorId` null = System.
 */
public record OrderStatusChangedEvent(Long orderId, OrderStatus oldStatus, OrderStatus newStatus, Long actorId) {}
