package vn.campvivo.notification.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.campvivo.notification.NotificationDispatcher;
import vn.campvivo.notification.NotificationMessages;
import vn.campvivo.notification.NotificationService;
import vn.campvivo.promotion.VoucherAnnouncedEvent;

/**
 * `VOUCHER_NEW` (D-42, D-45): voucher công khai vừa được công bố → mọi khách đang hoạt động, ghi theo lô
 * `app.notification.fan-out-batch-size` (mỗi lô một transaction). Chạy SAU commit của job trên {@link NotificationDispatcher}
 * — job không phải chờ fan-out. `FAVORITE_ON_SALE` từ `CampaignStartedEvent` thêm ở Phần 7.
 */
@Component
public class PromotionNotificationListener {

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;

    public PromotionNotificationListener(NotificationService notifications, NotificationDispatcher dispatcher) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoucherAnnounced(VoucherAnnouncedEvent e) {
        dispatcher.submit("VOUCHER_NEW voucher id=" + e.voucherId(), () ->
                notifications.notifyAllCustomers(NotificationMessages.voucherNew(e.voucherId(), e.code(),
                        e.discountType(), e.discountValue(), e.maxDiscount(), e.minOrderValue(), e.expiryDate())));
    }
}
