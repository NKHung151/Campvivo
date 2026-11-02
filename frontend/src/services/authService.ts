import { apiClient } from "@/services/apiClient";
import type {
  ForgotPasswordRequest,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  ResendCodeRequest,
  ResetPasswordRequest,
  VerifyEmailRequest,
} from "@/types/api/auth";
import type { UserProfile } from "@/types/api/common";

/** Gọi API xác thực (docs/02-ky-thuat/api.md mục 4.1–4.2). Việc lưu / xoá token do AuthProvider đảm nhiệm. */
export const authService = {
  // skipAuth: không gửi kèm token cũ (xem apiClient). Mọi endpoint xác thực công khai (login, register,
  // verify-email, resend-code, forgot-password, reset-password) PHẢI dùng { skipAuth: true }.
  login: (body: LoginRequest) => apiClient.post<LoginResponse>("/auth/login", body, { skipAuth: true }),

  register: (body: RegisterRequest) => apiClient.post<RegisterResponse>("/auth/register", body, { skipAuth: true }),

  /** Đúng mã → xác thực email và đăng nhập luôn (trả token như login). */
  verifyEmail: (body: VerifyEmailRequest) =>
    apiClient.post<LoginResponse>("/auth/verify-email", body, { skipAuth: true }),

  resendCode: (body: ResendCodeRequest) => apiClient.post<null>("/auth/resend-code", body, { skipAuth: true }),

  /** Gửi mã đặt lại mật khẩu; luôn 200 kể cả email không tồn tại (chỉ lỗi gửi mail mới báo). */
  forgotPassword: (body: ForgotPasswordRequest) =>
    apiClient.post<null>("/auth/forgot-password", body, { skipAuth: true }),

  /** Đúng mã → đổi mật khẩu, mọi thiết bị bị đăng xuất; KHÔNG trả token. */
  resetPassword: (body: ResetPasswordRequest) =>
    apiClient.post<null>("/auth/reset-password", body, { skipAuth: true }),

  /** Tăng token_version ở server: mọi token đã cấp (mọi thiết bị) hết hiệu lực. */
  logout: () => apiClient.post<null>("/auth/logout"),

  /** Hồ sơ người đang đăng nhập — dùng để khôi phục phiên khi tải lại trang. */
  getMe: () => apiClient.get<UserProfile>("/users/me"),
};
