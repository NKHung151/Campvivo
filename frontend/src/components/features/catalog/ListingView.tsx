"use client";
/* eslint-disable @next/next/no-img-element -- catalogue imagery is served as-is */

import { useMemo, useRef, useState } from "react";
import type { Filter, LinkItem, ProductCard } from "@/types/shop";
import { GridCard } from "@/components/features/catalog/ProductCard";
import { SmartLink } from "@/components/ui/SmartLink";
import { Breadcrumb } from "@/components/features/layout/Breadcrumb";
import { CompareBar, useCompare } from "@/components/features/catalog/Compare";
import { CategoryComments } from "@/components/features/catalog/CategoryComments";

const SORTS = [
  { key: "", label: "Sản phẩm ưa chuộng" },
  { key: "price", label: "Giá thấp đến cao" },
  { key: "price-desc", label: "Giá cao đến thấp" },
  { key: "new", label: "Mới nhất" },
  { key: "discount", label: "Giảm giá" },
];

const COLORS: Record<string, string[]> = {
  "Xanh lá": ["xanh lá", "xanh rêu", "green", "olive", "army"],
  "Xanh lam": ["xanh lam", "xanh dương", "xanh navy", "blue", "navy"],
  "Ghi xám": ["ghi", "xám", "grey", "gray"],
  "Rằn ri": ["rằn ri", "camo"],
  "Nhiều màu": ["nhiều màu", "multi"],
};

const fold = (s: string) =>
  s
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d");

const discountPct = (p: ProductCard) => (p.oldPrice && p.price ? 1 - p.price / p.oldPrice : 0);

/**
 * Filters are evaluated against what the mock data knows about a product (brand, name, price).
 * The catalog API should filter on real attributes; name matching is a local stand-in.
 */
function matches(p: ProductCard, group: string, option: string): boolean {
  const name = fold(p.name);
  if (/thuong hieu/.test(fold(group))) {
    const b = fold(p.brand);
    const o = fold(option).replace(/\b(usa|korea|china|italia|outdoor)\b/g, "").trim();
    return Boolean(b) && (b.includes(o) || o.includes(b) || name.includes(o));
  }
  if (/mau sac/.test(fold(group))) {
    const words = COLORS[option] ?? [option.toLowerCase()];
    return words.some((w) => name.includes(fold(w)));
  }
  const o = fold(option).split(/\s+-\s+/)[0];
  const lit = o.match(/(\d+(?:\+\d+)?)\s*(lit|l)\b/);
  if (lit) return new RegExp(`\\b${lit[1].replace("+", "\\+")}\\s*l`).test(name);
  return name.includes(o);
}

export interface ListingProps {
  title: string;
  breadcrumbs: LinkItem[];
  intro: string | null;
  sideCats: LinkItem[];
  subCats: (LinkItem & { img: string })[];
  filters: Filter[];
  guide: string | null;
  products: ProductCard[];
  bottomHtml: string | null;
  showTitleGroup?: boolean;
  tabs?: { products: string; articles: string | null } | null;
  commentsKey: string;
  emptyText?: string;
  /** Brand pages don't show the compare widget. */
  enableCompare?: boolean;
  /** Brand pages render the grid in #BrandCategory instead of #ProductCategory. */
  gridId?: string;
}

