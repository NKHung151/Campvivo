package vn.campvivo.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;

import java.util.List;

/**
 * Chat hỗ trợ phía nhân viên — docs/02-ky-thuat/api.md mục 4.8. EMPLOYEE và ADMIN đều dùng được; @PreAuthorize nêu rõ vai trò
 * thay vì chỉ trông vào đường dẫn trong SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/support/conversations")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'ADMIN')")
@Validated
public class AdminSupportController {

    private final SupportService service;

    public AdminSupportController(SupportService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ConversationResponse> conversations(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Số dòng mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số dòng mỗi trang từ 1 đến 100.") int limit) {
        return service.listConversations(page, limit);
    }

    @GetMapping("/{customerId}/messages")
    public ApiResponse<List<SupportMessageResponse>> messages(
            @PathVariable Long customerId,
            @RequestParam(required = false) @Min(value = 1, message = "before phải là id tin nhắn hợp lệ.") Long before,
            @RequestParam(defaultValue = "30") @Min(value = 1, message = "limit từ 1 đến 100.")
            @Max(value = 100, message = "limit từ 1 đến 100.") int limit) {
        return ApiResponse.of(service.listMessagesForStaff(customerId, before, limit));
    }

    @PostMapping("/{customerId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SupportMessageResponse> reply(@PathVariable Long customerId, @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.of(service.sendAsStaff(customerId, request.content()));
    }
}
