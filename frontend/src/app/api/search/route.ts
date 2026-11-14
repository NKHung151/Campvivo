import { comboCard, toCard } from "@/services/catalogService";
import { SEARCH_PAGE_SIZE } from "@/services/searchApi";
import { isApiError } from "@/services/apiError";
import { serverGetBody } from "@/services/serverApi";
import { MIN_SEARCH_LENGTH, type SearchBody } from "@/types/api/search";

/**
 * Trang kế tiếp ("Xem thêm") của một loại kết quả tìm kiếm — `/search` ở trình duyệt gọi route này thay vì gọi thẳng backend vì
 * việc đổi `ItemSummary` / `ComboSummary` thành thẻ hiển thị (`toCard`, `comboCard`) chỉ chạy được phía server.
 * `GET /api/search?q=&type=PRODUCT|COMBO&page=` → `{ items: ProductCard[], hasMore }`.
 */
export async function GET(request: Request) {
  const params = new URL(request.url).searchParams;
  const q = (params.get("q") ?? "").trim();
  const type = params.get("type");
  const page = Math.max(1, Number(params.get("page")) || 1);
  if (q.length < MIN_SEARCH_LENGTH || (type !== "PRODUCT" && type !== "COMBO")) {
    return Response.json({ error: "Tham số không hợp lệ." }, { status: 400 });
  }
  try {
    const body = await serverGetBody<SearchBody>("/search", { params: { q, type, page, limit: SEARCH_PAGE_SIZE } });
    const items = type === "PRODUCT" ? body.data.products.map(toCard) : body.data.combos.map(comboCard);
    const hasMore = body.pagination ? body.pagination.currentPage < body.pagination.totalPages : false;
    return Response.json({ items, hasMore });
  } catch (e) {
    const status = isApiError(e) && e.status === 400 ? 400 : 502;
    return Response.json({ error: "Không tìm kiếm được lúc này." }, { status });
  }
}
