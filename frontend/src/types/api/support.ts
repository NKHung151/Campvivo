/** Chat hỗ trợ — docs/02-ky-thuat/api.md mục 4.8 (FR-26). */

/** Một tin nhắn. `customerId` luôn là KHÁCH chủ hội thoại, kể cả tin do nhân viên gửi. Cũng là nội dung đẩy qua `/user/queue/support`. */
export interface SupportMessage {
  id: number;
  customerId: number;
  content: string;
  isFromStaff: boolean;
  createdAt: string;
}

/** Một hội thoại trong danh sách của nhân viên. */
export interface SupportConversation {
  customer: { id: number; fullName: string; email: string };
  lastMessage: SupportMessage;
}

/** Tham số cuộn tin cũ: `before` = id tin cũ nhất đang có, `limit` mặc định 30 (1–100). */
export interface SupportMessagesQuery {
  before?: number;
  limit?: number;
}
