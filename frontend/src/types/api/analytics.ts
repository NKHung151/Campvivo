import type { Money, OrderStatus } from "@/types/api/common";

/** Dashboard (FR-24) — docs/02-ky-thuat/api.md mục 4.12. Ngày dạng yyyy-MM-dd, giờ Việt Nam, gồm cả hai đầu. */
export interface AnalyticsRange {
  from?: string;
  to?: string;
}

export type AnalyticsGroupBy = "day" | "month" | "year";

export interface AnalyticsSummary {
  from: string;
  to: string;
  /** Tổng tiền đơn hoàn tất trong khoảng (theo ngày hoàn tất). */
  revenue: Money;
  completedOrders: number;
  /** Số đơn đặt trong khoảng theo trạng thái — đủ 7 trạng thái. */
  ordersByStatus: Record<OrderStatus, number>;
  newCustomers: number;
}

/** `period` = "2026-10-17" | "2026-10" | "2026"; mốc không có đơn vẫn có (0). */
export interface RevenuePoint {
  period: string;
  revenue: Money;
  orderCount: number;
}

export interface TopItem {
  type: "PRODUCT" | "COMBO";
  id: number;
  name: string;
  imageUrl: string | null;
  quantitySold: number;
  revenue: Money;
}
