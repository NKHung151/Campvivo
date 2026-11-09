import { apiClient } from "@/services/apiClient";
import type { AnalyticsGroupBy, AnalyticsRange, AnalyticsSummary, RevenuePoint, TopItem } from "@/types/api/analytics";

/** API dashboard (FR-24) — docs/02-ky-thuat/api.md mục 4.12. Chỉ ADMIN. */

export function getAnalyticsSummary(range: AnalyticsRange): Promise<AnalyticsSummary> {
  return apiClient.get<AnalyticsSummary>("/admin/analytics/summary", { params: range });
}

export function getRevenueSeries(range: AnalyticsRange, groupBy: AnalyticsGroupBy): Promise<RevenuePoint[]> {
  return apiClient.get<RevenuePoint[]>("/admin/analytics/revenue", { params: { ...range, groupBy } });
}

export function getTopItems(range: AnalyticsRange, limit = 10): Promise<TopItem[]> {
  return apiClient.get<TopItem[]>("/admin/analytics/top-items", { params: { ...range, limit } });
}
