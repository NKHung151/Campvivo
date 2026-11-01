import type { UserProfile } from "@/types/api/common";

/** Body `POST /auth/login` — docs/02-ky-thuat/api.md mục 4.1. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** Kết quả đăng nhập: JWT + thông tin người dùng (`user.role` quyết định trang đích). */
export interface LoginResponse {
  accessToken: string;
  user: UserProfile;
}

/** Body `POST /auth/register`. */
export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

/** Kết quả đăng ký: email (đã chuẩn hoá) để chuyển sang màn nhập mã. */
export interface RegisterResponse {
  email: string;
}

/** Body `POST /auth/verify-email`. Trả `LoginResponse` (xác thực xong là đăng nhập luôn). */
export interface VerifyEmailRequest {
  email: string;
  code: string;
}

export type OtpPurpose = "VERIFY_EMAIL" | "RESET_PASSWORD";

/** Body `POST /auth/resend-code`. */
export interface ResendCodeRequest {
  email: string;
  purpose: OtpPurpose;
}
