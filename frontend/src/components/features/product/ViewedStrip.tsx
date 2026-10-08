"use client";

import { useEffect, useState } from "react";
import type { ProductCard } from "@/types/shop";
import { BrandStrip } from "@/components/features/catalog/LandingView";
import { useShop } from "@/components/features/shop/ShopProvider";

/** "SẢN PHẨM ĐÃ XEM" — recently viewed products from this browser's history. */
export function ViewedStrip({ current }: { current: string }) {
  const { viewed, ready } = useShop();
  const [cards, setCards] = useState<ProductCard[]>([]);
  const slugs = viewed.filter((v) => v !== current).slice(0, 10);
  const key = slugs.join(",");

  useEffect(() => {
    if (!ready || !key) return;
    let alive = true;
    fetch(`/api/mock/cards?slugs=${key}`)
      .then((r) => r.json())
      .then((d: ProductCard[]) => alive && setCards(d))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [key, ready]);

  if (cards.length > 0) return <BrandStrip id="SubProductSanPhamDaXem" title="SẢN PHẨM ĐÃ XEM" items={cards} />;
  return (
    <div id="SubProductSanPhamDaXem" className="owl-style-brand">
      <div className="headcate">
        <span className="name">SẢN PHẨM ĐÃ XEM</span>
      </div>
      <div className="cb h5" />
    </div>
  );
}
