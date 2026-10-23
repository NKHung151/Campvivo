import { apiClient } from "@/services/apiClient";
import type { LoginRequest, LoginResponse } from "@/types/api/auth";
import type { UserProfile } from "@/types/api/common";

/** Gọi API xác thực (docs/02-ky-thuat/api.md mục 4.1–4.2). Việc lưu / xoá token do AuthProvider đảm nhiệm. */
export const authService = {
  // skipAuth: không gửi kèm token cũ (xem apiClient) — endpoint công khai khác (register, verify-email,
  // resend-code, forgot/reset-password) khi thêm cũng phải dùng { skipAuth: true }.
  login: (body: LoginRequest) => apiClient.post<LoginResponse>("/auth/login", body, { skipAuth: true }),

  /** Tăng token_version ở server: mọi token đã cấp (mọi thiết bị) hết hiệu lực. */
  logout: () => apiClient.post<null>("/auth/logout"),

  /** Hồ sơ người đang đăng nhập — dùng để khôi phục phiên khi tải lại trang. */
  getMe: () => apiClient.get<UserProfile>("/users/me"),
};
