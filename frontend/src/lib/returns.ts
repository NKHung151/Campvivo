import type { ReturnStatus } from "@/types/api/common";
import type { ReturnReason } from "@/types/api/order";

/** Trả hàng (D-41) — dùng chung storefront và admin (không phụ thuộc antd). */

/** Lý do theo thứ tự hiển thị — cùng danh sách với enum `ReturnReason` ở backend. */
export const RETURN_REASONS: { value: ReturnReason; label: string }[] = [
  { value: "MISSING_ITEMS", label: "Thiếu hàng" },
  { value: "WRONG_ITEM", label: "Gửi sai hàng" },
  { value: "DAMAGED", label: "Hàng bể vỡ" },
  { value: "DEFECTIVE", label: "Hàng lỗi, không hoạt động" },
  { value: "EXPIRED", label: "Hàng hết hạn sử dụng" },
  { value: "NOT_AS_DESCRIBED", label: "Khác với mô tả" },
  { value: "USED", label: "Hàng đã qua sử dụng" },
  { value: "COUNTERFEIT", label: "Hàng giả, nhái" },
  { value: "CHANGED_MIND", label: "Đổi ý (còn nguyên tem, nhãn mác, bao bì)" },
];

export const RETURN_STATUS: Record<ReturnStatus, { label: string; color: string }> = {
  PENDING: { label: "Chờ duyệt", color: "#d48806" },
  APPROVED: { label: "Đã duyệt trả hàng", color: "#2f6a3f" },
  REJECTED: { label: "Bị từ chối", color: "#cf1322" },
};

export const RETURN_STATUSES = Object.keys(RETURN_STATUS) as ReturnStatus[];

/** Giới hạn ảnh — khớp ReturnService / ImageUploadService ở backend. */
export const RETURN_MAX_IMAGES = 2;
export const RETURN_MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const RETURN_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
export const RETURN_MAX_DESCRIPTION = 2000;
