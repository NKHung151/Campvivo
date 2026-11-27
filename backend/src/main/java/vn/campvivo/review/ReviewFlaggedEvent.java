package vn.campvivo.review;

/**
 * Khách viết / sửa đánh giá và kiểm duyệt từ điển gắn SENSITIVE (D-33) — đánh giá vào hàng chờ kiểm duyệt của nhân viên
 * (bộ lọc `status=SENSITIVE` của `/admin/reviews`). Phát trong transaction ghi đánh giá.
 */
public record ReviewFlaggedEvent(long reviewId) {}
