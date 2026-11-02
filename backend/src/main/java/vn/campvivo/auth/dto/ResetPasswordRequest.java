package vn.campvivo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.campvivo.auth.PasswordPolicy;

/** POST /auth/reset-password — mã OTP gồm đúng 6 chữ số; mật khẩu mới theo {@link PasswordPolicy}. */
public record ResetPasswordRequest(
        @NotBlank(message = "Email không được để trống")
        @Size(max = 255, message = "Email quá dài")
        String email,

        @NotBlank(message = "Vui lòng nhập mã xác thực")
        @Pattern(regexp = "^\\d{6}$", message = "Mã xác thực gồm 6 chữ số")
        String code,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.LENGTH_MESSAGE)
        @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.PATTERN_MESSAGE)
        String newPassword
) {}
