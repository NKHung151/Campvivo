import type { Money } from "@/types/api/common";

/** Vận chuyển GHN — khớp docs/02-ky-thuat/api.md mục 4.12 (D-27). */

export interface Province {
  id: number;
  name: string;
}

export interface District {
  id: number;
  name: string;
}

export interface Ward {
  code: string;
  name: string;
}

export interface ShippingServiceOption {
  serviceId: number;
  serviceTypeId: number;
  name: string;
}

export interface ShippingFeeRequest {
  toDistrictId: number;
  toWardCode: string;
  /** Tổng số món (1..999); backend quy ra cân nặng. */
  quantity: number;
  /** Tạm tính − giảm giá, để xét miễn phí ship. */
  orderValue?: Money;
}

export interface ShippingFee {
  /** Phí khách trả (0 khi được miễn phí). */
  fee: Money;
  /** Phí GHN báo — shop trả GHN. */
  carrierFee: Money;
  freeShipping: boolean;
  freeShippingThreshold: Money;
}
