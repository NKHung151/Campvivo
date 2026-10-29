import { getToken } from "@/lib/authToken";
import { CHATBOT_API_URL } from "@/services/apiConfig";
import { ApiError, CLIENT_ERROR_CODES, networkError, toApiError } from "@/services/apiError";
import { chatbotHttp, reportUnauthorized } from "@/services/chatbotHttp";
import { readSse } from "@/services/sseReader";
import type { Page } from "@/types/api/common";
import type { ChatCard, ChatConversation, ChatMessage, ChatSource, ChatStreamEvent } from "@/types/api/chatbot";

/** Chatbot cho khách hàng (CUSTOMER) — docs/02-ky-thuat/api.md mục 4.12. */

const BASE = "/chatbot/conversations";

export function listConversations(page = 1, limit = 20): Promise<Page<ChatConversation>> {
  return chatbotHttp.getPage<ChatConversation>(BASE, { params: { page, limit } });
}

export function createConversation(): Promise<ChatConversation> {
  return chatbotHttp.post<ChatConversation>(BASE);
}

export function getMessages(conversationId: string): Promise<ChatMessage[]> {
  return chatbotHttp.get<ChatMessage[]>(`${BASE}/${conversationId}/messages`);
}

export function deleteConversation(conversationId: string): Promise<void> {
  return chatbotHttp.delete(`${BASE}/${conversationId}`);
}

/**
 * Gửi tin nhắn, nhận câu trả lời dạng SSE: gọi `onEvent` cho từng sự kiện (delta → cards → sources → done, hoặc
 * error). Lỗi trước khi bắt đầu trả lời (400/404/429, mất mạng) → ném ApiError. Huỷ bằng `signal`.
 */
export async function sendMessage(
  conversationId: string,
  content: string,
  onEvent: (e: ChatStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const token = getToken();
  let res: Response;
  try {
    res = await fetch(`${CHATBOT_API_URL}${BASE}/${conversationId}/messages`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream, application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ content }),
      signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw networkError();
  }

  const type = res.headers.get("content-type") ?? "";
  if (!res.ok || !type.includes("text/event-stream") || !res.body) {
    const body = await res.json().catch(() => null);
    const error = toApiError(res.status, body);
    if (error.status === 401 && error.errorCode === "UNAUTHORIZED") reportUnauthorized();
    throw error;
  }

  await readSse(res.body, ({ event, data }) => {
    const parsed = parseJson(data);
    const obj = (typeof parsed === "object" && parsed !== null ? parsed : {}) as Record<string, unknown>;
    const num = (v: unknown) => (typeof v === "number" ? v : null);
    switch (event) {
      case "delta":
        onEvent({ type: "delta", text: String(obj.text ?? "") });
        break;
      case "cards":
        onEvent({ type: "cards", cards: Array.isArray(parsed) ? (parsed as ChatCard[]) : [] });
        break;
      case "sources":
        onEvent({ type: "sources", sources: Array.isArray(parsed) ? (parsed as ChatSource[]) : [] });
        break;
      case "done":
        onEvent({
          type: "done",
          messageId: Number(obj.messageId),
          promptTokens: num(obj.promptTokens),
          completionTokens: num(obj.completionTokens),
        });
        break;
      case "error":
        onEvent({
          type: "error",
          errorCode: String(obj.errorCode ?? CLIENT_ERROR_CODES.UNKNOWN_ERROR),
          message: String(obj.message ?? ""),
        });
        break;
      default:
        break;
    }
  });
}

/** Lỗi `error` của luồng SSE → ApiError để hiển thị bằng getErrorMessage. */
export function streamError(e: Extract<ChatStreamEvent, { type: "error" }>): ApiError {
  return new ApiError({ status: 0, errorCode: e.errorCode, message: e.message });
}

function parseJson(data: string): unknown {
  try {
    return JSON.parse(data);
  } catch {
    return null;
  }
}
