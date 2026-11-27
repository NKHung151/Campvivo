package vn.campvivo.notification.dto;

import vn.campvivo.notification.Notification;
import vn.campvivo.notification.NotificationType;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/** `Notification` ở api.md mục 4.7; cũng nằm trong `NotificationPush` (mục 4.9). */
public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String content,
        String link,
        int eventCount,
        boolean isRead,
        OffsetDateTime createdAt,
        boolean actionable,
        OffsetDateTime resolvedAt,
        Actor resolvedBy
) {

    /** Người đã xử lý; null khi `resolvedAt` có giá trị = Hệ thống. */
    public record Actor(Long id, String fullName) {}

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /** `names`: họ tên theo id người xử lý (thiếu → hiện id, tên rỗng — tài khoản đã bị xoá). */
    public static NotificationResponse from(Notification n, Map<Long, String> names) {
        Actor actor = n.getResolvedBy() == null ? null : new Actor(n.getResolvedBy(), names.getOrDefault(n.getResolvedBy(), ""));
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getContent(), n.getLink(), n.getEventCount(),
                n.isRead(), vn(n.getCreatedAt()), n.getType().actionable(), vn(n.getResolvedAt()), actor);
    }

    public static OffsetDateTime vn(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(VN).toOffsetDateTime();
    }
}
