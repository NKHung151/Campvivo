package vn.campvivo.review.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import vn.campvivo.review.ModerationStatus;
import vn.campvivo.review.Review;

import java.time.OffsetDateTime;
import java.util.List;

/** Kiểu dữ liệu quản trị đánh giá — api.md mục 4.10 (`AdminReview`), D-31, D-33. */
public final class AdminReviewResponses {

    private AdminReviewResponses() {
    }

    /** `VISIBLE` = chưa ẩn; `HIDDEN` = đã ẩn; `SENSITIVE` = chưa ẩn và đang bị làm mờ. */
    public enum StatusFilter { VISIBLE, HIDDEN, SENSITIVE }

    /** `itemType`: "PRODUCT" / "COMBO" / null; page bắt đầu từ 1. */
    public record Query(String q, String itemType, Integer rating, StatusFilter status, int page, int limit) {}

    public record HideRequest(
            @NotBlank(message = "Vui lòng nhập lý do ẩn.")
            @Size(max = 500, message = "Lý do ẩn tối đa 500 ký tự.") String reason) {}

    public record UserRef(Long id, String fullName, String email) {}

    public record ItemRef(String type, Long id, String name, String slug) {}

    public record AdminReview(
            Long id,
            int rating,
            String content,
            UserRef user,
            ItemRef item,
            ModerationStatus moderationStatus,
            List<String> flaggedTerms,
            boolean isHidden,
            String hiddenReason,
            String hiddenByName,
            OffsetDateTime hiddenAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        static AdminReview from(Review r, UserRef user, ItemRef item, String hiddenByName) {
            return new AdminReview(r.getId(), r.getRating(), r.getContent(), user, item, r.getModerationStatus(),
                    r.getFlaggedTermList(), r.isHidden(), r.getHiddenReason(), hiddenByName, r.getHiddenAt(),
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }
}
