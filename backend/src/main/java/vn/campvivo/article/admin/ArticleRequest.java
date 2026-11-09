package vn.campvivo.article.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.campvivo.article.ArticleCategory;

/**
 * Tạo/sửa bài viết (FR-21b) — api.md mục 4.10. Nháp được lưu khi còn thiếu tóm tắt, ảnh đại diện, nội dung;
 * xuất bản (`isPublished = true`) thì bắt buộc đủ cả ba — kiểm tra ở AdminArticleService (400 fieldErrors).
 */
public record ArticleRequest(
        @NotBlank(message = "Vui lòng nhập tiêu đề.")
        @Size(min = 5, max = 255, message = "Tiêu đề phải từ 5 đến 255 ký tự.")
        String title,

        /* Bỏ trống → tạo mới thì sinh từ tiêu đề; sửa thì giữ slug cũ. */
        @Size(max = 280, message = "Slug tối đa 280 ký tự.")
        @Pattern(regexp = "^$|^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang.")
        String slug,

        @Size(max = 1000, message = "Tóm tắt tối đa 1000 ký tự.")
        String summary,

        @Size(max = 200_000, message = "Nội dung quá dài.")
        String content,

        @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự.")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://.")
        String thumbnailUrl,

        @NotNull(message = "Vui lòng chọn loại bài viết.")
        ArticleCategory category,

        boolean isPublished
) {}
