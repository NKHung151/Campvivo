package vn.campvivo.catalog.admin;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.catalog.admin.AdminCategoryResponses.Node;
import vn.campvivo.common.dto.ApiResponse;

import java.util.List;

/** API quản lý danh mục (FR-05) — docs/02-ky-thuat/api.md mục 4.10. Chỉ ADMIN. */
@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminCategoryController {

    private final AdminCategoryService categoryService;

    public AdminCategoryController(AdminCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<Node>> tree() {
        return ApiResponse.of(categoryService.tree());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Node> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.of(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Node> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.of(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ApiResponse.of(null);
    }
}
