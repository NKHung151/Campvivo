package vn.campvivo.review.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body của API đánh giá — docs/02-ky-thuat/api.md mục 4.6. Chỉ bắt buộc `rating`; `content` tuỳ chọn. */
public final class ReviewRequests {

    private ReviewRequests() {
    }

    /** POST /reviews — đúng một trong productId, comboId. */
    public record Create(
            Long productId,
            Long comboId,
            @NotNull(message = "Vui lòng chọn số sao")
            @Min(value = 1, message = "Số sao từ 1 đến 5") @Max(value = 5, message = "Số sao từ 1 đến 5")
            Integer rating,
            @Size(max = 2000, message = "Nhận xét tối đa 2000 ký tự")
            String content
    ) {
        @AssertTrue(message = "Chọn đúng một sản phẩm hoặc một combo")
        public boolean isExactlyOneItem() {
            return (productId == null) != (comboId == null);
        }
    }

    /** PUT /reviews/{id}. */
    public record Update(
            @NotNull(message = "Vui lòng chọn số sao")
            @Min(value = 1, message = "Số sao từ 1 đến 5") @Max(value = 5, message = "Số sao từ 1 đến 5")
            Integer rating,
            @Size(max = 2000, message = "Nhận xét tối đa 2000 ký tự")
            String content
    ) {}
}
