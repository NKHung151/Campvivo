import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { SearchView } from "@/components/features/catalog/SearchView";
import { comboCard, searchArticles, toCard } from "@/services/catalogService";
import { searchAll } from "@/services/searchApi";

type Props = { searchParams: { q?: string } };

export function generateMetadata({ searchParams }: Props): Metadata {
  return { title: searchParams.q ? `Tìm kiếm: ${searchParams.q} | Campvivo` : "Tìm kiếm | Campvivo" };
}

/**
 * /search?q= — sản phẩm và combo lấy từ `GET /search` (FR-07, N2-11); bài viết vẫn là dữ liệu mẫu cho tới khi trang chi tiết bài viết
 * nối API (N2-10). Từ khoá không hợp lệ (< 2 ký tự, chỉ có ký hiệu) → `searchAll` trả null và trang hiện gợi ý.
 */
export default async function SearchPage({ searchParams }: Props) {
  const q = (searchParams.q ?? "").trim();
  const result = q ? await searchAll(q) : null;
  const invalid = q !== "" && result === null;
  return (
    <Shell page="search" query={q}>
      {/* key: đổi từ khoá thì dựng lại trạng thái (danh sách đã tải thêm, tab đang chọn) */}
      <SearchView
        key={q}
        query={q}
        products={result ? result.data.products.map(toCard) : []}
        combos={result ? result.data.combos.map(comboCard) : []}
        totals={result ? result.totals : { products: 0, combos: 0, articles: 0 }}
        articles={q && !invalid ? searchArticles(q) : []}
        invalid={invalid}
      />
    </Shell>
  );
}
