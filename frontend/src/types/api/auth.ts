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
