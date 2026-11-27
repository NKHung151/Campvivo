package vn.campvivo.notification;

/**
 * Loại thông báo — khớp `notifications.type` (docs/02-ky-thuat/database.md 3.20) và `NotificationType` ở api.md (D-42).
 * `audience` = ai nhận; `actionable` = loại cần nhân viên xử lý, có `resolved_at` / `resolved_by` (D-43).
 */
public enum NotificationType {
    // Khách
    ORDER_STATUS(Audience.CUSTOMER, false),
    RETURN_STATUS(Audience.CUSTOMER, false),
    SUPPORT_MESSAGE(Audience.CUSTOMER, false),
    VOUCHER_NEW(Audience.CUSTOMER, false),
    FAVORITE_ON_SALE(Audience.CUSTOMER, false),
    FAVORITE_BACK_IN_STOCK(Audience.CUSTOMER, false),
    // Nhân viên (EMPLOYEE, ADMIN)
    ORDER_NEW(Audience.STAFF, true),
    ORDER_PAID(Audience.STAFF, true),
    RETURN_NEW(Audience.STAFF, true),
    SUPPORT_NEW(Audience.STAFF, true),
    LOW_STOCK(Audience.STAFF, true),
    REVIEW_FLAGGED(Audience.STAFF, true);

    public enum Audience { CUSTOMER, STAFF }

    private final Audience audience;
    private final boolean actionable;

    NotificationType(Audience audience, boolean actionable) {
        this.audience = audience;
        this.actionable = actionable;
    }

    public Audience audience() {
        return audience;
    }

    /** Cần xử lý: đọc KHÔNG đóng dòng gộp, chỉ "đã xử lý" mới đóng (D-43). Loại còn lại đóng dòng gộp khi đọc. */
    public boolean actionable() {
        return actionable;
    }
}
