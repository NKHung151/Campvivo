"use client";
/* eslint-disable @next/next/no-img-element -- catalogue imagery is served as-is */

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import type { HomeData, ProductCard } from "@/types/shop";
import { productHref } from "@/lib/format";
import { OwlCarousel } from "@/components/ui/OwlCarousel";
import { FlashCard, HomeCard } from "@/components/features/catalog/ProductCard";
import { SmartLink } from "@/components/ui/SmartLink";
import { Lightbox } from "@/components/ui/Lightbox";

export function Hero({ hero }: { hero: HomeData["hero"] }) {
  return (
    <div id="CommonHomepgeBackgroundSlide" className="SlideVideo">
      <div>
        <div className="BackgroundSlideHomepage hotspot-wrapper">
          <SmartLink href={hero.href}>
            <img alt={hero.alt} width={1920} src={hero.img} />
          </SmartLink>
          {hero.hotspots.map((h, i) => (
            <a key={i} className="hotspot" style={{ left: h.left, top: h.top }} href={h.href} target="_blank" rel="noreferrer">
              <span className="hotspot-icon">
                <img src="/assets/css/icon/cal.svg" alt="icon" />
              </span>
            </a>
          ))}
        </div>
      </div>
      <div id="BannerAboveSlide" />
    </div>
  );
}

export function BrandStrip({ logos }: { logos: HomeData["brandLogos"] }) {
  return (
    <div id="CommonMenuThuongHieu">
      <div className="contentMenuThuongHieu">
        <OwlCarousel className="slidePartner" autoWidth margin={20} loop autoplay={2500} dots={false}>
          {logos.map((l) => (
            <div className="frameOut" key={l.href}>
              <SmartLink href={l.href} title={l.name} className="frameIn">
                <img alt={l.name} src={l.img} style={{ opacity: 1 }} />
              </SmartLink>
            </div>
          ))}
        </OwlCarousel>
      </div>
    </div>
  );
}

function useMidnightCountdown() {
  const [left, setLeft] = useState<[string, string, string] | null>(null);
  useEffect(() => {
    const tick = () => {
      const now = new Date();
      const end = new Date(now);
      end.setHours(24, 0, 0, 0);
      const s = Math.max(0, Math.floor((end.getTime() - now.getTime()) / 1000));
      const pad = (n: number) => String(n).padStart(2, "0");
      setLeft([pad(Math.floor(s / 3600)), pad(Math.floor((s % 3600) / 60)), pad(s % 60)]);
    };
    tick();
    const t = setInterval(tick, 1000);
    return () => clearInterval(t);
  }, []);
  return left;
}

export function FlashSale({ data }: { data: HomeData["flashSale"] }) {
  const left = useMidnightCountdown();
  const router = useRouter();
  const buy = (p: ProductCard) => router.push(productHref(p.slug));
  return (
    <div className="ChuongTrinhGiamGiaTrangChu">
      <h2 className="head">
        <SmartLink className="name" href={data.href}>
          {data.title}{" "}
        </SmartLink>
      </h2>
      <div className="promotion-time">
        <div className="promotion-time-remain">
          Chỉ còn: <span className="remain-hours">{left?.[0] ?? "--"}</span>:<span className="remain-minutes">{left?.[1] ?? "--"}</span>:
          <span className="remain-seconds">{left?.[2] ?? "--"}</span>
        </div>
      </div>
      <OwlCarousel className="list" items={5} margin={15} nav dots responsive={{ 0: 2, 768: 3, 1024: 5 }}>
        {data.items.map((p) => (
          <FlashCard key={p.slug} p={p} onBuy={buy} />
        ))}
      </OwlCarousel>
    </div>
  );
}

