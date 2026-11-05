import type { SupportMessage } from "@/types/api/support";

/** Độ dài tối đa của một tin (api.md mục 4.8). */
export const MAX_MESSAGE_LENGTH = 2000;
/** Số tin mỗi lần tải; ít hơn số này nghĩa là đã hết tin cũ. */
export const PAGE_SIZE = 30;

/**
 * Gộp tin mới vào danh sách đang hiển thị: bỏ trùng theo `id` (một tin có thể đến cả từ REST lẫn WebSocket, hoặc từ lần đồng bộ lại
 * sau khi nối lại) và xếp CŨ → MỚI theo `id`. Trả về mảng mới; không đổi mảng đầu vào.
 */
export function mergeMessages(current: SupportMessage[], incoming: SupportMessage[]): SupportMessage[] {
  const byId = new Map<number, SupportMessage>();
  for (const m of current) byId.set(m.id, m);
  for (const m of incoming) byId.set(m.id, m);
  return Array.from(byId.values()).sort((a, b) => a.id - b.id);
}

/** Nội dung đã bỏ khoảng trắng hai đầu; null nếu không gửi được (rỗng hoặc quá dài). */
export function normalizeContent(raw: string): string | null {
  const text = raw.trim();
  if (text.length === 0 || text.length > MAX_MESSAGE_LENGTH) return null;
  return text;
}

/** Địa chỉ SockJS suy ra từ địa chỉ API: `http://host:8080/api` → `http://host:8080/ws`. */
export function wsUrlFromApiUrl(apiUrl: string): string {
  return apiUrl.replace(/\/+$/, "").replace(/\/api$/, "") + "/ws";
}
