package vn.campvivo.catalog.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.catalog.admin.AdminProductResponses.Detail;
import vn.campvivo.catalog.admin.AdminProductResponses.Summary;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;

/** API quản lý sản phẩm — docs/02-ky-thuat/api.md mục 4.10. Chỉ ADMIN. */
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminProductController {

    private final AdminProductService service;

    public AdminProductController(AdminProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Summary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) AdminProductQuery.StockFilter stock,
            @RequestParam(defaultValue = "UPDATED_DESC") AdminProductQuery.SortOption sort,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.list(new AdminProductQuery(q, categoryId, brand, isActive, stock, sort, page, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<Detail> get(@PathVariable Long id) {
        return ApiResponse.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Detail> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.of(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Detail> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ApiResponse.of(service.update(id, request));
    }

    /** Ẩn/hiện (BR-14 — không xoá cứng). */
    @PatchMapping("/{id}/status")
    public ApiResponse<Summary> changeStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusRequest request) {
        return ApiResponse.of(service.changeStatus(id, request.isActive()));
    }
}
