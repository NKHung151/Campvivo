package vn.campvivo.notification.dto;

import vn.campvivo.common.dto.PageResponse;

import java.util.List;

/**
 * `GET /notifications` (api.md 4.7): `{success, data: {items, unreadCount}, pagination}` — khác `PageResponse` thường vì `data`
 * kèm `unreadCount` (badge chuông) trong cùng một lần gọi.
 */
public record NotificationListResponse(boolean success, Data data, PageResponse.Pagination pagination) {

    public record Data(List<NotificationResponse> items, long unreadCount) {}

    public static NotificationListResponse of(PageResponse<NotificationResponse> page, long unreadCount) {
        return new NotificationListResponse(true, new Data(page.data(), unreadCount), page.pagination());
    }
}
