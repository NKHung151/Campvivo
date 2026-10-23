"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import type { LinkItem, ProductCard } from "@/types/shop";
import { productHref, vnd } from "@/lib/format";
import { OwlCarousel } from "@/components/ui/OwlCarousel";
import { SmartLink } from "@/components/ui/SmartLink";
import { CategoryComments } from "@/components/features/catalog/CategoryComments";

/** `.owl-style-brand` product strip used on landing pages and below the PDP. */
export function BrandStrip({ title, href, items, id }: { title: string; href?: string | null; items: ProductCard[]; id?: string }) {
  if (!items.length) return null;
  return (
    <div className="owl-style-brand" id={id}>
      {href ? (
        <SmartLink className="headcate" href={href}>
          <span className="name">{title}</span>
        </SmartLink>
      ) : (
        <div className="headcate">
          <span className="name">{title}</span>
        </div>
      )}
      <div className="cb h5" />
      <OwlCarousel items={5} margin={0} responsive={{ 0: 2, 768: 3, 1024: 5 }}>
        {items.map((p) => (
          <Link key={p.slug} className="item" title={p.name} href={productHref(p.slug)} prefetch={false}>
            {" "}
            <div className="wImage">
              {" "}
              <span className="khungAnhCrop0">
                <img loading="lazy" alt={p.name} width={350} src={p.img} />
              </span>{" "}
              {p.discount && (
                <span className={`saleoff ${p.discountClass ?? "mauPercenDo"}`}>
                  <b>{p.discount.replace(/\s*off$/i, "")}</b> off
                </span>
              )}
            </div>{" "}
            <div className="brand">{p.brand}</div> <h3 className="title">{p.name}</h3>{" "}
            <div className="core">
              <div className="stars" style={{ width: `${p.rating ?? 100}%` }} />
            </div>
            <div className="price">
              {vnd(p.price)} {p.oldPrice ? <span>{vnd(p.oldPrice)}</span> : null}
            </div>
          </Link>
        ))}
      </OwlCarousel>
    </div>
  );
}

export function LandingView({
  tiles,
  carousels,
  commentsKey,
  tileClass = "image",
}: {
  tiles: (LinkItem & { img: string })[];
  carousels: { title: string; href: string | null; items: ProductCard[] }[];
  commentsKey: string;
  tileClass?: string;
}) {
  return (
    <div className="pageContent">
      <div id="ProductIndexBanner">
        <div id="SubProductCateIndex">
          <div className="SubListCatePage">
            {tiles.map((t) => (
              <SmartLink className={tileClass} href={t.href} key={t.href}>
                {" "}
                <img alt={t.name} width={300} src={t.img} /> <span className="cate">{t.name}</span>{" "}
              </SmartLink>
            ))}
          </div>
        </div>
        <div id="SubBrandCateIndex">
          {carousels.map((c) => (
            <BrandStrip key={c.title} title={c.title} href={c.href} items={c.items} />
          ))}
        </div>
      </div>
      <CategoryComments storageKey={commentsKey} />
    </div>
  );
}
