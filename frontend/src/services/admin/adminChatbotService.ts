import { chatbotHttp } from "@/services/chatbotHttp";
import type { Page } from "@/types/api/common";
import type {
  ChatbotDocument,
  ChatbotDocumentDetail,
  ChatbotDocumentQuery,
  RetrievalHit,
  UpdateChatbotDocumentRequest,
} from "@/types/api/chatbot";

/** API admin quản lý tài liệu cho chatbot — docs/02-ky-thuat/api.md mục 4.12. */

const BASE = "/admin/chatbot/documents";

export function listDocuments(query: ChatbotDocumentQuery): Promise<Page<ChatbotDocument>> {
  return chatbotHttp.getPage<ChatbotDocument>(BASE, { params: query });
}

export function getDocument(id: number): Promise<ChatbotDocumentDetail> {
  return chatbotHttp.get<ChatbotDocumentDetail>(`${BASE}/${id}`);
}

/** Tải lên → PROCESSING, xử lý nền (đọc, chia đoạn, embed). Tiêu đề trống → lấy theo tên file. */
export function uploadDocument(file: File, title?: string, description?: string): Promise<ChatbotDocument> {
  const form = new FormData();
  form.append("file", file);
  if (title?.trim()) form.append("title", title.trim());
  if (description?.trim()) form.append("description", description.trim());
  return chatbotHttp.post<ChatbotDocument>(BASE, form, { timeout: 120_000 });
}

export function updateDocument(id: number, body: UpdateChatbotDocumentRequest): Promise<ChatbotDocument> {
  return chatbotHttp.put<ChatbotDocument>(`${BASE}/${id}`, body);
}

/** Thay file gốc → nạp lại (bot tạm không dùng tài liệu này cho tới khi xong). */
export function replaceDocumentFile(id: number, file: File): Promise<ChatbotDocument> {
  const form = new FormData();
  form.append("file", file);
  return chatbotHttp.put<ChatbotDocument>(`${BASE}/${id}/file`, form, { timeout: 120_000 });
}

export function reindexDocument(id: number): Promise<ChatbotDocument> {
  return chatbotHttp.post<ChatbotDocument>(`${BASE}/${id}/reindex`);
}

export function deleteDocument(id: number): Promise<void> {
  return chatbotHttp.delete(`${BASE}/${id}`);
}

export function downloadDocumentFile(id: number) {
  return chatbotHttp.getBlob(`${BASE}/${id}/file`);
}

/** Admin hỏi thử xem bot tìm được đoạn nào (không gọi model chat). */
export function searchTest(question: string): Promise<RetrievalHit[]> {
  return chatbotHttp.post<RetrievalHit[]>(`${BASE}/search-test`, { question });
}
