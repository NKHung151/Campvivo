/* eslint-disable @next/next/no-img-element */
import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { getPromos, routeSlug } from "@/services/catalogService";
import { productHref, vnd } from "@/lib/format";

type Props = { params: { slug: string } };

const promoOf = (raw: string) => getPromos()[routeSlug(raw)] ?? null;

export function generateMetadata({ params }: Props): Metadata {
  const p = promoOf(params.slug);
  return p ? { title: `${p.title} | Campvivo` } : {};
}

export default function PromotionPage({ params }: Props) {
  const p = promoOf(params.slug);
  if (!p) notFound();
  return (
    <Shell page="shop">
      <div className="pageContent cvPromotion">
        <h1 className="cvPromotionHead">{p.title}</h1>
        <div className="cvPromotionDesc" dangerouslySetInnerHTML={{ __html: p.desc }} />
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
