import { apiClient } from "@/services/apiClient";
import type { ItemType, Page } from "@/types/api/common";
import type {
  CreateReviewRequest,
  MyReview,
  Review,
  ReviewItemRef,
  ReviewSummary,
  UpdateReviewRequest,
} from "@/types/api/review";

/** Đánh giá sản phẩm/combo — docs/02-ky-thuat/api.md mục 4.3 (công khai) và 4.6 (khách) — FR-18. */

const base = (itemType: ItemType, itemId: number) => `/${itemType === "PRODUCT" ? "products" : "combos"}/${itemId}/reviews`;

/** Danh sách công khai, mới nhất trước; `rating` = lọc đúng số sao. Không gửi token (đọc công khai). */
export function listReviews(
  itemType: ItemType,
  itemId: number,
  params: { rating?: number; page?: number; limit?: number },
): Promise<Page<Review>> {
  return apiClient.getPage<Review>(base(itemType, itemId), { params, skipAuth: true });
}

export function getReviewSummary(itemType: ItemType, itemId: number): Promise<ReviewSummary> {
  return apiClient.get<ReviewSummary>(`${base(itemType, itemId)}/summary`, { skipAuth: true });
}

/** Đánh giá của tôi cho mặt hàng (kể cả khi bị ẩn); chưa viết → null. */
export function getMyReview(item: ReviewItemRef): Promise<MyReview | null> {
  return apiClient.get<MyReview | null>("/reviews/mine", { params: item });
}

/** Viết được ngay lúc này không (đã nhận hàng + chưa viết). */
export async function canReview(item: ReviewItemRef): Promise<boolean> {
  return (await apiClient.get<{ canReview: boolean }>("/reviews/eligibility", { params: item })).canReview;
}

export function createReview(body: CreateReviewRequest): Promise<MyReview> {
  return apiClient.post<MyReview>("/reviews", body);
}

export function updateReview(id: number, body: UpdateReviewRequest): Promise<MyReview> {
  return apiClient.put<MyReview>(`/reviews/${id}`, body);
}

export function deleteReview(id: number): Promise<void> {
  return apiClient.delete<void>(`/reviews/${id}`);
}

/** ItemType + id → tham số `productId`/`comboId`. */
export function itemRef(itemType: ItemType, itemId: number): ReviewItemRef {
  return itemType === "PRODUCT" ? { productId: itemId } : { comboId: itemId };
}
