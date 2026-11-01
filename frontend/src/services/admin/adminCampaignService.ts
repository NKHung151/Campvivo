import { apiClient } from "@/services/apiClient";
import type { Page } from "@/types/api/common";
import type {
  AdminCampaignDetail,
  AdminCampaignQuery,
  AdminCampaignSummary,
  CampaignOverlap,
  CampaignRequest,
} from "@/types/api/promotion";

/** API quản lý chiến dịch giảm giá — docs/02-ky-thuat/api.md mục 4.10 (FR-17). */

export function listCampaigns(query: AdminCampaignQuery): Promise<Page<AdminCampaignSummary>> {
  return apiClient.getPage<AdminCampaignSummary>("/admin/campaigns", { params: query });
}

export function getCampaign(id: number): Promise<AdminCampaignDetail> {
  return apiClient.get<AdminCampaignDetail>(`/admin/campaigns/${id}`);
}

export function createCampaign(body: CampaignRequest): Promise<AdminCampaignDetail> {
  return apiClient.post<AdminCampaignDetail>("/admin/campaigns", body);
}

export function updateCampaign(id: number, body: CampaignRequest): Promise<AdminCampaignDetail> {
  return apiClient.put<AdminCampaignDetail>(`/admin/campaigns/${id}`, body);
}

export function deleteCampaign(id: number): Promise<void> {
  return apiClient.delete<void>(`/admin/campaigns/${id}`);
}

/** Sản phẩm đã thuộc chiến dịch khác đang bật, trùng thời gian — chỉ để cảnh báo (BR-6 lấy mức giảm lớn nhất). */
export function findOverlaps(params: {
  productIds: number[];
  startDate: string;
  endDate: string;
  excludeId?: number;
}): Promise<CampaignOverlap[]> {
  return apiClient.get<CampaignOverlap[]>("/admin/campaigns/overlaps", {
    params: { ...params, productIds: params.productIds.join(",") },
  });
}
