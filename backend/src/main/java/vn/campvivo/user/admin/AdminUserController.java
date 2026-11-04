package vn.campvivo.user.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.user.Role;
import vn.campvivo.user.admin.AdminUserResponses.Summary;

/**
 * Quản lý người dùng — docs/02-ky-thuat/api.md mục 4.10. Chỉ ADMIN: SecurityConfig cho `/api/admin/**` qua cả EMPLOYEE nên
 * phải chặn ở đây bằng @PreAuthorize.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<Summary> list(
            @RequestParam(required = false) @Size(max = 100, message = "Từ khoá tối đa 100 ký tự.") String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.list(new AdminUserQuery(q, role, isActive, page, limit));
    }
}
