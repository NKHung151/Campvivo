package vn.campvivo.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.auth.dto.ForgotPasswordRequest;
import vn.campvivo.auth.dto.LoginRequest;
import vn.campvivo.auth.dto.LoginResponse;
import vn.campvivo.auth.dto.RegisterRequest;
import vn.campvivo.auth.dto.RegisterResponse;
import vn.campvivo.auth.dto.ResendCodeRequest;
import vn.campvivo.auth.dto.ResetPasswordRequest;
import vn.campvivo.auth.dto.VerifyEmailRequest;
import vn.campvivo.common.dto.ApiResponse;

/** /api/auth/* — docs/02-ky-thuat/api.md mục 4.1. Controller mỏng: chỉ nhận request, gọi Service. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, RegistrationService registrationService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
    }

    /** Đăng ký: tạo tài khoản CUSTOMER chưa xác thực và gửi OTP qua email (201). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(registrationService.register(request));
    }

    /** Xác thực email bằng OTP và đăng nhập luôn (trả token + hồ sơ). */
    @PostMapping("/verify-email")
    public ApiResponse<LoginResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ApiResponse.of(registrationService.verifyEmail(request));
    }

    /** Gửi lại mã; luôn 200 với email không tồn tại (chống dò email). */
    @PostMapping("/resend-code")
    public ApiResponse<Void> resendCode(@Valid @RequestBody ResendCodeRequest request) {
        registrationService.resendCode(request);
        return ApiResponse.of(null);
    }

    /** Gửi mã đặt lại mật khẩu; luôn 200 (kể cả email không tồn tại) để không lộ email nào đã đăng ký. */
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request);
        return ApiResponse.of(null);
    }

    /** Đặt lại mật khẩu bằng mã OTP; thành công thì mọi token cũ hết hiệu lực (không đăng nhập luôn). */
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ApiResponse.of(null);
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
