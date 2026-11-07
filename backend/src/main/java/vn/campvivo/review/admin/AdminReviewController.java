package vn.campvivo.review.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.review.admin.AdminReviewResponses.AdminReview;
import vn.campvivo.review.admin.AdminReviewResponses.HideRequest;
import vn.campvivo.review.admin.AdminReviewResponses.Query;
import vn.campvivo.review.admin.AdminReviewResponses.StatusFilter;

/**
 * Quản trị đánh giá — api.md mục 4.10 (D-31). Staff (EMPLOYEE/ADMIN) theo SecurityConfig `/api/admin/**`;
 * xoá chỉ ADMIN.
 */
@Validated
@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final AdminReviewService service;

    public AdminReviewController(AdminReviewService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AdminReview> list(
            @RequestParam(required = false) @Size(max = 100, message = "Từ khoá tối đa 100 ký tự.") String q,
            @RequestParam(required = false)
            @Pattern(regexp = "PRODUCT|COMBO", message = "Loại mặt hàng phải là PRODUCT hoặc COMBO.") String itemType,
            @RequestParam(required = false) @Min(value = 1, message = "Số sao từ 1 đến 5.")
            @Max(value = 5, message = "Số sao từ 1 đến 5.") Integer rating,
            @RequestParam(required = false) StatusFilter status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.list(new Query(q, itemType, rating, status, page, limit));
    }

    @PatchMapping("/{id}/hide")
    public ApiResponse<AdminReview> hide(@PathVariable Long id, @Valid @RequestBody HideRequest request,
                                         @CurrentUserId Long staffId) {
        return ApiResponse.of(service.hide(id, request.reason(), staffId));
    }

    @PatchMapping("/{id}/unhide")
    public ApiResponse<AdminReview> unhide(@PathVariable Long id) {
        return ApiResponse.of(service.unhide(id));
    }

    @PatchMapping("/{id}/clear-sensitive")
    public ApiResponse<AdminReview> clearSensitive(@PathVariable Long id) {
        return ApiResponse.of(service.clearSensitive(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.of(null);
    }
}
