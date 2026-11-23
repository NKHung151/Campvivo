import { apiClient } from "@/services/apiClient";
import type {
  CreateReturnRequest,
  CreateOrderRequest,
  CreateOrderResponse,
  OrderDetail,
  OrderPreview,
  OrderPreviewRequest,
  OrderSummary,
  ReturnRequestDto,
  VnpayReturnResult,
} from "@/types/api/order";
import type { OrderStatus, Page } from "@/types/api/common";

/** Đặt hàng, xem đơn, kết quả VNPay — docs/02-ky-thuat/api.md mục 4.5. */

export function previewOrder(body: OrderPreviewRequest): Promise<OrderPreview> {
  return apiClient.post<OrderPreview>("/orders/preview", body);
}

export function createOrder(body: CreateOrderRequest): Promise<CreateOrderResponse> {
  return apiClient.post<CreateOrderResponse>("/orders", body);
}

export function getOrder(id: number): Promise<OrderDetail> {
  return apiClient.get<OrderDetail>(`/orders/${id}`);
}

/** Đơn của tôi, mới nhất trước (FR-12). */
export function listMyOrders(params: { status?: OrderStatus; page?: number; limit?: number }): Promise<Page<OrderSummary>> {
  return apiClient.getPage<OrderSummary>("/orders", { params });
}

/** Huỷ đơn khi còn PENDING; trả về chi tiết đơn sau khi huỷ. */
export function cancelOrder(id: number, reason?: string): Promise<OrderDetail> {
  return apiClient.post<OrderDetail>(`/orders/${id}/cancel`, { reason });
}

/** Gửi yêu cầu trả hàng (FR-19a, D-41): multipart lý do + mô tả + 1–2 ảnh (server tải ảnh lên Cloudinary). */
export function createReturn(orderId: number, body: CreateReturnRequest): Promise<ReturnRequestDto> {
  const form = new FormData();
  form.append("reason", body.reason);
  if (body.description) form.append("description", body.description);
  body.images.forEach((f) => form.append("images", f));
  return apiClient.post<ReturnRequestDto>(`/orders/${orderId}/returns`, form, { timeout: 60_000 });
}

/**
 * Chuyển nguyên query `vnp_*` VNPay trả về trang /payment/vnpay-return cho backend kiểm chữ ký và ghi nhận (D-28).
 * Endpoint công khai: không gửi token (token hết hạn sẽ làm backend từ chối).
 */
export function confirmVnpayReturn(query: string): Promise<VnpayReturnResult> {
  return apiClient.get<VnpayReturnResult>(`/vnpay/return?${query}`, { skipAuth: true });
}
