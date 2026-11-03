package vn.campvivo.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.campvivo.auth.PasswordPolicy;

/** PUT /users/me/password — mật khẩu mới theo {@link PasswordPolicy} (kể cả giới hạn 72 byte, kiểm ở service). */
public record ChangePasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        @Size(max = 128, message = "Mật khẩu hiện tại quá dài")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới không được để trống")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.LENGTH_MESSAGE)
        @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.PATTERN_MESSAGE)
        String newPassword
) {}
