package vn.campvivo.notification.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.campvivo.notification.NotificationDispatcher;
import vn.campvivo.notification.NotificationMessages;
import vn.campvivo.notification.NotificationRefType;
import vn.campvivo.notification.NotificationService;
import vn.campvivo.notification.NotificationType;
import vn.campvivo.support.SupportMessageResponse;
import vn.campvivo.support.SupportMessageSentEvent;
import vn.campvivo.user.UserService;
import vn.campvivo.user.dto.UserProfileResponse;

import java.util.List;

/**
 * Thông báo chat hỗ trợ (D-42, D-43), chạy SAU commit trên {@link NotificationDispatcher} — song song với
 * `SupportRealtimeListener` (đẩy chính tin nhắn):
 * <ul>
 *   <li>Tin của khách → `SUPPORT_NEW` cho mọi nhân viên, gộp theo khách (một dòng mở / khách / nhân viên) tới khi có người
 *       trả lời.</li>
 *   <li>Tin của nhân viên → `SUPPORT_NEW` của khách đó chuyển "đã xử lý" ở mọi nhân viên (người xử lý = người trả lời) và khách
 *       nhận `SUPPORT_MESSAGE`, gộp tới khi khách đọc.</li>
 * </ul>
 */
@Component
public class SupportNotificationListener {

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;
    private final UserService userService;

    public SupportNotificationListener(NotificationService notifications, NotificationDispatcher dispatcher,
                                       UserService userService) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
        this.userService = userService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(SupportMessageSentEvent event) {
        SupportMessageResponse message = event.message();
        dispatcher.submit("chat khách id=" + message.customerId() + ", tin id=" + message.id(), () -> {
            if (message.isFromStaff()) {
                notifications.resolve(NotificationType.SUPPORT_NEW, NotificationRefType.CONVERSATION, message.customerId(),
                        event.staffSenderId());
                notifications.notifyUser(message.customerId(),
                        NotificationMessages.supportReply(message.customerId(), message.content()));
            } else {
                notifications.notifyStaff(NotificationMessages.supportNew(message.customerId(),
                        customerName(message.customerId()), message.content()), null);
            }
        });
    }

    private String customerName(Long customerId) {
        UserProfileResponse profile = userService.profiles(List.of(customerId)).get(customerId);
        return profile == null || profile.fullName() == null || profile.fullName().isBlank() ? "Khách hàng" : profile.fullName();
    }
}
