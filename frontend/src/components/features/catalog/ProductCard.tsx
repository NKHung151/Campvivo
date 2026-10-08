/* eslint-disable @next/next/no-img-element -- product imagery is served as-is */
import Link from "next/link";
import type { ProductCard as Card } from "@/types/shop";
import { productHref, vnd } from "@/lib/format";

const INSTALLMENT_ICON = "/assets/pic/products/7c9ed3d6-e39f-46f7-93ef-e99b4bfdc50b.svg";

function Badges({ p }: { p: Card }) {
  return (
    <>
      {p.discount && (
        <span className={`saleoff ${p.discountClass ?? "mauPercenDo"}`}>
          <b>{p.discount.replace(/\s*off$/i, "")}</b> off
        </span>
      )}
      {p.installment && (
        <img
          loading="lazy"
          alt="ICON TRẢ GÓP"
          src={INSTALLMENT_ICON}
          className="iconpp"
          style={{ position: "absolute", zIndex: 1, bottom: 0, left: 0 }}
        />
      )}
    </>
  );
}

function Price({ p }: { p: Card }) {
  return (
    <div className="price">
      {vnd(p.price)} {p.oldPrice ? <span>{vnd(p.oldPrice)}</span> : null}
    </div>
  );
}

const points = (p: Card) => p.wepoint || (p.price ? `+ ${Math.floor(p.price / 1000).toLocaleString("de-DE")} WePoint` : "");

/** Card used in the home page carousels (`.owl-style-home a.item`). */
export function HomeCard({ p }: { p: Card }) {
  return (
    <Link className="item" title={p.name} href={productHref(p.slug)} prefetch={false}>
      {" "}
      <div className="wImage">
        {" "}
        <span className="khungAnhCrop0">
          <img loading="lazy" alt={p.name} width={350} src={p.img} />
        </span>{" "}
        <Badges p={p} />
      </div>{" "}
      <div className="brand">{p.brand}</div> <h3 className="title">{p.name}</h3>{" "}
      <div className="core">
        <div className="stars" style={{ width: `${p.rating ?? 100}%` }} />
      </div>
      {p.memberPrice ? <div className="loyalty-price">Giá Member {vnd(p.memberPrice)}</div> : null}
      <Price p={p} />
      <div className="points-purple">{points(p)}</div>
    </Link>
  );
}

/** Search results card (`.cvPromotion .groupItems .item`): like HomeCard, member price on its own line. */
export function SearchCard({ p }: { p: Card }) {
  return (
    <Link className="item" title={p.name} href={productHref(p.slug)} prefetch={false}>
      <div className="wImage">
        <span className="khungAnhCrop0">
          <img loading="lazy" alt={p.name} width={350} src={p.img} />
        </span>
        <Badges p={p} />
      </div>
      <div className="brand">{p.brand}</div>
      <h3 className="title">{p.name}</h3>
      <div className="core">
        <div className="stars" style={{ width: `${p.rating ?? 100}%` }} />
      </div>
      {p.memberPrice ? (
        <div className="loyalty-price">
          Giá Member <br /> {vnd(p.memberPrice)}
        </div>
      ) : null}
      <Price p={p} />
      <div className="points-purple">{points(p)}</div>
    </Link>
  );
}

/** Flash sale card (`.ChuongTrinhGiamGiaTrangChu .item`). */
export function FlashCard({ p, onBuy }: { p: Card; onBuy?: (p: Card) => void }) {
  return (
    <div className="item">
      <Link className="item-inner" title={p.name} href={productHref(p.slug)} prefetch={false}>
        <div className="wImage">
          {" "}
          <span className="khungAnhCrop0">
            <img alt={p.name} width={350} src={p.img} style={{ opacity: 1 }} />
          </span>{" "}
          <Badges p={p} />{" "}
        </div>{" "}
        {p.brand && <span className="brand-name">{p.brand}</span>} <h3 className="title">{p.name}</h3>
        <div className="item-social">{p.sold && <span className="social-sold">{p.sold}</span>}</div>
        {p.memberPrice ? <div className="loyalty-price">Giá Member {vnd(p.memberPrice)}</div> : null}
        <Price p={p} />
        {p.inv && (
          <div className={`inv-bar ${p.inv.cls}`} style={{ ["--bar" as string]: p.inv.bar ?? "100%" }}>
            <div className="inv-fill" />
            <span className="inv-label">{p.inv.label}</span>
          </div>
        )}
      </Link>
      <button className="btn-buy-now" type="button" onClick={() => onBuy?.(p)}>
        Mua ngay
      </button>
    </div>
  );
}

/** Category/brand grid card (`#ProductCategory .item`) with colour swatches + compare tag. */
export function GridCard({
  p,
  comparing,
  onCompare,
  onSwatch,
  activeImg,
}: {
  p: Card;
  comparing?: boolean;
  onCompare?: (p: Card) => void;
  onSwatch?: (img: string) => void;
  activeImg?: string;
}) {
  return (
    <div className="item" data-cmp-id={p.slug}>
      <Link className="wImage" title={p.name} href={productHref(p.slug)} prefetch={false}>
        <span className="khungAnhCrop0">
          <img loading="lazy" alt={p.name} width={350} src={activeImg ?? p.img} />
        </span>
        <Badges p={p} />
      </Link>
      {p.swatches && p.swatches.length > 0 && (
        <div className="color-swatches">
          {p.swatches.map((s) => (
            <button key={s.img} type="button" onMouseEnter={() => onSwatch?.(s.big ?? s.img)} onClick={() => onSwatch?.(s.big ?? s.img)}>
              <img loading="lazy" alt={s.name} width={28} src={s.img} />
            </button>
          ))}
        </div>
      )}
      <Link className="info" title={p.name} href={productHref(p.slug)} prefetch={false}>
        {p.brand && <div className="brand">{p.brand}</div>}
        <h3 className="title">{p.name}</h3>
        <div className="core">
          <div className="stars" style={{ width: `${p.rating ?? 100}%` }} />
        </div>
        {p.memberPrice ? <div className="loyalty-price">Giá Member {vnd(p.memberPrice)}</div> : null}
        <Price p={p} />
        <div className="points-purple">{points(p)}</div>
      </Link>
      {onCompare && (
        <button className={`cmp-tag${comparing ? " active" : ""}`} type="button" onClick={() => onCompare(p)}>
          {comparing ? (
            <svg className="cmp-ic" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M20 6 9 17l-5-5" />
            </svg>
          ) : (
            <svg className="cmp-ic" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="m3 16 4 4 4-4" />
              <path d="M7 20V4" />
              <path d="m21 8-4-4-4 4" />
              <path d="M17 4v16" />
            </svg>
          )}
          <span>{comparing ? "Đang so sánh" : "So sánh"}</span>
        </button>
      )}
    </div>
  );
}
