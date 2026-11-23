package vn.campvivo.order.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
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
import vn.campvivo.order.returns.dto.AdminReturnResponses;

/** Xử lý yêu cầu trả hàng — api.md mục 4.10 (quyền Staff: EMPLOYEE hoặc ADMIN, SecurityConfig `/api/admin/**`). */
@Validated
@RestController
@RequestMapping("/api/admin/returns")
public class AdminReturnController {

    private final ReturnService returnService;

    public AdminReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping
    public PageResponse<AdminReturnResponses.Summary> list(
            @RequestParam(required = false) ReturnStatus status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return returnService.list(status, page, limit);
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminReturnResponses.Detail> get(@PathVariable Long id) {
        return ApiResponse.of(returnService.detail(id));
    }

    /** `{action: APPROVE | REJECT, note?}` — từ chối bắt buộc `note`. */
    @PatchMapping("/{id}")
    public ApiResponse<AdminReturnResponses.Detail> process(@PathVariable Long id,
                                                            @Valid @RequestBody AdminReturnResponses.ProcessRequest request,
                                                            @CurrentUserId Long staffId) {
        return ApiResponse.of(returnService.process(id, request, staffId));
    }
}
