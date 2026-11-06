/** Kiểu module đánh giá — khớp docs/02-ky-thuat/api.md mục 4.3, 4.6, 4.10 (FR-18, D-31…D-33). */
import type { ItemType, PageQuery } from "./common";

/** Đánh giá công khai. `isSensitive` = hệ thống bắt được ngôn từ không phù hợp → làm mờ nội dung (D-33). */
export interface Review {
  id: number;
  userFullName: string;
  rating: number;
  content: string | null;
  isSensitive: boolean;
  createdAt: string;
  /** > createdAt = chủ đã sửa ("(đã chỉnh sửa)"). */
  updatedAt: string;
}

/** Đánh giá của chính khách, kể cả khi bị nhân viên ẩn (kèm lý do — D-31). */
export interface MyReview extends Review {
  itemType: ItemType;
  itemId: number;
  isHidden: boolean;
  hiddenReason: string | null;
}

export type RatingCounts = Record<1 | 2 | 3 | 4 | 5, number>;

export interface ReviewSummary {
  averageRating: number | null;
  reviewCount: number;
  ratingCounts: RatingCounts;
}

/** Đúng một trong hai id. */
export type ReviewItemRef = { productId: number; comboId?: never } | { comboId: number; productId?: never };

export type CreateReviewRequest = ReviewItemRef & { rating: number; content?: string | null };

export interface UpdateReviewRequest {
  rating: number;
  content?: string | null;
}

/* ───────────── Admin ───────────── */

export type ModerationStatus = "SAFE" | "SENSITIVE" | "CLEARED";

/** `VISIBLE` = chưa ẩn; `HIDDEN` = đã ẩn; `SENSITIVE` = chưa ẩn và đang bị làm mờ. */
export type AdminReviewStatus = "VISIBLE" | "HIDDEN" | "SENSITIVE";

export interface AdminReview {
  id: number;
  rating: number;
  content: string | null;
  user: { id: number; fullName: string; email: string } | null;
  item: { type: ItemType; id: number; name: string | null; slug: string | null };
  moderationStatus: ModerationStatus;
  flaggedTerms: string[];
  isHidden: boolean;
  hiddenReason: string | null;
  hiddenByName: string | null;
  hiddenAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminReviewQuery extends PageQuery {
  q?: string;
  itemType?: ItemType;
  rating?: number;
  status?: AdminReviewStatus;
}
