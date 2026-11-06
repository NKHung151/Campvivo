package vn.campvivo.review.dto;

import vn.campvivo.review.Review;

import java.time.OffsetDateTime;

/** `Review` (api.md mục 4.3). `isSensitive` → giao diện làm mờ nội dung (D-33). */
public record ReviewResponse(
        Long id,
        String userFullName,
        int rating,
        String content,
        boolean isSensitive,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ReviewResponse from(Review r, String userFullName) {
        return new ReviewResponse(r.getId(), userFullName, r.getRating(), r.getContent(), r.isSensitive(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
