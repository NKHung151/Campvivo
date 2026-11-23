import { apiClient } from "@/services/apiClient";
import type { Page, ReturnStatus } from "@/types/api/common";
import type { AdminReturnDetail, AdminReturnSummary } from "@/types/api/order";

/** API xử lý trả hàng cho Employee/Admin — docs/02-ky-thuat/api.md mục 4.10 (FR-19b, D-41). */

export function listReturns(query: { status?: ReturnStatus; page?: number; limit?: number }): Promise<Page<AdminReturnSummary>> {
  return apiClient.getPage<AdminReturnSummary>("/admin/returns", { params: query });
}

export function getReturn(id: number): Promise<AdminReturnDetail> {
  return apiClient.get<AdminReturnDetail>(`/admin/returns/${id}`);
}

/** Duyệt (cộng lại kho, đơn RETURNED) hoặc từ chối (bắt buộc `note`). */
export function processReturn(id: number, action: "APPROVE" | "REJECT", note?: string): Promise<AdminReturnDetail> {
  return apiClient.patch<AdminReturnDetail>(`/admin/returns/${id}`, { action, note });
}
