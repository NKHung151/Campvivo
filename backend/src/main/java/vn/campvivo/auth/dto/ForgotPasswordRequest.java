package vn.campvivo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/forgot-password — docs/02-ky-thuat/api.md mục 4.1. */
public record ForgotPasswordRequest(
        @NotBlank(message = "Email không được để trống")
        @Size(max = 255, message = "Email quá dài")
        String email
) {}
