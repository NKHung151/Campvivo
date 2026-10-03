package vn.campvivo.order;

/**
 * Máy trạng thái đơn hàng — PHẢI khớp bảng chuyển trạng thái trong
 * docs/02-ky-thuat/database.md. Không thêm trạng thái mới ở đây mà không cập nhật docs.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPING,
    COMPLETED,
    CANCELLED,
    PAYMENT_FAILED
}
