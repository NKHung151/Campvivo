"use client";
/* eslint-disable @next/next/no-img-element -- catalogue imagery is served as-is */

import Link from "next/link";
import { useState } from "react";
import type { ArticleCard, ProductCard } from "@/types/shop";
import { articleHref } from "@/lib/format";
import { SearchCard } from "@/components/features/catalog/ProductCard";

/** Shows 30 results per tab and appends 30 more per "Xem thêm". */
const PAGE = 30;

/**
 * Search page (`/search?q=`): "Sản phẩm (n)" and "Bài viết (n)" tabs.
 */
export function SearchView({ query, products, articles }: { query: string; products: ProductCard[]; articles: ArticleCard[] }) {
  const [tab, setTab] = useState<"sp" | "bv">(products.length || !articles.length ? "sp" : "bv");
  const [spShown, setSpShown] = useState(PAGE);
  const [bvShown, setBvShown] = useState(PAGE);

  return (
    <div className="pageContent">
      <div id="ProductCategory" className="container">
        <div className="cb h10" />
        <div className="toptab">
          <a className={`headtab fl${tab === "sp" ? " active" : ""}`} onClick={() => setTab("sp")}>
            <span />
            Sản phẩm ({products.length})
          </a>
          <a className={`headtab tabbaiviet${tab === "bv" ? " active" : ""}`} onClick={() => setTab("bv")}>
            <span />
            Bài viết ({articles.length})
          </a>
        </div>

        <div id="tabsanpham" className={`contenttab cvPromotion${tab === "sp" ? " active" : ""}`}>
          {products.length ? (
            <div className="groupItems">
              {products.slice(0, spShown).map((p) => (
                <SearchCard key={p.slug} p={p} />
              ))}
            </div>
          ) : (
            <p className="cv-empty">Không tìm thấy sản phẩm nào cho &quot;{query}&quot;.</p>
          )}
          <div className="cb h20" />
          {spShown < products.length && (
            <div className="tac">
              <a className="btn btn-primary" onClick={() => setSpShown((n) => n + PAGE)}>
                Xem thêm
              </a>
            </div>
          )}
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
              <a className="btn btn-primary" onClick={() => setBvShown((n) => n + PAGE)}>
                Xem thêm
              </a>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
