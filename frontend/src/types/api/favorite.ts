/** Yêu thích (FR-20) — khớp docs/02-ky-thuat/api.md mục 4.2, 4.6 và D-47. */
import type { ArticleSummary, ComboSummary, ItemSummary } from "./catalog";

export type FavoriteType = "PRODUCT" | "COMBO" | "ARTICLE";

/** Đúng một trong ba id. */
export type FavoriteRef = { productId: number } | { comboId: number } | { articleId: number };

export type Favorite =
  | { id: number; type: "PRODUCT"; item: ItemSummary; createdAt: string }
  | { id: number; type: "COMBO"; item: ComboSummary; createdAt: string }
  | { id: number; type: "ARTICLE"; item: ArticleSummary; createdAt: string };

/** Mọi mục của khách (kể cả mặt hàng đang ẩn) — tô tim ở trang chi tiết / giỏ hàng. */
export interface FavoriteIds {
  productIds: number[];
  comboIds: number[];
  articleIds: number[];
}
