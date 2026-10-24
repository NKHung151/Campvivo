package vn.campvivo.combo.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.catalog.admin.ProductStatusRequest;
import vn.campvivo.combo.admin.AdminComboResponses.Detail;
import vn.campvivo.combo.admin.AdminComboResponses.Summary;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;

/** API quản lý combo (FR-15) — docs/02-ky-thuat/api.md mục 4.10. Chỉ ADMIN. */
@RestController
@RequestMapping("/api/admin/combos")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminComboController {

    private final AdminComboService service;

    public AdminComboController(AdminComboService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Summary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "UPDATED_DESC") AdminComboQuery.SortOption sort,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.list(new AdminComboQuery(q, categoryId, isActive, sort, page, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<Detail> get(@PathVariable Long id) {
        return ApiResponse.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Detail> create(@Valid @RequestBody ComboRequest request) {
        return ApiResponse.of(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Detail> update(@PathVariable Long id, @Valid @RequestBody ComboRequest request) {
        return ApiResponse.of(service.update(id, request));
    }

    /** Đóng gói thêm bộ combo: trừ kho từng sản phẩm thành phần, cộng vào kho combo (D-22). */
    @PostMapping("/{id}/pack")
    public ApiResponse<Detail> pack(@PathVariable Long id, @Valid @RequestBody PackRequest request) {
        return ApiResponse.of(service.pack(id, request.sets()));
    }

    /** Tháo bộ combo (chưa bị giữ chỗ): trả hàng về kho từng sản phẩm (D-22). */
    @PostMapping("/{id}/unpack")
    public ApiResponse<Detail> unpack(@PathVariable Long id, @Valid @RequestBody PackRequest request) {
        return ApiResponse.of(service.unpack(id, request.sets()));
    }

    /** Ẩn/hiện (BR-14 — không xoá cứng). */
    @PatchMapping("/{id}/status")
    public ApiResponse<Summary> changeStatus(@PathVariable Long id, @Valid @RequestBody ProductStatusRequest request) {
        return ApiResponse.of(service.changeStatus(id, request.isActive()));
    }
}
