package vn.campvivo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /auth/verify-email — mã OTP gồm đúng 6 chữ số. */
public record VerifyEmailRequest(
        @NotBlank(message = "Email không được để trống")
        @Size(max = 255, message = "Email quá dài")
        String email,

        @NotBlank(message = "Vui lòng nhập mã xác thực")
        @Pattern(regexp = "^\\d{6}$", message = "Mã xác thực gồm 6 chữ số")
        String code
) {}
