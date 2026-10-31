import { apiClient } from "@/services/apiClient";
import type { Page } from "@/types/api/common";
import type { AdjustmentRequest, InventoryItem, InventoryLogEntry, InventoryLogQuery, InventoryQuery } from "@/types/api/inventory";

/** API trang Tồn kho — docs/02-ky-thuat/api.md mục 4.10–4.11. ADMIN và EMPLOYEE. Đóng gói/tháo combo: adminComboService. */

export function listInventory(query: InventoryQuery): Promise<Page<InventoryItem>> {
  return apiClient.getPage<InventoryItem>("/admin/inventory", { params: query });
}

/** Nhập / xuất / điều chỉnh tay → dòng nhật ký vừa ghi. */
export function adjustStock(body: AdjustmentRequest): Promise<InventoryLogEntry> {
  return apiClient.post<InventoryLogEntry>("/admin/inventory/adjustments", body);
}

export function listInventoryLogs(query: InventoryLogQuery): Promise<Page<InventoryLogEntry>> {
  return apiClient.getPage<InventoryLogEntry>("/admin/inventory/logs", { params: query });
}
