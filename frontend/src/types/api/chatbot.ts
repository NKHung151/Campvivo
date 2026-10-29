import type { ItemType, Money } from "@/types/api/common";

/** Kiểu dữ liệu chatbot — khớp docs/02-ky-thuat/api.md mục 4.12 (FR-28). */

export interface ChatConversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export type ChatRole = "USER" | "ASSISTANT";

export interface ChatCard {
  type: ItemType;
  id: number;
  slug: string;
  name: string;
  price: Money;
  primaryImageUrl: string | null;
  availableQuantity: number;
  /** Số lượng bot đề xuất mua (tư vấn chuyến đi); null nếu chỉ giới thiệu. */
  suggestedQuantity: number | null;
  /** Lý do ngắn, vd. "Bổ sung túi ngủ cho 2 người còn lại". */
  note: string | null;
}

export interface ChatSource {
  documentId: number;
  title: string;
  page: number | null;
}

export interface ChatMessage {
  id: number;
  role: ChatRole;
  content: string;
  cards: ChatCard[];
  sources: ChatSource[];
  createdAt: string;
}

/** Sự kiện SSE khi gửi tin nhắn. */
export type ChatStreamEvent =
  | { type: "delta"; text: string }
  | { type: "cards"; cards: ChatCard[] }
  | { type: "sources"; sources: ChatSource[] }
  | { type: "done"; messageId: number; promptTokens: number | null; completionTokens: number | null }
  | { type: "error"; errorCode: string; message: string };

export type ChatbotDocumentStatus = "PROCESSING" | "READY" | "FAILED";

export interface ChatbotDocument {
  id: number;
  title: string;
  description: string | null;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: ChatbotDocumentStatus;
  errorMessage: string | null;
  chunkCount: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ChatbotDocumentDetail extends ChatbotDocument {
  chunks: { index: number; page: number | null; content: string }[];
}

export interface ChatbotDocumentQuery {
  q?: string;
  status?: ChatbotDocumentStatus;
  isActive?: boolean;
  page?: number;
  limit?: number;
}

export interface UpdateChatbotDocumentRequest {
  title: string;
  description?: string | null;
  isActive: boolean;
}

export interface RetrievalHit {
  documentId: number;
  title: string;
  page: number | null;
  content: string;
  /** 0..1, cao = giống hơn. */
  score: number;
}
