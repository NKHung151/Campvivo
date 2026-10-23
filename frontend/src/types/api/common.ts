/** Kiểu dùng chung cho mọi API — khớp docs/02-ky-thuat/api.md mục 1 và 3. */

/** VND, số nguyên. */
export type Money = number;

export type Role = "CUSTOMER" | "EMPLOYEE" | "ADMIN";
export type ItemType = "PRODUCT" | "COMBO";
export type DiscountType = "PERCENTAGE" | "FIXED_AMOUNT";
export type ArticleCategory = "BLOG" | "JOURNAL" | "GUIDE";

export interface Pagination {
  currentPage: number;
  limit: number;
  totalPages: number;
  totalItems: number;
}

/** Danh sách có phân trang: `{ success, data: T[], pagination }` sau khi bóc `success`. */
export interface Page<T> {
  data: T[];
  pagination: Pagination;
}

/** Body lỗi chuẩn của backend. */
export interface ApiErrorBody {
  success: false;
  errorCode: string;
  message: string;
  details?: Record<string, unknown>;
}

/** Tham số phân trang chung (`page` bắt đầu từ 1, `limit` mặc định 12, tối đa 100). */
export interface PageQuery {
  page?: number;
  limit?: number;
}
