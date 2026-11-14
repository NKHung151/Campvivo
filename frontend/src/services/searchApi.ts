import { isApiError } from "@/services/apiError";
import { serverGetBody } from "@/services/serverApi";
import { MIN_SEARCH_LENGTH, type SearchBody } from "@/types/api/search";

/** Số kết quả mỗi loại ở lần tải đầu của trang `/search` (server cho tối đa 50). */
export const SEARCH_PAGE_SIZE = 24;

/**
 * Tìm kiếm gộp phía SERVER cho trang `/search` (SSR): sản phẩm, combo, bài viết kèm tổng số — api.md mục 4.3.
 * Từ khoá không hợp lệ (quá ngắn / chỉ có ký hiệu) → null để trang hiện gợi ý thay vì báo lỗi; mọi lỗi khác ném tiếp.
 */
export async function searchAll(q: string, limit = SEARCH_PAGE_SIZE): Promise<SearchBody | null> {
  const query = q.trim();
  if (query.length < MIN_SEARCH_LENGTH) return null;
  try {
    return await serverGetBody<SearchBody>("/search", { params: { q: query, limit } });
  } catch (e) {
    if (isApiError(e) && e.status === 400) return null;
    throw e;
  }
}
