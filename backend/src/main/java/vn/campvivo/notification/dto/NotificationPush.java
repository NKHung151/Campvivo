package vn.campvivo.notification.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phong bì đẩy qua `/user/queue/notifications` (api.md mục 4.9, D-43). `CREATED` / `UPDATED` có `notification`;
 * `RESOLVED` có `ids`, `resolvedAt`, `resolvedBy` (null = Hệ thống). `unreadCount` là giá trị mới của chính người nhận.
 */
public record NotificationPush(
        Event event,
        NotificationResponse notification,
        List<Long> ids,
        OffsetDateTime resolvedAt,
        NotificationResponse.Actor resolvedBy,
        long unreadCount
) {

    public enum Event { CREATED, UPDATED, RESOLVED }

    public static NotificationPush written(boolean inserted, NotificationResponse notification, long unreadCount) {
        return new NotificationPush(inserted ? Event.CREATED : Event.UPDATED, notification, null, null, null, unreadCount);
    }

    public static NotificationPush resolved(List<Long> ids, OffsetDateTime resolvedAt, NotificationResponse.Actor resolvedBy,
                                            long unreadCount) {
        return new NotificationPush(Event.RESOLVED, null, ids, NotificationResponse.vn(resolvedAt), resolvedBy, unreadCount);
    }
}
