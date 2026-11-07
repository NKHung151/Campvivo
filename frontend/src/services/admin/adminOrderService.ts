import { apiClient } from "@/services/apiClient";
import type { OrderStatus, Page } from "@/types/api/common";
import type { AdminOrderDetail, AdminOrderQuery, AdminOrderSummary, OrderStatusChange, ShipmentTracking } from "@/types/api/order";

/** API quản lý đơn hàng cho Employee/Admin — docs/02-ky-thuat/api.md mục 4.10 (FR-13). */

export function listOrders(query: AdminOrderQuery): Promise<Page<AdminOrderSummary>> {
  return apiClient.getPage<AdminOrderSummary>("/admin/orders", { params: query });
}

export function getOrder(id: number): Promise<AdminOrderDetail> {
  return apiClient.get<AdminOrderDetail>(`/admin/orders/${id}`);
}

/** Chuyển trạng thái (chỉ các trạng thái trong `allowedStatuses`); huỷ bắt buộc `note`. */
export function changeOrderStatus(id: number, status: OrderStatus, note?: string): Promise<OrderStatusChange> {
  return apiClient.patch<OrderStatusChange>(`/admin/orders/${id}/status`, { status, note });
}

/** Tạo (lại) vận đơn GHN khi lần tự động trước lỗi. */
export function createShipment(id: number): Promise<AdminOrderDetail> {
  return apiClient.post<AdminOrderDetail>(`/admin/orders/${id}/shipment`);
}

export function getShipmentTracking(id: number): Promise<ShipmentTracking> {
  return apiClient.get<ShipmentTracking>(`/admin/orders/${id}/shipment`);
}
