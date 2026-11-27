package vn.campvivo.notification.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.campvivo.notification.NotificationDispatcher;
import vn.campvivo.notification.NotificationMessages;
import vn.campvivo.notification.NotificationRefType;
import vn.campvivo.notification.NotificationService;
import vn.campvivo.notification.NotificationType;
import vn.campvivo.order.returns.ReturnRequestCreatedEvent;
import vn.campvivo.order.returns.ReturnRequestProcessedEvent;
import vn.campvivo.order.returns.ReturnStatus;

/**
 * Thông báo trả hàng (D-42, D-43), chạy SAU commit trên {@link NotificationDispatcher}: yêu cầu mới → `RETURN_NEW` cho mọi nhân
 * viên; duyệt / từ chối → `RETURN_NEW` đã xử lý (người xử lý = nhân viên đó) + `RETURN_STATUS` cho khách.
 */
@Component
public class ReturnNotificationListener {

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;

    public ReturnNotificationListener(NotificationService notifications, NotificationDispatcher dispatcher) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(ReturnRequestCreatedEvent event) {
        dispatcher.submit("RETURN_NEW yêu cầu id=" + event.returnId(), () ->
                notifications.notifyStaff(NotificationMessages.returnNew(event.returnId(), event.orderCode(),
                        event.reasonLabel()), null));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProcessed(ReturnRequestProcessedEvent event) {
        dispatcher.submit("kết quả trả hàng yêu cầu id=" + event.returnId(), () -> {
            notifications.resolve(NotificationType.RETURN_NEW, NotificationRefType.RETURN, event.returnId(),
                    event.staffId());
            notifications.notifyUser(event.customerId(), NotificationMessages.returnStatus(event.returnId(),
                    event.orderId(), event.orderCode(), event.status() == ReturnStatus.APPROVED, event.note(),
                    event.refundAmount()));
        });
    }
}
