import { apiClient } from "@/services/apiClient";
import type { Page } from "@/types/api/common";
import type { AdminArticleDetail, AdminArticleQuery, AdminArticleSummary, ArticleRequest } from "@/types/api/article";

/** API quản lý bài viết — docs/02-ky-thuat/api.md mục 4.10 (FR-21b). Chỉ ADMIN. */

export function listArticles(query: AdminArticleQuery): Promise<Page<AdminArticleSummary>> {
  return apiClient.getPage<AdminArticleSummary>("/admin/articles", { params: query });
}

export function getArticle(id: number): Promise<AdminArticleDetail> {
  return apiClient.get<AdminArticleDetail>(`/admin/articles/${id}`);
}

export function createArticle(body: ArticleRequest): Promise<AdminArticleDetail> {
  return apiClient.post<AdminArticleDetail>("/admin/articles", body);
}

export function updateArticle(id: number, body: ArticleRequest): Promise<AdminArticleDetail> {
  return apiClient.put<AdminArticleDetail>(`/admin/articles/${id}`, body);
}

/** Đăng / gỡ nhanh từ danh sách. Đăng mà bài thiếu tóm tắt, ảnh hoặc nội dung → 400 `fieldErrors`. */
export function changeArticleStatus(id: number, isPublished: boolean): Promise<AdminArticleSummary> {
  return apiClient.patch<AdminArticleSummary>(`/admin/articles/${id}/status`, { isPublished });
}

/** Xoá cứng. */
export function deleteArticle(id: number): Promise<void> {
  return apiClient.delete<void>(`/admin/articles/${id}`);
}
