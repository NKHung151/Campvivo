import { apiClient } from "@/services/apiClient";
import type { AvailableVoucher, PublicVoucher } from "@/types/api/promotion";

/** Voucher phía khách — docs/02-ky-thuat/api.md mục 4.5 (D-29). */

/** Mã công khai tính trên các dòng giỏ đang chọn (rỗng = cả giỏ); mã dùng được trước. Cần đăng nhập CUSTOMER. */
export function getAvailableVouchers(cartItemIds?: number[]): Promise<AvailableVoucher[]> {
  const params = cartItemIds && cartItemIds.length > 0 ? { cartItemIds: cartItemIds.join(",") } : undefined;
  return apiClient.get<AvailableVoucher[]>("/vouchers/available", { params });
}

/** Mã công khai đang có (PromoBar). Endpoint công khai: không gửi token. */
export function getPublicVouchers(): Promise<PublicVoucher[]> {
  return apiClient.get<PublicVoucher[]>("/vouchers/public", { skipAuth: true });
}
