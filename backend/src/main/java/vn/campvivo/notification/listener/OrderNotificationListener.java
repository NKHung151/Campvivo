package vn.campvivo.notification.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.campvivo.notification.NotificationDispatcher;
import vn.campvivo.notification.NotificationMessages;
import vn.campvivo.notification.NotificationRefType;
import vn.campvivo.notification.NotificationService;
import vn.campvivo.notification.NotificationType;
import vn.campvivo.order.OrderQueryService;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.order.dto.OrderNotificationInfo;
import vn.campvivo.order.event.OrderStatusChangedEvent;
import vn.campvivo.payment.PaymentMethod;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Thông báo theo trạng thái đơn (D-42, D-43), chạy SAU commit trên {@link NotificationDispatcher} — đơn bị rollback thì không
 * có thông báo:
 * <ul>
 *   <li>Đơn COD vừa tạo → `ORDER_NEW` cho mọi nhân viên (đơn VNPay chưa trả tiền không báo).</li>
 *   <li>Đơn rời PENDING → `ORDER_NEW` đã xử lý (người xử lý = nhân viên; khách tự huỷ / Hệ thống → không ghi người).</li>
 *   <li>Đơn VNPay PENDING → CONFIRMED (IPN) → `ORDER_PAID` cho mọi nhân viên; CONFIRMED → SHIPPING → `ORDER_PAID` đã xử lý.</li>
 *   <li>Khách: `ORDER_STATUS` khi sang CONFIRMED, SHIPPING, COMPLETED, CANCELLED, PAYMENT_FAILED — trừ khi chính khách đổi
 *       (tự huỷ). RETURNED không gửi vì đã có `RETURN_STATUS`.</li>
 * </ul>
 */
@Component
public class OrderNotificationListener {

    static final Set<OrderStatus> CUSTOMER_NOTIFIED = EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPING,
            OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED);

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;
    private final OrderQueryService orders;

    public OrderNotificationListener(NotificationService notifications, NotificationDispatcher dispatcher,
                                     OrderQueryService orders) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
        this.orders = orders;
    }

    /**
     * Thông tin đơn đọc lúc việc chạy (có thể sau vài lần đổi trạng thái khác); chỉ `lastNote` phụ thuộc thời điểm và nó chỉ
     * được dùng cho CANCELLED — trạng thái cuối, không đổi tiếp.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        dispatcher.submit("đơn id=" + event.orderId() + " " + event.oldStatus() + " → " + event.newStatus(),
                () -> orders.notificationInfo(event.orderId()).ifPresent(info -> handle(event, info)));
    }

    private void handle(OrderStatusChangedEvent event, OrderNotificationInfo info) {
        boolean byCustomer = Objects.equals(event.actorId(), info.customerId());
        Long staffActor = byCustomer ? null : event.actorId();

        if (event.oldStatus() == null) {
            if (info.paymentMethod() == PaymentMethod.COD) {
                notifications.notifyStaff(NotificationMessages.orderNew(info.orderId(), info.orderCode(),
                        info.finalAmount(), info.itemCount()), null);
            }
            return;
        }

        if (event.oldStatus() == OrderStatus.PENDING) {
            notifications.resolve(NotificationType.ORDER_NEW, NotificationRefType.ORDER, info.orderId(), staffActor);
            if (event.newStatus() == OrderStatus.CONFIRMED && info.paymentMethod() != PaymentMethod.COD) {
                notifications.notifyStaff(NotificationMessages.orderPaid(info.orderId(), info.orderCode(),
                        info.finalAmount()), null);
            }
        }
        if (event.oldStatus() == OrderStatus.CONFIRMED && event.newStatus() == OrderStatus.SHIPPING) {
            notifications.resolve(NotificationType.ORDER_PAID, NotificationRefType.ORDER, info.orderId(), staffActor);
        }

        if (CUSTOMER_NOTIFIED.contains(event.newStatus()) && !byCustomer) {
            notifications.notifyUser(info.customerId(), NotificationMessages.orderStatus(info.orderId(), info.orderCode(),
                    event.newStatus(), info.lastNote()));
        }
    }
}
