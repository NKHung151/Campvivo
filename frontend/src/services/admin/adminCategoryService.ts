import { apiClient } from "@/services/apiClient";
import type { AdminCategoryNode, CategoryRequest } from "@/types/api/catalog";

/** API quản lý danh mục — docs/02-ky-thuat/api.md mục 4.10–4.11. */

export function getAdminCategoryTree(): Promise<AdminCategoryNode[]> {
  return apiClient.get<AdminCategoryNode[]>("/admin/categories");
}

export function createCategory(body: CategoryRequest): Promise<AdminCategoryNode> {
  return apiClient.post<AdminCategoryNode>("/admin/categories", body);
}

export function updateCategory(id: number, body: CategoryRequest): Promise<AdminCategoryNode> {
  return apiClient.put<AdminCategoryNode>(`/admin/categories/${id}`, body);
}

/** Chặn nếu còn danh mục con, sản phẩm hoặc combo (CATEGORY_NOT_EMPTY). */
export async function deleteCategory(id: number): Promise<void> {
  await apiClient.delete<null>(`/admin/categories/${id}`);
}
