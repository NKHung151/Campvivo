package vn.campvivo.notification;

/**
 * Loại đối tượng mà thông báo nói tới — `notifications.ref_type` (database.md 3.20, D-43). Cùng `ref_id` dùng để đánh dấu
 * "đã xử lý" mọi dòng của một đối tượng. `CONVERSATION` → `ref_id` = id khách; `REVIEW_QUEUE` → `ref_id` = NULL.
 */
public enum NotificationRefType {
    ORDER,
    RETURN,
    CONVERSATION,
    PRODUCT,
    COMBO,
    VOUCHER,
    CAMPAIGN,
    REVIEW_QUEUE
}
