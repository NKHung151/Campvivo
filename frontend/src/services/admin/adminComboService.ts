import { apiClient } from "@/services/apiClient";
import type { Page } from "@/types/api/common";
import type { AdminComboDetail, AdminComboQuery, AdminComboSummary, ComboRequest } from "@/types/api/catalog";

/** API quản lý combo — docs/02-ky-thuat/api.md mục 4.10–4.11. */

export function listCombos(query: AdminComboQuery): Promise<Page<AdminComboSummary>> {
  return apiClient.getPage<AdminComboSummary>("/admin/combos", { params: query });
}

export function getCombo(id: number): Promise<AdminComboDetail> {
  return apiClient.get<AdminComboDetail>(`/admin/combos/${id}`);
}

export function createCombo(body: ComboRequest): Promise<AdminComboDetail> {
  return apiClient.post<AdminComboDetail>("/admin/combos", body);
}

export function updateCombo(id: number, body: ComboRequest): Promise<AdminComboDetail> {
  return apiClient.put<AdminComboDetail>(`/admin/combos/${id}`, body);
}

/** Ẩn/hiện (BR-14 — không xoá cứng). */
export function changeComboStatus(id: number, isActive: boolean): Promise<AdminComboSummary> {
  return apiClient.patch<AdminComboSummary>(`/admin/combos/${id}/status`, { isActive });
}

/** Đóng gói thêm `sets` bộ: trừ kho từng sản phẩm thành phần, cộng vào kho combo (D-22). Thiếu hàng → OUT_OF_STOCK. */
export function packCombo(id: number, sets: number): Promise<AdminComboDetail> {
  return apiClient.post<AdminComboDetail>(`/admin/combos/${id}/pack`, { sets });
}

/** Tháo `sets` bộ (chưa bị giữ chỗ): trả hàng về kho từng sản phẩm. */
export function unpackCombo(id: number, sets: number): Promise<AdminComboDetail> {
  return apiClient.post<AdminComboDetail>(`/admin/combos/${id}/unpack`, { sets });
}
