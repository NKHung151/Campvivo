package vn.campvivo.promotion.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.promotion.VoucherStatus;

/** API quản lý voucher (FR-16) — docs/02-ky-thuat/api.md mục 4.10. Chỉ ADMIN. */
@RestController
@RequestMapping("/api/admin/vouchers")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminVoucherController {

    private final AdminVoucherService service;

    public AdminVoucherController(AdminVoucherService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminVoucherResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) VoucherStatus status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.list(q, status, page, limit);
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminVoucherResponse> get(@PathVariable Long id) {
        return ApiResponse.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminVoucherResponse> create(@Valid @RequestBody VoucherRequest request) {
        return ApiResponse.of(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminVoucherResponse> update(@PathVariable Long id, @Valid @RequestBody VoucherRequest request) {
        return ApiResponse.of(service.update(id, request));
    }

    /** Đã có đơn dùng → 409 VOUCHER_IN_USE (chỉ được tắt). */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.of(null);
    }
}
