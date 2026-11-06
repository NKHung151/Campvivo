import { apiClient } from "@/services/apiClient";
import type { Page } from "@/types/api/common";
import type { AdminReview, AdminReviewQuery } from "@/types/api/review";

/** Quản trị đánh giá — docs/02-ky-thuat/api.md mục 4.10 (FR-18, D-31). Employee + Admin; xoá chỉ Admin. */
export function listReviews(query: AdminReviewQuery): Promise<Page<AdminReview>> {
  return apiClient.getPage<AdminReview>("/admin/reviews", { params: query });
}

/** Ẩn (đang ẩn → ghi đè lý do). `reason` bắt buộc, tối đa 500 ký tự. */
export function hideReview(id: number, reason: string): Promise<AdminReview> {
  return apiClient.patch<AdminReview>(`/admin/reviews/${id}/hide`, { reason });
}

export function unhideReview(id: number): Promise<AdminReview> {
  return apiClient.patch<AdminReview>(`/admin/reviews/${id}/unhide`);
}

/** SENSITIVE → CLEARED (bỏ làm mờ). */
export function clearSensitive(id: number): Promise<AdminReview> {
  return apiClient.patch<AdminReview>(`/admin/reviews/${id}/clear-sensitive`);
}

/** Xoá cứng — chỉ Admin; khách viết lại được. */
export function deleteReview(id: number): Promise<void> {
  return apiClient.delete<void>(`/admin/reviews/${id}`);
}
