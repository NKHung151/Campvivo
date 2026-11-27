package vn.campvivo.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.notification.dto.NotificationListResponse;
import vn.campvivo.notification.dto.ReadResponses;

/**
 * Thông báo của chính người đang đăng nhập — docs/02-ky-thuat/api.md mục 4.7 (FR-22). Mọi role (CUSTOMER, EMPLOYEE, ADMIN);
 * SecurityConfig chỉ yêu cầu đã đăng nhập. Người nhận luôn lấy từ token, không nhận từ request.
 */
@RestController
@RequestMapping("/api/notifications")
@Validated
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationListResponse list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.list(userId, isRead, resolved, page, limit);
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<ReadResponses.Read> markRead(@CurrentUserId Long userId, @PathVariable Long id) {
        return ApiResponse.of(service.markRead(userId, id));
    }

    @PatchMapping("/read-all")
    public ApiResponse<ReadResponses.ReadAll> markAllRead(@CurrentUserId Long userId) {
        return ApiResponse.of(service.markAllRead(userId));
    }
}