export function ListingView(props: ListingProps) {
  const { title, breadcrumbs, intro, sideCats, subCats, filters, guide, products, bottomHtml } = props;
  const [sort, setSort] = useState("");
  const [onSale, setOnSale] = useState(false);
  const [selected, setSelected] = useState<Record<string, string[]>>({});
  const [open, setOpen] = useState<Record<string, boolean>>({});
  const [swatchImg, setSwatchImg] = useState<Record<string, string>>({});
  const compare = useCompare();
  const subRef = useRef<HTMLDivElement>(null);

  const list = useMemo(() => {
    let l = products.filter((p) => !onSale || discountPct(p) > 0);
    for (const [group, opts] of Object.entries(selected)) {
      if (opts.length) l = l.filter((p) => opts.some((o) => matches(p, group.replace(/^\d+:/, ""), o)));
    }
    const by = [...l];
    if (sort === "price") by.sort((a, b) => (a.price ?? 0) - (b.price ?? 0));
    if (sort === "price-desc") by.sort((a, b) => (b.price ?? 0) - (a.price ?? 0));
    if (sort === "new") by.reverse();
    if (sort === "discount") by.sort((a, b) => discountPct(b) - discountPct(a));
    return by;
  }, [products, onSale, selected, sort]);

  const toggle = (group: string, opt: string) =>
    setSelected((s) => {
      const cur = s[group] ?? [];
      return { ...s, [group]: cur.includes(opt) ? cur.filter((x) => x !== opt) : [...cur, opt] };
    });

  const scrollSub = (dir: number) => subRef.current?.scrollBy({ left: dir * 400, behavior: "smooth" });
  const sortLabel = SORTS.find((s) => s.key === sort && sort)?.label ?? "Lựa chọn tiêu chí";

  const cards = list.map((p) => (
    <GridCard
      key={p.slug}
      p={p}
      comparing={compare.has(p.slug)}
      onCompare={props.enableCompare === false ? undefined : compare.toggle}
      activeImg={swatchImg[p.slug]}
      onSwatch={(img) => setSwatchImg((m) => ({ ...m, [p.slug]: img }))}
    />
  ));
  if (guide && cards.length >= 3) {
    cards.splice(
      3,
      0,
      <article className="item guide-card" aria-label="Hướng dẫn chọn mua" key="__guide">
        <div className="gc-inner">
          <div className="gc-body">{guide}</div>
          <div className="gc-author">— Campvivology</div>
        </div>
      </article>,
    );
  }

  return (
    <>
      <div className="pageContent">
        {breadcrumbs.length > 0 && <Breadcrumb items={breadcrumbs} />}
        <div className="pageSort border-none">
          {props.showTitleGroup === false ? (
            <h1 className="titleHead">{title}</h1>
          ) : (
            <div className="titleHead-group">
              <h1 className="titleHead">{title}</h1>
            </div>
          )}
          <div className="sortList">
            <div className="lb">
              Sắp xếp theo: <span id="sortNameId">{sortLabel}</span>
            </div>
            <div className="boundSort">
              {SORTS.map((s) => (
                <a
                  key={s.key}
                  className={sort === s.key ? "current" : ""}
                  href="#"
                  onClick={(e) => {
                    e.preventDefault();
                    setSort(s.key);
                  }}
                >
                  {s.label}
                </a>
              ))}
            </div>
          </div>
        </div>
        {intro && <p className="cate-intro">{intro}</p>}
        {props.tabs && (
          <div className="pr-category-tab">
            <span className="category-tab product-tab active">{props.tabs.products}</span>
            {props.tabs.articles && <span className="category-tab article-tab">{props.tabs.articles}</span>}
          </div>
        )}
        <div id="ProductCategoryLeft">
          {sideCats.length > 0 && (
            <div id="SubProductLeftCategory">
              <div className="TopBar">Danh mục</div>
              <div id="smoothmenu2" className="ddsmoothmenu-v">
                <ul>
                  {sideCats.map((c) => (
                    <li key={c.href}>
                      <div className="item_categoryLeft">
                        <span />
                        <SmartLink className="cate" title={c.name} href={c.href}>
                          {c.name}
                        </SmartLink>
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          )}
          <div id="promotion-option" className="filter-wrap">
            <div className="filter">
              <div className={`title${open.__promo === false ? "" : " active"}`} onClick={() => setOpen((o) => ({ ...o, __promo: o.__promo === false }))}>
                Khuyến mại
              </div>
              {open.__promo !== false && (
                <div className="filterFrame">
                  <a
                    className={`sub-filter nm${onSale ? " current" : ""}`}
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      setOnSale((v) => !v);
                    }}
                  >
                    Đang giảm giá{" "}
                    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24">
                      <path d="m19.1 9.45-9 12a.75.75 0 0 1-.6.3.92.92 0 0 1-.28-.05.74.74 0 0 1-.46-.79l.89-7.16H5.5a.74.74 0 0 1-.6-.3.76.76 0 0 1-.12-.67l3-10a.76.76 0 0 1 .72-.53h7a.74.74 0 0 1 .64.36.76.76 0 0 1 0 .73l-2.43 4.91h4.79a.76.76 0 0 1 .67.41.75.75 0 0 1-.07.79z" fill="#000" />
                    </svg>
                  </a>
                </div>
              )}
            </div>
          </div>
          {filters.length > 0 && (
            <div id="spft" className="filter-wrap">
              {filters.map((f, fi) => {
                const gk = `${fi}:${f.title}`;
                const isOpen = open[gk] !== false;
                return (
                  <div className="filter" key={gk}>
                    <div className={`title${isOpen ? " active" : ""}`} onClick={() => setOpen((o) => ({ ...o, [gk]: !isOpen }))}>
                      {f.title}
                    </div>
                    {isOpen && (
                      <div className="filterFrame">
                        {/* Mock data repeats some options (e.g. "Merida" twice); they filter identically. */}
                        {[...new Set(f.options)].map((o) => (
                          <a
                            key={o}
                            className={`sub-filter nm${selected[gk]?.includes(o) ? " current" : ""}`}
                            href="#"
                            onClick={(e) => {
                              e.preventDefault();
                              toggle(gk, o);
                            }}
                          >
                            {o}
                          </a>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
        <div id="ProductCategoryRight">
          {subCats.length > 0 && (
            <nav className="sub-category" aria-label="Danh mục con">
              <div className="horizon-nav horizon-prev" onClick={() => scrollSub(-1)}>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true" className="cdr-icon_13-5-2">
                  <path d="M7.415 11l3.295-3.295a1 1 0 00-1.417-1.412l-4.98 4.98a.997.997 0 00-.025 1.429l5.005 5.005a1 1 0 101.414-1.414L7.414 13H19a1 1 0 000-2H7.415z" />
                </svg>
              </div>
              <div className="horizon-nav horizon-next" onClick={() => scrollSub(1)}>
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true" className="cdr-icon_13-5-2">
                  <path d="M16.585 13l-3.295 3.295a1 1 0 001.417 1.412l4.98-4.98a.997.997 0 00.025-1.429l-5.005-5.005a1 1 0 00-1.414 1.414L16.586 11H5a1 1 0 000 2h11.585z" />
                </svg>
              </div>
              <div className="carousel" ref={subRef}>
                {subCats.map((c) => (
                  <SmartLink key={c.href} href={c.href} className="sc-item">
                    <div className="w-img contain">
                      <img alt={c.name} width={150} src={c.img} />
                    </div>
                    <h2 className="sc-name">{c.name}</h2>
                  </SmartLink>
                ))}
              </div>
            </nav>
          )}
          <div id={props.gridId ?? "ProductCategory"}>
            {cards.length ? (
              <div className="group_items">{cards}</div>
            ) : (
              <div className="cv-empty">{props.emptyText ?? "Không tìm thấy sản phẩm phù hợp với tiêu chí đã chọn."}</div>
            )}
          </div>
        </div>
        <div className="cb h20" />
        {bottomHtml && <div className="motaThuonghieu" dangerouslySetInnerHTML={{ __html: bottomHtml }} />}
        <div className="cb h20" />
        <CategoryComments storageKey={props.commentsKey} />
        <div className="cb h20" />
      </div>
      {props.enableCompare !== false && <CompareBar compare={compare} />}
    </>
  );
}
