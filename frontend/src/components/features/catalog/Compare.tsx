"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import type { ProductCard } from "@/types/shop";
import { productHref, vnd } from "@/lib/format";
import { useShop } from "@/components/features/shop/ShopProvider";

const MAX = 3;
const KEY = "cv-compare";

interface CompareRow {
  slug: string;
  name: string;
  img: string;
  price: number | null;
  oldPrice: number | null;
  brand: string;
  rating: number | null;
  specs: [string, string][];
}

export function useCompare() {
  const [items, setItems] = useState<ProductCard[]>([]);
  const { notify } = useShop();
  useEffect(() => {
    try {
      const raw = window.sessionStorage.getItem(KEY);
      if (raw) setItems(JSON.parse(raw) as ProductCard[]);
    } catch {
      /* ignore */
    }
  }, []);
  const persist = (next: ProductCard[]) => {
    setItems(next);
    try {
      window.sessionStorage.setItem(KEY, JSON.stringify(next));
    } catch {
      /* ignore */
    }
  };
  const toggle = useCallback(
    (p: ProductCard) => {
      setItems((cur) => {
        let next: ProductCard[];
        if (cur.some((c) => c.slug === p.slug)) next = cur.filter((c) => c.slug !== p.slug);
        else if (cur.length >= MAX) {
          notify(`Chỉ có thể so sánh tối đa ${MAX} sản phẩm`);
          return cur;
        } else next = [...cur, p];
        try {
          window.sessionStorage.setItem(KEY, JSON.stringify(next));
        } catch {
          /* ignore */
        }
        return next;
      });
    },
    [notify],
  );
  return {
    items,
    toggle,
    has: (slug: string) => items.some((i) => i.slug === slug),
    clear: () => persist([]),
    remove: (slug: string) => persist(items.filter((i) => i.slug !== slug)),
  };
}

export function CompareBar({ compare }: { compare: ReturnType<typeof useCompare> }) {
  const [open, setOpen] = useState(false);
  const [rows, setRows] = useState<CompareRow[] | null>(null);
  const { addToCart, notify } = useShop();

  useEffect(() => {
    if (!open) return;
    let alive = true;
    fetch(`/api/mock/compare?slugs=${compare.items.map((i) => i.slug).join(",")}`)
      .then((r) => r.json())
      .then((d: CompareRow[]) => alive && setRows(d))
      .catch(() => alive && setRows([]));
    return () => {
      alive = false;
    };
  }, [open, compare.items]);

  const keys = rows ? [...new Set(rows.flatMap((r) => r.specs.map(([k]) => k)))].slice(0, 12) : [];

  return (
    <>
      <div id="cmp-bar" className={`cmp-bar${compare.items.length ? " visible" : ""}`}>
        <div className="cmp-bar-inner">
          <div className="cmp-bar-items">
            {Array.from({ length: MAX }, (_, i) => {
              const it = compare.items[i];
              return it ? (
                <div className="cmp-bar-item" key={it.slug}>
                  <img src={it.img} alt="" />
                  <span className="cmp-bar-name">{it.name}</span>
                  <button className="cmp-bar-remove" type="button" onClick={() => compare.remove(it.slug)}>
                    ×
                  </button>
                </div>
              ) : (
                <div className="cmp-bar-slot" key={i}>
                  + Thêm
                </div>
              );
            })}
          </div>
          <div className="cmp-bar-actions">
            <button
              className="cmp-btn-go"
              type="button"
              disabled={compare.items.length < 2}
              onClick={() => {
                setRows(null);
                setOpen(true);
              }}
            >
              So sánh (<span className="cmp-bar-count">{compare.items.length}</span>)
            </button>
            <button className="cmp-btn-clear" type="button" onClick={compare.clear}>
              Xóa tất cả
            </button>
          </div>
        </div>
      </div>
      <div id="cmp-popup" className={`cmp-popup${open ? " active" : ""}`}>
        <div className="cmp-popup-overlay" onClick={() => setOpen(false)} />
        <div className="cmp-popup-sheet">
          <div className="cmp-sheet-handle" />
          <button className="cmp-popup-close" type="button" onClick={() => setOpen(false)}>
            ×
          </button>
          <div className="cmp-popup-body">
            {!rows ? (
              <div className="cmp-loading">Đang tải...</div>
            ) : (
              <>
                <div className="cmp-prod-cards">
                  {rows.map((r) => (
                    <div className="cmp-prod-card" key={r.slug}>
                      <div className="cmp-card-img">
                        <img src={r.img} alt={r.name} />
                      </div>
                      <div className="cmp-card-name">{r.name}</div>
                      <div className="cmp-card-price">{vnd(r.price)}</div>
                    </div>
                  ))}
                </div>
                <div className="cmp-spec-row cmp-spec-row-alt">
                  <div className="cmp-spec-label">
                    <span className="cmp-spec-label-pill">Thương hiệu</span>
                  </div>
                  {rows.map((r) => (
                    <div className="cmp-spec-val" key={r.slug}>
                      {r.brand || "—"}
                    </div>
                  ))}
                </div>
                {keys.map((k, i) => (
                  <div className={`cmp-spec-row${i % 2 ? " cmp-spec-row-alt" : ""}`} key={k}>
                    <div className="cmp-spec-label">
                      <span className="cmp-spec-label-pill">{k}</span>
                    </div>
                    {rows.map((r) => (
                      <div className="cmp-spec-val" key={r.slug}>
                        {r.specs.find(([kk]) => kk === k)?.[1] ?? "—"}
                      </div>
                    ))}
                  </div>
                ))}
                <div className="cmp-footer">
                  {rows.map((r) => (
                    <div key={r.slug} style={{ display: "flex", gap: 8 }}>
                      <Link className="cmp-footer-detail" href={productHref(r.slug)}>
                        Xem chi tiết
                      </Link>
                      <button
                        type="button"
                        className="cmp-footer-cart"
                        onClick={() => {
                          if (!r.price) return;
                          addToCart({ slug: r.slug, name: r.name, img: r.img, sku: "", variant: null, price: r.price });
                          notify("Đã thêm vào giỏ hàng");
                        }}
                      >
                        Thêm vào giỏ
                      </button>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      </div>
    </>
  );
}
