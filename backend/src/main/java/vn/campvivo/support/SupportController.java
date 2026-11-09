package vn.campvivo.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;

import java.util.List;

/** Chat hỗ trợ phía khách — docs/02-ky-thuat/api.md mục 4.8. SecurityConfig chỉ cho CUSTOMER vào `/api/support/**`. */
@RestController
@RequestMapping("/api/support/messages")
@Validated
public class SupportController {

    private final SupportService service;

    public SupportController(SupportService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SupportMessageResponse>> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @Min(value = 1, message = "before phải là id tin nhắn hợp lệ.") Long before,
            @RequestParam(defaultValue = "30") @Min(value = 1, message = "limit từ 1 đến 100.")
            @Max(value = 100, message = "limit từ 1 đến 100.") int limit) {
        return ApiResponse.of(service.listMessages(userId, before, limit));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SupportMessageResponse> send(@CurrentUserId Long userId, @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.of(service.sendAsCustomer(userId, request.content()));
    }
}
