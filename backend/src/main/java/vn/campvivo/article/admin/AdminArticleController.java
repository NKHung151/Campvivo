package vn.campvivo.article.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.article.ArticleCategory;
import vn.campvivo.article.admin.AdminArticleResponses.Detail;
import vn.campvivo.article.admin.AdminArticleResponses.Summary;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;

/** API quản lý bài viết (FR-21b) — docs/02-ky-thuat/api.md mục 4.10. Chỉ ADMIN. */
@RestController
@RequestMapping("/api/admin/articles")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminArticleController {

    private final AdminArticleService service;

    public AdminArticleController(AdminArticleService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Summary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ArticleCategory category,
            @RequestParam(required = false) Boolean isPublished,
            @RequestParam(defaultValue = "UPDATED_DESC") AdminArticleQuery.SortOption sort,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.list(new AdminArticleQuery(q, category, isPublished, sort, page, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<Detail> get(@PathVariable Long id) {
        return ApiResponse.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Detail> create(@Valid @RequestBody ArticleRequest request, @CurrentUserId Long userId) {
        return ApiResponse.of(service.create(request, userId));
    }

    @PutMapping("/{id}")
    public ApiResponse<Detail> update(@PathVariable Long id, @Valid @RequestBody ArticleRequest request) {
        return ApiResponse.of(service.update(id, request));
    }

    /** Xuất bản / gỡ nhanh từ danh sách. */
    @PatchMapping("/{id}/status")
    public ApiResponse<Summary> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.of(service.changeStatus(id, request.isPublished()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.of(null);
    }

    public record StatusRequest(@NotNull(message = "Thiếu trạng thái.") Boolean isPublished) {}
}