export function ProductGroups({ groups }: { groups: HomeData["groups"] }) {
  return (
    <div id="SubProductGroupHomepageInBannerNew">
      {groups.map((g) => (
        <div className="owl-style-home" key={g.title}>
          <div className="headcate">
            <SmartLink className="name" href={g.href}>
              <h2 className="inherit">{g.title}</h2>
            </SmartLink>
            <p className="descCate">{g.desc}</p>
            <SmartLink className="more" href={g.href}>
              Xem tất cả »
            </SmartLink>
          </div>
          <OwlCarousel items={5} margin={15} responsive={{ 0: 2, 768: 3, 1024: 5 }}>
            {g.items.map((p) => (
              <HomeCard key={p.slug} p={p} />
            ))}
          </OwlCarousel>
        </div>
      ))}
    </div>
  );
}

export function Campers({ data, skuMap }: { data: HomeData["campers"]; skuMap: Record<string, string> }) {
  // All 12 photos are in the mock data; "Xem thêm" just reveals the rest.
  const [moreHidden, setMoreHidden] = useState(false);
  const [open, setOpen] = useState<number | null>(null);
  const items = data.items;
  const current = open !== null ? data.items[open] : null;
  const slug = current ? skuMap[current.sku] : undefined;
  return (
    <div className="CampvivoMember">
      <div className="container">
        <h2 className="heading">{data.title}</h2>
        <div className="desc">{data.desc}</div>
        <div className="group_items">
          {items.map((it, i) => (
            <div className="item" key={it.img} onClick={() => setOpen(i)}>
              <img loading="lazy" alt={it.alt} width={400} src={it.img} />
              <div className="shopNow">
                <span>Mua ngay</span>
                <div className="tag" />
              </div>
            </div>
          ))}
        </div>
        {!moreHidden && (
          <div className="showMore">
            <button type="button" className="btShowMore" onClick={() => setMoreHidden(true)}>
              Xem thêm
            </button>
          </div>
        )}
      </div>
      {current && (
        <div id="CampvivoMember" className="modal fade show" tabIndex={-1} style={{ display: "block" }}>
          <div className="modal-light" onClick={() => setOpen(null)} />
          <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
              <div className="modal-header">
                <span className="close" onClick={() => setOpen(null)} />
              </div>
              <div className="modal-body modal-product-detail">
                <img src={current.img.replace(/-w\.400-q\.80/, "-w.400-q.80")} alt={current.alt} style={{ width: "100%" }} />
                <p style={{ margin: "12px 0 6px", fontWeight: 600 }}>{current.alt}</p>
                {slug ? (
                  <Link className="cv-btn" href={productHref(slug)}>
                    Mua ngay
                  </Link>
                ) : (
                  <Link className="cv-btn" href={`/search?q=${encodeURIComponent(current.sku)}`}>
                    Tìm sản phẩm
                  </Link>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/** SEO copy + store photos. The YouTube teaser opens in a lightbox. */
export function SeoBlock({ html, storesTitle, stores }: { html: string; storesTitle: string; stores: HomeData["stores"] }) {
  const [video, setVideo] = useState<string | null>(null);
  return (
    <div id="CommonFooter" className="container">
      <div
        dangerouslySetInnerHTML={{ __html: html }}
        style={{ display: "contents" }}
        onClick={(e) => {
          const a = (e.target as HTMLElement).closest("a[data-fancybox]") as HTMLAnchorElement | null;
          if (a && /youtube\.com\/watch/.test(a.href)) {
            e.preventDefault();
            setVideo(new URL(a.href).searchParams.get("v"));
          }
        }}
      />
      <h3>{storesTitle}</h3>
      <div className="store">
        {stores.map((s) => (
          <a key={s.title} title={s.title} href={s.href} target="_blank" rel="noreferrer">
            {" "}
            <img alt={s.title} loading="lazy" src={s.img} />{" "}
          </a>
        ))}
      </div>
      {video && (
        <Lightbox onClose={() => setVideo(null)}>
          <iframe src={`https://www.youtube.com/embed/${video}?autoplay=1`} allow="autoplay; encrypted-media" allowFullScreen title="Campvivo video" />
        </Lightbox>
      )}
    </div>
  );
}
