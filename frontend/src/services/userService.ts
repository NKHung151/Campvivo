import { apiClient } from "@/services/apiClient";
import type { ChangePasswordRequest, UpdateProfileRequest } from "@/types/api/user";
import type { UserProfile } from "@/types/api/common";

/** Hồ sơ người dùng đang đăng nhập (docs/02-ky-thuat/api.md mục 4.2). Cần token nên KHÔNG dùng skipAuth. */
export const userService = {
  /** Thay thế fullName, phone, address; trả hồ sơ mới. */
  updateProfile: (body: UpdateProfileRequest) => apiClient.put<UserProfile>("/users/me", body),

  /** Đúng mật khẩu hiện tại → đổi và tăng token_version: token hiện có ngừng dùng được, phải đăng nhập lại. */
  changePassword: (body: ChangePasswordRequest) => apiClient.put<null>("/users/me/password", body),
};
