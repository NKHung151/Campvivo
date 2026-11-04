import { apiClient } from "@/services/apiClient";
import type { Page } from "@/types/api/common";
import type { AdminUserDetail, AdminUserQuery, AdminUserSummary, UpdateAdminUserRequest } from "@/types/api/adminUser";

/** API quản lý người dùng cho Admin — docs/02-ky-thuat/api.md mục 4.10 (FR-14). Chỉ ADMIN. */
export function listUsers(query: AdminUserQuery): Promise<Page<AdminUserSummary>> {
  return apiClient.getPage<AdminUserSummary>("/admin/users", { params: query });
}

export function getUser(id: number): Promise<AdminUserDetail> {
  return apiClient.get<AdminUserDetail>(`/admin/users/${id}`);
}

/** Sửa từng phần. Đổi role / khoá thì tăng token_version; tự khoá hoặc tự đổi role của mình → CANNOT_MODIFY_SELF. */
export function updateUser(id: number, body: UpdateAdminUserRequest): Promise<AdminUserDetail> {
  return apiClient.patch<AdminUserDetail>(`/admin/users/${id}`, body);
}
