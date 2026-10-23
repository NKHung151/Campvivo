package vn.campvivo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/login — docs/02-ky-thuat/api.md mục 4.1. */
public record LoginRequest(
        @NotBlank(message = "Email không được để trống")
        @Size(max = 255, message = "Email quá dài")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(max = 128, message = "Mật khẩu quá dài")
        String password
) {}
