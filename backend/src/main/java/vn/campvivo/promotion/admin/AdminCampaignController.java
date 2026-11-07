package vn.campvivo.promotion.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.promotion.CampaignStatus;
import vn.campvivo.promotion.admin.AdminCampaignResponses.Detail;
import vn.campvivo.promotion.admin.AdminCampaignResponses.Overlap;
import vn.campvivo.promotion.admin.AdminCampaignResponses.Summary;

import java.time.OffsetDateTime;
import java.util.List;

/** API quản lý chiến dịch giảm giá (FR-17) — docs/02-ky-thuat/api.md mục 4.10. Chỉ ADMIN. */
@RestController
@RequestMapping("/api/admin/campaigns")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminCampaignController {

    private final AdminCampaignService service;

    public AdminCampaignController(AdminCampaignService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Summary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CampaignStatus status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.list(q, status, page, limit);
    }

    /** Cảnh báo sản phẩm đã thuộc chiến dịch khác trùng thời gian (không chặn lưu — BR-6). */
    @GetMapping("/overlaps")
    public ApiResponse<List<Overlap>> overlaps(
            @RequestParam @Size(max = 500, message = "Tối đa 500 sản phẩm.") List<Long> productIds,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startDate,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endDate,
            @RequestParam(required = false) Long excludeId) {
        return ApiResponse.of(service.overlaps(productIds, startDate, endDate, excludeId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Detail> get(@PathVariable Long id) {
        return ApiResponse.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Detail> create(@Valid @RequestBody CampaignRequest request) {
        return ApiResponse.of(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Detail> update(@PathVariable Long id, @Valid @RequestBody CampaignRequest request) {
        return ApiResponse.of(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.of(null);
    }
}
