import type { ProductCard } from "@/types/shop";

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
