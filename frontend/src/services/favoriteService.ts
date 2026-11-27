import { apiClient } from "@/services/apiClient";
import type { Page, PageQuery } from "@/types/api/common";
import type { Favorite, FavoriteIds, FavoriteRef, FavoriteType } from "@/types/api/favorite";

/** Yêu thích của khách — docs/02-ky-thuat/api.md mục 4.2, 4.6 (FR-20, D-47). Chỉ role CUSTOMER. */

/** Thêm; đã thích thì trả id cũ (idempotent). */
export async function addFavorite(ref: FavoriteRef): Promise<number> {
  return (await apiClient.post<{ id: number }>("/favorites", ref)).id;
}

/** Bỏ thích theo mặt hàng; chưa thích vẫn thành công. */
export function removeFavorite(ref: FavoriteRef): Promise<void> {
  return apiClient.delete<void>("/favorites", { params: ref });
}

export function removeFavoriteById(id: number): Promise<void> {
  return apiClient.delete<void>(`/favorites/${id}`);
}

/** Mới thích trước; chỉ mặt hàng đang hiển thị. */
export function listFavorites(query: PageQuery & { type?: FavoriteType }): Promise<Page<Favorite>> {
  return apiClient.getPage<Favorite>("/users/me/favorites", { params: query });
}

export function getFavoriteIds(): Promise<FavoriteIds> {
  return apiClient.get<FavoriteIds>("/users/me/favorites/ids");
}
