package vn.campvivo.notification.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.campvivo.notification.NotificationDispatcher;
import vn.campvivo.notification.NotificationMessages;
import vn.campvivo.notification.NotificationRefType;
import vn.campvivo.notification.NotificationService;
import vn.campvivo.notification.NotificationType;
import vn.campvivo.review.ReviewFlaggedEvent;
import vn.campvivo.review.ReviewModeratedEvent;
import vn.campvivo.review.ReviewQueryService;

/**
 * `REVIEW_FLAGGED` (D-43): MỘT dòng gộp cho mỗi nhân viên, nội dung là số đánh giá đang chờ kiểm duyệt. Chạy SAU commit trên
 * {@link NotificationDispatcher}; số luôn đếm lại lúc xử lý (sự kiện tới lệch thứ tự vẫn ra đúng số cuối cùng).
 * <ul>
 *   <li>Đánh giá mới bị gắn cờ → báo / gộp ("Có N đánh giá…", chưa đọc lại). Nhân viên hiện lại một đánh giá SENSITIVE (vào
 *       lại hàng chờ) → báo cho các nhân viên khác.</li>
 *   <li>Hàng chờ đổi do kiểm duyệt (ẩn, đánh dấu an toàn, xoá, khách sửa bỏ từ nhạy cảm…) → còn N &gt; 0: chỉ sửa số trên dòng
 *       đang mở, không báo lại; về 0: đã xử lý, người xử lý = nhân viên thao tác cuối (null = do khách → Hệ thống).</li>
 * </ul>
 */
@Component
public class ReviewNotificationListener {

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;
    private final ReviewQueryService reviews;

    public ReviewNotificationListener(NotificationService notifications, NotificationDispatcher dispatcher,
                                      ReviewQueryService reviews) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
        this.reviews = reviews;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlagged(ReviewFlaggedEvent event) {
        dispatcher.submit("REVIEW_FLAGGED đánh giá id=" + event.reviewId(), () -> {
            long pending = reviews.countPendingSensitive();
            if (pending > 0) {
                notifications.notifyStaff(NotificationMessages.reviewFlagged(pending), null);
            } else {
                resolve(null);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onModerated(ReviewModeratedEvent event) {
        dispatcher.submit("REVIEW_FLAGGED kiểm duyệt đánh giá id=" + event.reviewId(), () -> {
            long pending = reviews.countPendingSensitive();
            if (pending > 0 && event.addedToQueue()) {
                notifications.notifyStaff(NotificationMessages.reviewFlagged(pending), event.staffId());
            } else if (pending > 0) {
                notifications.refreshGroup(NotificationMessages.reviewFlagged(pending));
            } else {
                resolve(event.staffId());
            }
        });
    }

    private void resolve(Long staffId) {
        notifications.resolve(NotificationType.REVIEW_FLAGGED, NotificationRefType.REVIEW_QUEUE, null, staffId);
    }
}
