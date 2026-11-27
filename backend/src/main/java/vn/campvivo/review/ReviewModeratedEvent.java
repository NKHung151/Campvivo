package vn.campvivo.review;

/**
 * Hàng chờ kiểm duyệt đổi do kiểm duyệt (không phải đánh giá mới bị gắn cờ): nhân viên ẩn / hiện lại / đánh dấu an toàn / xoá
 * một đánh giá, hoặc khách sửa bỏ từ nhạy cảm / xoá đánh giá đang chờ. Phát trong transaction đó.
 *
 * @param staffId      nhân viên thao tác; null = do khách
 * @param addedToQueue true = đánh giá VÀO lại hàng chờ (nhân viên hiện lại một đánh giá SENSITIVE); false = ra khỏi hàng chờ
 */
public record ReviewModeratedEvent(long reviewId, Long staffId, boolean addedToQueue) {}
