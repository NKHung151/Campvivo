package vn.campvivo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.campvivo.auth.PasswordPolicy;

/** POST /auth/register — docs/02-ky-thuat/api.md mục 4.1. Mật khẩu 8–64 ký tự, có ít nhất 1 chữ và 1 số. */
public record RegisterRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email quá dài")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.LENGTH_MESSAGE)
        @Pattern(regexp = PasswordPolicy.PATTERN, message = PasswordPolicy.PATTERN_MESSAGE)
        String password,

        @NotBlank(message = "Họ tên không được để trống")
        @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
        String fullName
) {}
