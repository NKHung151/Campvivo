import type { ApiSuccessBody } from "./common";
import type { ArticleSummary, ComboSummary, ItemSummary } from "./catalog";

/** Tìm kiếm gộp `GET /search` — docs/02-ky-thuat/api.md mục 4.3 (FR-07). */

export type SearchType = "PRODUCT" | "COMBO" | "ARTICLE";

/** Từ khoá ngắn hơn số này (sau khi bỏ khoảng trắng hai đầu) bị server từ chối bằng 400. */
export const MIN_SEARCH_LENGTH = 2;

export interface SearchResults {
  products: ItemSummary[];
  combos: ComboSummary[];
  articles: ArticleSummary[];
}

/** Tổng số kết quả của từng loại (luôn đủ ba số), để tab hiện "(n)". */
export interface SearchTotals {
  products: number;
  combos: number;
  articles: number;
}

/** "Có phải bạn muốn tìm…?": từ khoá đã sửa lỗi gõ (có ít nhất một kết quả), chỉ có khi từ khoá gốc không ra kết quả nào. */
export interface SearchSuggestion {
  q: string;
  totals: SearchTotals;
}

/** Cả body: `data`, `totals`, `pagination` (chỉ có khi hỏi một `type`) và `suggestion` (xem {@link SearchSuggestion}). */
export type SearchBody = ApiSuccessBody<SearchResults> & { totals: SearchTotals; suggestion?: SearchSuggestion };

/** Một dòng gợi ý khi gõ (`GET /search/suggestions`): chỉ sản phẩm và combo đang bán. */
export interface SearchSuggestionItem {
  type: "PRODUCT" | "COMBO";
  name: string;
  slug: string;
  imageUrl: string | null;
}

export interface SearchQuery {
  q: string;
  type?: SearchType;
  page?: number;
  limit?: number;
}
