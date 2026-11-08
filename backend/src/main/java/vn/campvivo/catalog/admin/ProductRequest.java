package vn.campvivo.catalog.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body POST/PUT /api/admin/products — docs/02-ky-thuat/api.md mục 4.11 (`ProductRequest`).
 * Ràng buộc chéo (đúng 1 ảnh chính, slug trùng, danh mục tồn tại) kiểm tra ở AdminProductService.
 * Không có tồn kho: nhập kho ở trang Tồn kho (FR-08).
 * Biến thể (D-37): tối đa 2 tuỳ chọn; server tự sinh mọi tổ hợp giá trị, `variants` chỉ gửi SKU / bật-tắt theo tổ hợp.
 */
public record ProductRequest(
        @NotBlank(message = "Vui lòng nhập tên sản phẩm.")
        @Size(min = 2, max = 255, message = "Tên sản phẩm phải từ 2 đến 255 ký tự.")
        String name,

        /* Bỏ trống → tạo mới thì sinh từ tên; sửa thì giữ slug cũ. */
        @Size(max = 280, message = "Slug tối đa 280 ký tự.")
        @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang.")
        String slug,

        @Size(max = 500, message = "Mô tả ngắn tối đa 500 ký tự.")
        String shortDescription,

        @Size(max = 50_000, message = "Mô tả quá dài.")
        String description,

        @Size(max = 100, message = "Thương hiệu tối đa 100 ký tự.")
        String brand,

        @NotNull(message = "Vui lòng nhập giá bán.")
        @Positive(message = "Giá bán phải lớn hơn 0.")
        @Digits(integer = 15, fraction = 0, message = "Giá bán phải là số nguyên VND.")
        BigDecimal price,

        @NotNull(message = "Vui lòng chọn danh mục.")
        Long categoryId,

        @NotNull(message = "Vui lòng chọn trạng thái kinh doanh.")
        Boolean isActive,

        @Size(max = 30, message = "Tối đa 30 dòng thông số.")
        List<@Valid @NotNull SpecRequest> specs,

        @NotNull(message = "Danh sách ảnh không được thiếu.")
        @Size(max = 10, message = "Tối đa 10 ảnh cho một sản phẩm.")
        List<@Valid @NotNull ImageRequest> images,

        /* null/rỗng = sản phẩm không có tuỳ chọn (1 biến thể mặc định). */
        @Size(max = 2, message = "Tối đa 2 tuỳ chọn (vd. Màu sắc, Size).")
        List<@Valid @NotNull OptionRequest> options,

        @Size(max = 200, message = "Quá nhiều biến thể.")
        List<@Valid @NotNull VariantRequest> variants
) {

    /** `id` có = tuỳ chọn đã có (đổi tên vẫn giữ biến thể); null = tuỳ chọn mới. */
    public record OptionRequest(
            Long id,

            @NotBlank(message = "Tên tuỳ chọn không được để trống.")
            @Size(max = 50, message = "Tên tuỳ chọn tối đa 50 ký tự.")
            String name,

            @NotNull(message = "Tuỳ chọn cần ít nhất 1 giá trị.")
            @Size(min = 1, max = 30, message = "Mỗi tuỳ chọn có từ 1 đến 30 giá trị.")
            List<@Valid @NotNull OptionValueRequest> values
    ) {}

    /** `id` có = giá trị đã có (đổi tên vẫn giữ tồn kho của biến thể); null = giá trị mới. */
    public record OptionValueRequest(
            Long id,

            @NotBlank(message = "Giá trị tuỳ chọn không được để trống.")
            @Size(max = 100, message = "Giá trị tuỳ chọn tối đa 100 ký tự.")
            String value
    ) {}

    /** Thiết lập của một tổ hợp — `values` theo thứ tự tuỳ chọn (rỗng với biến thể mặc định). */
    public record VariantRequest(
            @NotNull List<@NotNull String> values,

            @Size(max = 64, message = "SKU tối đa 64 ký tự.")
            @Pattern(regexp = "^\\s*([A-Za-z0-9._-]+\\s*)?$", message = "SKU chỉ gồm chữ, số, dấu chấm, gạch ngang, gạch dưới.")
            String sku,

            Boolean isActive
    ) {}

    public record SpecRequest(
            @NotBlank(message = "Tên thông số không được để trống.")
            @Size(max = 100, message = "Tên thông số tối đa 100 ký tự.")
            String name,

            @NotBlank(message = "Giá trị thông số không được để trống.")
            @Size(max = 500, message = "Giá trị thông số tối đa 500 ký tự.")
            String value
    ) {}

    public record ImageRequest(
            @NotBlank(message = "Đường dẫn ảnh không được để trống.")
            @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự.")
            @Pattern(regexp = "^https?://\\S+$", message = "Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://.")
            String url,

            @NotNull Boolean isPrimary,

            @NotNull @Min(value = 0, message = "Thứ tự ảnh không hợp lệ.") Integer displayOrder
    ) {}
}
