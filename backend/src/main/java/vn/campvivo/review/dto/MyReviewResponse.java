package vn.campvivo.review.dto;

import vn.campvivo.review.Review;

import java.time.OffsetDateTime;

/** `MyReview` (api.md mục 4.6): đánh giá của chính khách, kể cả khi bị ẩn (kèm lý do — D-31). */
public record MyReviewResponse(
        Long id,
        String userFullName,
        int rating,
        String content,
        boolean isSensitive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String itemType,
        Long itemId,
        boolean isHidden,
        String hiddenReason
) {
    public static MyReviewResponse from(Review r, String userFullName) {
        return new MyReviewResponse(r.getId(), userFullName, r.getRating(), r.getContent(), r.isSensitive(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getItemType(), r.getItemId(), r.isHidden(), r.getHiddenReason());
    }
}
