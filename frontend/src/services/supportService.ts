import { apiClient } from "@/services/apiClient";
import type { SupportMessage, SupportMessagesQuery } from "@/types/api/support";

/** Chat hỗ trợ phía khách — docs/02-ky-thuat/api.md mục 4.8. Chỉ CUSTOMER. Hội thoại lấy theo token, không gửi id khách. */

/** Tin của hội thoại của chính mình, MỚI NHẤT TRƯỚC. Ít hơn `limit` tin = đã hết tin cũ. */
export function listMyMessages(query: SupportMessagesQuery = {}): Promise<SupportMessage[]> {
  return apiClient.get<SupportMessage[]>("/support/messages", { params: query });
}

export function sendMyMessage(content: string): Promise<SupportMessage> {
  return apiClient.post<SupportMessage>("/support/messages", { content });
}
