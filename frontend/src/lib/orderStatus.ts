import type { OrderStatus, PaymentMethod, PaymentStatus } from "@/types/api/common";

/** Nhãn + màu trạng thái đơn/thanh toán/vận đơn — dùng chung storefront và admin (không phụ thuộc antd). */

export const ORDER_STATUS: Record<OrderStatus, { label: string; color: string }> = {
  PENDING: { label: "Chờ xử lý", color: "#d48806" },
  CONFIRMED: { label: "Đã xác nhận", color: "#1677ff" },
  SHIPPING: { label: "Đang giao", color: "#13a8a8" },
  COMPLETED: { label: "Hoàn tất", color: "#2f6a3f" },
  CANCELLED: { label: "Đã huỷ", color: "#8c8c8c" },
  PAYMENT_FAILED: { label: "Thanh toán thất bại", color: "#cf1322" },
  RETURNED: { label: "Đã trả hàng", color: "#722ed1" },
};

export const ORDER_STATUSES = Object.keys(ORDER_STATUS) as OrderStatus[];

export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  COD: "Thanh toán khi nhận hàng",
  VNPAY: "VNPay",
};

export const PAYMENT_STATUS: Record<PaymentStatus, { label: string; color: string }> = {
  PENDING: { label: "Chưa thanh toán", color: "#d48806" },
  SUCCESS: { label: "Đã thanh toán", color: "#2f6a3f" },
  FAILED: { label: "Thất bại", color: "#cf1322" },
};

/** Nhãn nút chuyển trạng thái cho nhân viên (FR-13). */
export const STAFF_ACTION_LABEL: Partial<Record<OrderStatus, string>> = {
  CONFIRMED: "Xác nhận đơn",
  CANCELLED: "Huỷ đơn",
  SHIPPING: "Bàn giao vận chuyển",
  COMPLETED: "Hoàn tất (khách đã nhận)",
};

/** Mã trạng thái vận đơn GHN → tiếng Việt (không có trong bảng thì hiện nguyên mã). */
const GHN_STATUS: Record<string, string> = {
  ready_to_pick: "Chờ lấy hàng",
  picking: "Đang lấy hàng",
  cancel: "Đã huỷ vận đơn",
  money_collect_picking: "Đang thu tiền người gửi",
  picked: "Đã lấy hàng",
  storing: "Đang lưu kho",
  transporting: "Đang luân chuyển",
  sorting: "Đang phân loại",
  delivering: "Đang giao hàng",
  money_collect_delivering: "Đang thu tiền người nhận",
  delivered: "Đã giao hàng",
  delivery_fail: "Giao hàng thất bại",
  waiting_to_return: "Chờ trả hàng",
  return: "Trả hàng",
  return_transporting: "Đang luân chuyển hàng trả",
  return_sorting: "Đang phân loại hàng trả",
  returning: "Đang trả hàng",
  return_fail: "Trả hàng thất bại",
  returned: "Đã trả hàng cho shop",
  exception: "Đơn ngoại lệ",
  damage: "Hàng bị hư hỏng",
  lost: "Hàng bị thất lạc",
};

export const ghnStatusLabel = (status: string) => GHN_STATUS[status] ?? status;

export const fmtDateTime = (iso: string | null | undefined) =>
  iso ? new Date(iso).toLocaleString("vi-VN", { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit", year: "numeric" }) : "";
