package vn.campvivo.combo.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tạo/sửa combo (FR-15) — docs/02-ky-thuat/api.md mục 4.10. Tồn kho combo đổi bằng đóng gói/tháo (D-22).
 * Ràng buộc chéo (≥ 2 sản phẩm khác nhau, sản phẩm tồn tại, đúng 1 ảnh chính, slug trùng) kiểm tra ở AdminComboService.
 * `images`: ảnh tự tải lên (có thể rỗng) — combo còn hiển thị thêm ảnh của các sản phẩm thành phần (D-26).
 */
public record ComboRequest(
        @NotBlank(message = "Vui lòng nhập tên combo.")
        @Size(min = 2, max = 255, message = "Tên combo phải từ 2 đến 255 ký tự.")
        String name,

        /* Bỏ trống → tạo mới thì sinh từ tên; sửa thì giữ slug cũ. */
        @Size(max = 280, message = "Slug tối đa 280 ký tự.")
        @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang.")
        String slug,

        @Size(max = 50_000, message = "Mô tả quá dài.")
        String description,

        @NotNull(message = "Vui lòng chọn danh mục.")
        Long categoryId,

        @NotNull(message = "Vui lòng nhập giá combo.")
        @DecimalMin(value = "1", message = "Giá combo phải lớn hơn 0.")
        @DecimalMax(value = "999999999999", message = "Giá combo quá lớn.")
        @Digits(integer = 12, fraction = 0, message = "Giá combo phải là số nguyên (VND).")
        BigDecimal comboPrice,

        boolean isActive,

        @Size(max = 10, message = "Tối đa 10 ảnh cho một combo.")
        List<@Valid ImageRequest> images,

        @NotNull(message = "Vui lòng chọn sản phẩm trong combo.")
        @Size(min = 2, max = 20, message = "Combo cần từ 2 đến 20 sản phẩm.")
        List<@Valid ItemRequest> items
) {
    public record ImageRequest(
            @NotBlank(message = "Thiếu đường dẫn ảnh.")
            @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự.")
            @Pattern(regexp = "^https?://\\S+$", message = "Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://.")
            String url,
            boolean isPrimary,
            @Min(value = 0, message = "Thứ tự ảnh không hợp lệ.") int displayOrder
    ) {}

    public record ItemRequest(
            @NotNull(message = "Thiếu sản phẩm.") Long productId,
            @Min(value = 1, message = "Số lượng từ 1 đến 99.") @Max(value = 99, message = "Số lượng từ 1 đến 99.") int quantity
    ) {}
}
