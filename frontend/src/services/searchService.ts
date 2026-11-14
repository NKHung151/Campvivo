import { apiClient } from "@/services/apiClient";
import type { SearchSuggestionItem } from "@/types/api/search";
import type { ProductCard } from "@/types/shop";

/** Số gợi ý hiện trong danh sách thả xuống của ô tìm kiếm. */
export const SUGGESTION_LIMIT = 6;

/**
 * Gợi ý khi gõ (`GET /search/suggestions`, api.md mục 4.3): sản phẩm và combo đang bán khớp đầu tên, chứa từ khoá hoặc gần giống.
 * Công khai nên không gắn token (một token hết hạn còn sót lại sẽ bị backend từ chối ngay cả ở endpoint công khai). `signal` để huỷ
 * yêu cầu cũ khi người dùng gõ tiếp.
 */
export function fetchSuggestions(q: string, signal?: AbortSignal): Promise<SearchSuggestionItem[]> {
  return apiClient.get<SearchSuggestionItem[]>("/search/suggestions", { params: { q, limit: SUGGESTION_LIMIT }, signal, skipAuth: true });
}

/**
 * "Xem thêm" của trang `/search` phía CLIENT: gọi route `/api/search` (cùng origin) lấy trang kế tiếp của MỘT loại kết quả đã đổi
 * thành thẻ hiển thị. Không gọi thẳng backend vì việc tạo thẻ chỉ chạy được phía server. Lỗi → ném `Error` với thông báo tiếng Việt.
 */
export async function searchMore(q: string, type: "PRODUCT" | "COMBO", page: number): Promise<{ items: ProductCard[]; hasMore: boolean }> {
  const url = `/api/search?${new URLSearchParams({ q, type, page: String(page) })}`;
  let response: Response;
  try {
    response = await fetch(url, { cache: "no-store" });
  } catch {
    throw new Error("Không kết nối được máy chủ. Vui lòng thử lại.");
  }
  if (!response.ok) throw new Error("Không tải thêm được kết quả. Vui lòng thử lại.");
  return (await response.json()) as { items: ProductCard[]; hasMore: boolean };
}
