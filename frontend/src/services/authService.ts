import { apiClient } from "@/services/apiClient";
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  ResendCodeRequest,
  VerifyEmailRequest,
} from "@/types/api/auth";
import type { UserProfile } from "@/types/api/common";

/** Gọi API xác thực (docs/02-ky-thuat/api.md mục 4.1–4.2). Việc lưu / xoá token do AuthProvider đảm nhiệm. */
export const authService = {
  // skipAuth: không gửi kèm token cũ (xem apiClient). Mọi endpoint xác thực công khai (login, register,
  // verify-email, resend-code; sau này forgot/reset-password) PHẢI dùng { skipAuth: true }.
  login: (body: LoginRequest) => apiClient.post<LoginResponse>("/auth/login", body, { skipAuth: true }),

  register: (body: RegisterRequest) => apiClient.post<RegisterResponse>("/auth/register", body, { skipAuth: true }),

  /** Đúng mã → xác thực email và đăng nhập luôn (trả token như login). */
  verifyEmail: (body: VerifyEmailRequest) =>
    apiClient.post<LoginResponse>("/auth/verify-email", body, { skipAuth: true }),

  resendCode: (body: ResendCodeRequest) => apiClient.post<null>("/auth/resend-code", body, { skipAuth: true }),

  /** Tăng token_version ở server: mọi token đã cấp (mọi thiết bị) hết hiệu lực. */
  logout: () => apiClient.post<null>("/auth/logout"),

  /** Hồ sơ người đang đăng nhập — dùng để khôi phục phiên khi tải lại trang. */
  getMe: () => apiClient.get<UserProfile>("/users/me"),
};
