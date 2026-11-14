"use client";
/* eslint-disable @next/next/no-img-element -- catalogue imagery is served as-is */

import Link from "next/link";
import { useState } from "react";
import type { ArticleCard, ProductCard } from "@/types/shop";
import type { SearchSuggestion, SearchTotals } from "@/types/api/search";
import { articleHref } from "@/lib/format";
import { SearchCard } from "@/components/features/catalog/ProductCard";
import { searchMore } from "@/services/searchService";

type Tab = "sp" | "cb" | "bv";
type Kind = "PRODUCT" | "COMBO";

/** Số thẻ bài viết hiện mỗi lần (bài viết còn lọc phía client trên dữ liệu mẫu, chưa qua API). */
const ARTICLE_PAGE = 30;

interface Props {
  query: string;
  /** Lần tải đầu của sản phẩm và combo từ `GET /search` (server); các trang sau lấy qua "Xem thêm". */
  products: ProductCard[];
  combos: ProductCard[];
  /** Tổng số kết quả thật của từng loại (kể cả phần chưa tải) để tab hiện "(n)". */
  totals: SearchTotals;
  articles: ArticleCard[];
  /** Từ khoá có nhưng server từ chối (ngắn hơn 2 ký tự hoặc chỉ có ký hiệu): hiện gợi ý thay vì "không có kết quả". */
  invalid: boolean;
  /** "Có phải bạn muốn tìm…?": từ khoá đã sửa lỗi gõ, chỉ có khi từ khoá gốc không ra kết quả nào. */
  suggestion: SearchSuggestion | null;
}

/** Thêm các thẻ mới vào danh sách, bỏ thẻ đã có (cùng slug) để một mặt hàng không hiện hai lần khi dữ liệu dịch chuyển giữa các trang. */
function appendUnique(current: ProductCard[], incoming: ProductCard[]): ProductCard[] {
  const seen = new Set(current.map((c) => c.slug));
  return [...current, ...incoming.filter((c) => !seen.has(c.slug))];
}

/**
 * Trang kết quả tìm kiếm (`/search?q=`): ba tab "Sản phẩm (n)", "Combo (n)", "Bài viết (n)". Sản phẩm và combo lấy từ `GET /search`
 * (phân trang thật bằng "Xem thêm"); bài viết đang là dữ liệu mẫu cho tới khi trang chi tiết bài viết nối API (N2-10).
 */
export function SearchView({ query, products, combos, totals, articles, invalid, suggestion }: Props) {
  const [tab, setTab] = useState<Tab>(totals.products || (!totals.combos && !articles.length) ? "sp" : totals.combos ? "cb" : "bv");
  const [lists, setLists] = useState<Record<Kind, ProductCard[]>>({ PRODUCT: products, COMBO: combos });
  const [more, setMore] = useState<Record<Kind, boolean>>({ PRODUCT: products.length < totals.products, COMBO: combos.length < totals.combos });
  const [loading, setLoading] = useState<Kind | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [bvShown, setBvShown] = useState(ARTICLE_PAGE);

  async function loadMore(kind: Kind) {
    if (loading) return;
    setLoading(kind);
    setError(null);
    try {
      // Mỗi trang đầy thì kích thước trang bằng số thẻ đã nhận ở lần đầu; trang kế tiếp = số trang đã tải + 1.
      const pageSize = Math.max(1, kind === "PRODUCT" ? products.length : combos.length);
      const next = Math.floor(lists[kind].length / pageSize) + 1;
      const res = await searchMore(query, kind, next);
      setLists((cur) => ({ ...cur, [kind]: appendUnique(cur[kind], res.items) }));
      setMore((cur) => ({ ...cur, [kind]: res.hasMore }));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Không tải thêm được kết quả.");
    } finally {
      setLoading(null);
    }
  }

  const renderCards = (kind: Kind, emptyText: string) => (
    <>
      {lists[kind].length ? (
        <div className="groupItems">
          {lists[kind].map((p) => (
            <SearchCard key={p.slug} p={p} />
          ))}
        </div>
      ) : (
        <p className="cv-empty">{emptyText}</p>
      )}
      <div className="cb h20" />
      {more[kind] && (
        <div className="tac">
          <a className="btn btn-primary" onClick={() => void loadMore(kind)} aria-busy={loading === kind}>
            {loading === kind ? "Đang tải…" : "Xem thêm"}
          </a>
        </div>
      )}
      {error && tab !== "bv" && (
        <p className="cv-empty" role="alert">
          {error}
        </p>
      )}
    </>
  );

  if (invalid || !query) {
    return (
      <div className="pageContent">
        <div id="ProductCategory" className="container">
          <div className="cb h10" />
          <p className="cv-empty">
            {query ? "Nhập ít nhất 2 ký tự (có chữ hoặc số) để tìm kiếm." : "Nhập từ khoá vào ô tìm kiếm để bắt đầu."}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="pageContent">
      <div id="ProductCategory" className="container">
        <div className="cb h10" />
        {suggestion && (
          <p className="cv-empty cv-did-you-mean" role="status">
            Không tìm thấy kết quả cho &quot;{query}&quot;. Có phải bạn muốn tìm{" "}
            <Link href={`/search?q=${encodeURIComponent(suggestion.q)}`} prefetch={false}>
              <strong>{suggestion.q}</strong>
            </Link>
            ?
          </p>
        )}
        <div className="toptab">
          <a className={`headtab fl${tab === "sp" ? " active" : ""}`} onClick={() => setTab("sp")}>
            <span />
            Sản phẩm ({totals.products})
          </a>
          <a className={`headtab${tab === "cb" ? " active" : ""}`} onClick={() => setTab("cb")}>
            <span />
            Combo ({totals.combos})
          </a>
          <a className={`headtab tabbaiviet${tab === "bv" ? " active" : ""}`} onClick={() => setTab("bv")}>
            <span />
            Bài viết ({articles.length})
          </a>
        </div>

        <div id="tabsanpham" className={`contenttab cvPromotion${tab === "sp" ? " active" : ""}`}>
          {renderCards("PRODUCT", `Không tìm thấy sản phẩm nào cho "${query}".`)}
        </div>

        <div id="tabcombo" className={`contenttab cvPromotion${tab === "cb" ? " active" : ""}`}>
          {renderCards("COMBO", `Không tìm thấy combo nào cho "${query}".`)}
        </div>

        <div id="tabbaiviet" className={`contenttab${tab === "bv" ? " active" : ""}`}>
          {articles.length ? (
            <div className="group_items">
              {articles.slice(0, bvShown).map((a) => (
                <Link className="item article" href={articleHref(a.slug)} prefetch={false} key={a.slug}>
                  <div className="wImage">
                    <span className="khungAnhCrop">
                      <img loading="lazy" alt={a.title} width={200} src={a.img} />
                    </span>
                  </div>
                  <h3 className="title">{a.title}</h3>
                </Link>
              ))}
            </div>
          ) : (
            <p className="cv-empty">Không tìm thấy bài viết nào cho &quot;{query}&quot;.</p>
          )}
          <div className="cb" />
          {bvShown < articles.length && (
            <div className="tac">
              <a className="btn btn-primary" onClick={() => setBvShown((n) => n + ARTICLE_PAGE)}>
                Xem thêm
              </a>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
