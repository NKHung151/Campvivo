import { apiClient } from "@/services/apiClient";
import type { Page } from "@/types/api/common";
import type { SupportConversation, SupportMessage, SupportMessagesQuery } from "@/types/api/support";

/** Chat hỗ trợ phía nhân viên — docs/02-ky-thuat/api.md mục 4.8. EMPLOYEE và ADMIN. */

/** Mỗi khách một dòng, hội thoại có tin mới nhất đứng đầu. Khách chưa nhắn gì thì không có. */
export function listConversations(query: { page?: number; limit?: number } = {}): Promise<Page<SupportConversation>> {
  return apiClient.getPage<SupportConversation>("/admin/support/conversations", { params: query });
}

/** Tin của hội thoại một khách, MỚI NHẤT TRƯỚC. `customerId` không phải khách → 404. */
export function listMessages(customerId: number, query: SupportMessagesQuery = {}): Promise<SupportMessage[]> {
  return apiClient.get<SupportMessage[]>(`/admin/support/conversations/${customerId}/messages`, { params: query });
}

export function reply(customerId: number, content: string): Promise<SupportMessage> {
  return apiClient.post<SupportMessage>(`/admin/support/conversations/${customerId}/messages`, { content });
}
