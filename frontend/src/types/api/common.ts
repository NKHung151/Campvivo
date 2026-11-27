/** Kiểu dùng chung cho mọi API — khớp docs/02-ky-thuat/api.md mục 1 và 3. */

/** VND, số nguyên. */
export type Money = number;

export type Role = "CUSTOMER" | "EMPLOYEE" | "ADMIN";
export type ItemType = "PRODUCT" | "COMBO";
export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "SHIPPING"
  | "COMPLETED"
  | "CANCELLED"
  | "PAYMENT_FAILED"
  | "RETURNED";
export type PaymentMethod = "VNPAY" | "COD";
export type PaymentStatus = "PENDING" | "SUCCESS" | "FAILED";
/** D-41: duyệt là hoàn tất (cộng lại kho, đơn RETURNED). */
export type ReturnStatus = "PENDING" | "APPROVED" | "REJECTED";
export type DiscountType = "PERCENTAGE" | "FIXED_AMOUNT";
export type ArticleCategory = "BLOG" | "JOURNAL" | "GUIDE";
/** D-42 — khách: 6 loại đầu; nhân viên (EMPLOYEE, ADMIN): 6 loại sau. */
export type NotificationType =
  | "ORDER_STATUS"
  | "RETURN_STATUS"
  | "SUPPORT_MESSAGE"
  | "VOUCHER_NEW"
  | "FAVORITE_ON_SALE"
  | "FAVORITE_BACK_IN_STOCK"
  | "ORDER_NEW"
  | "ORDER_PAID"
  | "RETURN_NEW"
  | "SUPPORT_NEW"
  | "LOW_STOCK"
  | "REVIEW_FLAGGED";

/** Thông tin người dùng đăng nhập (api.md mục 3). */
export interface UserProfile {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  address: string | null;
  role: Role;
}

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

/** Body thành công chuẩn của backend: `{ success: true, data }`, kèm `pagination` với danh sách phân trang. */
export interface ApiSuccessBody<T> {
  success: true;
  data: T;
  pagination?: Pagination;
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
