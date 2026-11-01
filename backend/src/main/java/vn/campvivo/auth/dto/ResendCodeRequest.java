package vn.campvivo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.campvivo.user.OtpPurpose;

/** POST /auth/resend-code — `purpose`: VERIFY_EMAIL hoặc RESET_PASSWORD. Giá trị khác bị từ chối ở bước đọc JSON (400). */
public record ResendCodeRequest(
        @NotBlank(message = "Email không được để trống")
        @Size(max = 255, message = "Email quá dài")
        String email,

        @NotNull(message = "Vui lòng chọn mục đích của mã")
        OtpPurpose purpose
) {}
