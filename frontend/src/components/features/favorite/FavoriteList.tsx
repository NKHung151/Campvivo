"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { GridCard } from "@/components/features/catalog/ProductCard";
import { useShop } from "@/components/features/shop/ShopProvider";
import { comboCard, toCard } from "@/lib/catalogCards";
import { getErrorMessage } from "@/lib/errorMessages";
import { listFavorites, removeFavoriteById } from "@/services/favoriteService";
import type { Favorite } from "@/types/api/favorite";

const PAGE_SIZE = 12;

/** Bài viết chưa có tab: trang blog còn đọc dữ liệu mẫu, chưa thích được (backend đã hỗ trợ — D-47). */
type Tab = "PRODUCT" | "COMBO";
const TABS: { id: Tab; label: string; empty: string; browse: string; browseHref: string }[] = [
  { id: "PRODUCT", label: "Sản phẩm", empty: "Chưa có sản phẩm yêu thích.", browse: "Xem sản phẩm", browseHref: "/products" },
  { id: "COMBO", label: "Combo", empty: "Chưa có combo yêu thích.", browse: "Xem combo", browseHref: "/combos" },
];

/** Tab "Danh sách yêu thích" của tài khoản (FR-20): mới thích trước, "Xem thêm", bỏ thích tại chỗ. */
export function FavoriteList() {
  const { forgetFavorite, notify } = useShop();
  const [tab, setTab] = useState<Tab>("PRODUCT");
  const [items, setItems] = useState<Favorite[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [removing, setRemoving] = useState<number | null>(null);

  const load = useCallback(
    (p: number) => {
      setLoading(true);
      setError(null);
      listFavorites({ type: tab, page: p, limit: PAGE_SIZE })
        .then((res) => {
          setItems((cur) => (p === 1 ? res.data : [...cur, ...res.data.filter((f) => !cur.some((c) => c.id === f.id))]));
          setPage(p);
          setTotalPages(res.pagination.totalPages);
        })
        .catch((e) => setError(getErrorMessage(e)))
        .finally(() => setLoading(false));
    },
    [tab],
  );

  useEffect(() => {
    setItems([]);
    load(1);
  }, [load]);

  const remove = async (f: Favorite) => {
    if (removing !== null) return;
    setRemoving(f.id);
    try {
      await removeFavoriteById(f.id);
      setItems((cur) => cur.filter((x) => x.id !== f.id));
      forgetFavorite({ type: f.type, id: f.item.id });
      notify("Đã bỏ khỏi danh sách yêu thích");
    } catch (e) {
      notify(getErrorMessage(e));
    } finally {
      setRemoving(null);
    }
  };

  const current = TABS.find((t) => t.id === tab)!;

  return (
    <>
      <div className="cv-fav-tabs" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            className={tab === t.id ? "active" : ""}
            onClick={() => setTab(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>
      {error && (
        <div className="cv-empty">
          {error}{" "}
          <a href="#" onClick={(e) => (e.preventDefault(), load(1))}>
            Thử lại
          </a>
        </div>
      )}
      {!error && !loading && items.length === 0 && (
        <div className="cv-empty">
          {current.empty} <Link href={current.browseHref}>{current.browse}</Link>
        </div>
      )}
      {items.length > 0 && (
        <div id="ProductCategory" style={{ width: "100%" }}>
          <div className="group_items">
            {items.map((f) => (
              <div key={f.id} className="cv-fav-item">
                {f.type === "COMBO" ? <GridCard p={comboCard(f.item)} /> : f.type === "PRODUCT" ? <GridCard p={toCard(f.item)} /> : null}
                <button type="button" className="cv-fav-remove" disabled={removing === f.id} onClick={() => void remove(f)}>
                  {removing === f.id ? "Đang bỏ..." : "Bỏ yêu thích"}
                </button>
              </div>
            ))}
          </div>
        </div>
      )}
      {loading && <div className="cv-empty">Đang tải danh sách yêu thích...</div>}
      {!loading && !error && page < totalPages && (
        <div style={{ textAlign: "center" }}>
          <button type="button" className="cv-btn ghost" onClick={() => load(page + 1)}>
            Xem thêm
          </button>
        </div>
      )}
    </>
  );
}
