/* eslint-disable @next/next/no-img-element */
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { getPromo, routeSlug } from "@/services/catalogService";
import { productHref, vnd } from "@/lib/format";

type Props = { params: { slug: string } };

const promoOf = (raw: string) => getPromo(routeSlug(raw));

/** "23:59 30/11/2026" theo giờ Việt Nam. */
const endsAt = (iso: string) =>
  new Date(iso).toLocaleString("vi-VN", {
    timeZone: "Asia/Ho_Chi_Minh",
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const p = await promoOf(params.slug);
  return p ? { title: `${p.title} | Campvivo` } : {};
}

export default async function PromotionPage({ params }: Props) {
  const p = await promoOf(params.slug);
  if (!p) notFound();
  return (
    <Shell page="shop">
      <div className="pageContent cvPromotion">
        {p.bannerUrl && <img className="cvPromotionBanner" src={p.bannerUrl} alt={p.title} />}
        <h1 className="cvPromotionHead">{p.title}</h1>
        <div className="cvPromotionEnds">Kết thúc lúc {endsAt(p.endDate)}</div>
        {/* Mô tả đã được backend làm sạch (HtmlSanitizer) */}
        <div className="cvPromotionDesc" dangerouslySetInnerHTML={{ __html: p.desc }} />
        {p.items.length === 0 && <p>Chương trình chưa có sản phẩm nào đang bán.</p>}
        <div className="groupItems">
          {p.items.map((c) => (
            <Link className="item" title={c.name} href={productHref(c.slug)} key={c.slug} prefetch={false}>
              <div className="wImage">
                <span className="khungAnhCrop0">
                  <img loading="lazy" alt={c.name} width={350} src={c.img} />
                </span>
                {c.discount && (
                  <span className={`saleoff ${c.discountClass ?? "mauPercenDo"}`}>
                    <b>{c.discount.replace(/\s*off$/i, "")}</b> off
                  </span>
                )}
              </div>
              <div className="brand">{c.brand}</div>
              <h3 className="title">{c.name}</h3>
              <div className="core">
                <div className="stars" style={{ width: `${c.rating ?? 100}%` }} />
              </div>
              <div className="price">
                {vnd(c.price)} {c.oldPrice ? <span>{vnd(c.oldPrice)}</span> : null}
              </div>
            </Link>
          ))}
        </div>
        <div className="cb h20" />
      </div>
    </Shell>
  );
}
