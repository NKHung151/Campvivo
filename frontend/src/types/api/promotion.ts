import type { ItemSummary } from "@/types/api/catalog";
import type { DiscountType, Money } from "@/types/api/common";

/** Khuyến mãi — docs/02-ky-thuat/api.md mục 4.5 (khách) và 4.10 (admin). FR-16, FR-17, D-29. */

/** Mã công khai trên PromoBar (`GET /vouchers/public`). */
export interface PublicVoucher {
  code: string;
  discountType: DiscountType;
  discountValue: Money;
  maxDiscount: Money | null;
  minOrderValue: Money;
  expiryDate: string;
}

/** Một mã trong danh sách ở popup voucher trang `/cart` (`GET /vouchers/available`). */
export interface AvailableVoucher extends PublicVoucher {
  remaining: number;
  usable: boolean;
  reason: "VOUCHER_MIN_ORDER_NOT_MET" | "VOUCHER_ALREADY_USED" | null;
  missingAmount: Money | null;
  estimatedDiscount: Money;
  best: boolean;
}

/** Một phần tử của `GET /campaigns` — chiến dịch đang hiệu lực. `productCount`: sản phẩm đang bán. */
export interface CampaignSummary {
  id: number;
  name: string;
  slug: string;
  bannerUrl: string | null;
  discountType: DiscountType;
  discountValue: Money;
  endDate: string;
  productCount: number;
}

/** `GET /campaigns/{slug}` — chỉ chiến dịch đang hiệu lực (ngược lại 404). */
export interface CampaignDetail {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  bannerUrl: string | null;
  discountType: DiscountType;
  discountValue: Money;
  startDate: string;
  endDate: string;
  products: ItemSummary[];
}

export type VoucherStatus = "UPCOMING" | "ACTIVE" | "EXPIRED" | "EXHAUSTED" | "INACTIVE";
export type CampaignStatus = "UPCOMING" | "RUNNING" | "ENDED" | "INACTIVE";

export interface VoucherRequest {
  code: string;
  discountType: DiscountType;
  discountValue: Money;
  maxDiscount?: Money | null;
  minOrderValue: Money;
  quantity: number;
  startDate: string;
  expiryDate: string;
  isActive: boolean;
  isPublic: boolean;
}

export interface AdminVoucher extends Omit<VoucherRequest, "maxDiscount"> {
  id: number;
  maxDiscount: Money | null;
  usedCount: number;
  status: VoucherStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CampaignRequest {
  name: string;
  slug?: string | null;
  description?: string | null;
  bannerUrl?: string | null;
  discountType: DiscountType;
  discountValue: Money;
  startDate: string;
  endDate: string;
  isActive: boolean;
  productIds: number[];
}

export interface AdminCampaignSummary {
  id: number;
  name: string;
  slug: string;
  bannerUrl: string | null;
  discountType: DiscountType;
  discountValue: Money;
  startDate: string;
  endDate: string;
  isActive: boolean;
  status: CampaignStatus;
  productCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface AdminCampaignProduct {
  id: number;
  name: string;
  slug: string;
  primaryImageUrl: string | null;
  isActive: boolean;
  price: Money;
  /** Giá nếu chỉ áp chiến dịch này. */
  campaignPrice: Money;
}

export interface AdminCampaignDetail extends AdminCampaignSummary {
  description: string | null;
  products: AdminCampaignProduct[];
}

export interface CampaignOverlap {
  productId: number;
  campaignId: number;
  campaignName: string;
}

export interface AdminCampaignQuery {
  q?: string;
  status?: CampaignStatus;
  page?: number;
  limit?: number;
}

export interface AdminVoucherQuery {
  q?: string;
  status?: VoucherStatus;
  page?: number;
  limit?: number;
}
