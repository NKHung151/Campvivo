package vn.campvivo.auth;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.auth.dto.LoginRequest;
import vn.campvivo.auth.dto.LoginResponse;
import vn.campvivo.common.dto.ApiResponse;

/** /api/auth/* — docs/02-ky-thuat/api.md mục 4.1. Controller mỏng: chỉ nhận request, gọi Service. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.of(authService.login(request));
    }

    /** Quyền: mọi role đã đăng nhập (SecurityConfig: /api/auth/logout cần authenticated). */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@CurrentUserId Long userId) {
        authService.logout(userId);
        return ApiResponse.of(null);
    }
}
