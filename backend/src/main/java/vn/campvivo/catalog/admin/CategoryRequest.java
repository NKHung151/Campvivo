package vn.campvivo.catalog.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Tạo/sửa danh mục (FR-05) — docs/02-ky-thuat/api.md mục 4.10. `parentId` null = danh mục gốc.
 */
public record CategoryRequest(
        @NotBlank(message = "Vui lòng nhập tên danh mục.")
        @Size(max = 150, message = "Tên danh mục tối đa 150 ký tự.")
        String name,

        /* Bỏ trống → tạo mới thì sinh từ tên; sửa thì giữ slug cũ. */
        @Size(max = 180, message = "Slug tối đa 180 ký tự.")
        @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang.")
        String slug,

        Long parentId
) {
}
