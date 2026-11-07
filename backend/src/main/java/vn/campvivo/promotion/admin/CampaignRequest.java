package vn.campvivo.promotion.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.campvivo.promotion.DiscountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tạo/sửa chiến dịch (FR-17) — `CampaignRequest` ở api.md mục 4.10. Ràng buộc chéo (miền giá trị theo loại, kết thúc sau
 * bắt đầu, sản phẩm tồn tại, slug trùng) kiểm ở AdminCampaignService.
 */
public record CampaignRequest(
        @NotBlank(message = "Vui lòng nhập tên chiến dịch.")
        @Size(min = 2, max = 255, message = "Tên chiến dịch phải từ 2 đến 255 ký tự.")
        String name,

        /* Bỏ trống → tạo mới thì sinh từ tên; sửa thì giữ slug cũ. */
        @Size(max = 280, message = "Slug tối đa 280 ký tự.")
        @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang.")
        String slug,

        @Size(max = 50_000, message = "Mô tả quá dài.")
        String description,

        @Size(max = 500, message = "Đường dẫn banner tối đa 500 ký tự.")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "Đường dẫn banner phải bắt đầu bằng http:// hoặc https://.")
        String bannerUrl,

        @NotNull(message = "Vui lòng chọn loại giảm giá.")
        DiscountType discountType,

        @NotNull(message = "Vui lòng nhập giá trị giảm.")
        @DecimalMin(value = "1", message = "Giá trị giảm phải lớn hơn 0.")
        @DecimalMax(value = "999999999999", message = "Giá trị giảm quá lớn.")
        @Digits(integer = 12, fraction = 0, message = "Giá trị giảm phải là số nguyên.")
        BigDecimal discountValue,

        @NotNull(message = "Vui lòng chọn thời gian bắt đầu.")
        OffsetDateTime startDate,

        @NotNull(message = "Vui lòng chọn thời gian kết thúc.")
        OffsetDateTime endDate,

        boolean isActive,

        @NotNull(message = "Vui lòng chọn sản phẩm áp dụng.")
        @Size(max = 500, message = "Tối đa 500 sản phẩm cho một chiến dịch.")
        List<@NotNull(message = "Thiếu sản phẩm.") Long> productIds
) {}
