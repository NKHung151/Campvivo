import { apiClient } from "@/services/apiClient";
import type { District, Province, ShippingFee, ShippingFeeRequest, ShippingServiceOption, Ward } from "@/types/api/shipping";

/**
 * API vận chuyển GHN — docs/02-ky-thuat/api.md mục 4.12. Token GHN chỉ nằm ở backend; frontend không gọi GHN trực tiếp.
 * Endpoint công khai nên không gửi token (token cũ hết hạn sẽ làm backend từ chối cả endpoint công khai).
 */

const PUBLIC = { skipAuth: true } as const;

export function getProvinces(): Promise<Province[]> {
  return apiClient.get<Province[]>("/shipping/provinces", PUBLIC);
}

export function getDistricts(provinceId: number): Promise<District[]> {
  return apiClient.get<District[]>("/shipping/districts", { ...PUBLIC, params: { provinceId } });
}

export function getWards(districtId: number): Promise<Ward[]> {
  return apiClient.get<Ward[]>("/shipping/wards", { ...PUBLIC, params: { districtId } });
}

export function getShippingServices(toDistrictId: number): Promise<ShippingServiceOption[]> {
  return apiClient.get<ShippingServiceOption[]>("/shipping/services", { ...PUBLIC, params: { toDistrictId } });
}

export function getShippingFee(body: ShippingFeeRequest): Promise<ShippingFee> {
  return apiClient.post<ShippingFee>("/shipping/fee", body, PUBLIC);
}
