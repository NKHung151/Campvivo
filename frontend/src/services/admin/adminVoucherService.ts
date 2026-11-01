import { apiClient } from "@/services/apiClient";
import type { Page } from "@/types/api/common";
import type { AdminVoucher, AdminVoucherQuery, VoucherRequest } from "@/types/api/promotion";

/** API quản lý voucher — docs/02-ky-thuat/api.md mục 4.10 (FR-16). */

export function listVouchers(query: AdminVoucherQuery): Promise<Page<AdminVoucher>> {
  return apiClient.getPage<AdminVoucher>("/admin/vouchers", { params: query });
}

export function createVoucher(body: VoucherRequest): Promise<AdminVoucher> {
  return apiClient.post<AdminVoucher>("/admin/vouchers", body);
}

export function updateVoucher(id: number, body: VoucherRequest): Promise<AdminVoucher> {
  return apiClient.put<AdminVoucher>(`/admin/vouchers/${id}`, body);
}

/** Đã có đơn dùng → VOUCHER_IN_USE (chỉ được tắt). */
export function deleteVoucher(id: number): Promise<void> {
  return apiClient.delete<void>(`/admin/vouchers/${id}`);
}
