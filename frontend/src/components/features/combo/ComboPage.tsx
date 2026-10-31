/* eslint-disable @next/next/no-img-element -- catalogue imagery is served as-is */
import Link from "next/link";
import type { ComboDetail } from "@/types/api/catalog";
import { productHref, vnd } from "@/lib/format";
import { Breadcrumb } from "@/components/features/layout/Breadcrumb";
import { ProductGallery } from "@/components/features/product/ProductGallery";
import { ComboBuyBox } from "@/components/features/combo/ComboBuyBox";

/** Trang chi tiết combo (FR-07, FR-09): ảnh, giá so với mua lẻ, tình trạng, mua/thêm giỏ, các món trong combo, mô tả. */
export function ComboPage({ c }: { c: ComboDetail }) {
  const saving = c.itemsTotal - c.price;
  const images = (c.images.length ? c.images : c.primaryImageUrl ? [{ url: c.primaryImageUrl, isPrimary: true }] : [])
    .slice()
    .sort((a, b) => Number(b.isPrimary) - Number(a.isPrimary))
    .map((i) => ({ big: i.url, thumb: i.url, alt: c.name }));
  const pieces = c.items.reduce((n, i) => n + i.quantity, 0);

  return (
    <div className="pageContent">
      <Breadcrumb
        items={[
          { name: "Combo", href: "/combos" },
          { name: c.category.name, href: `/combos?category=${c.category.slug}` },
        ]}
      />
      <div id="ProductDetail">
        <div className="in4onTop">
          <ProductGallery images={images} name={c.name} />
          <div className="in4mation">
            <div className="khoi1">
              <div className="brand">Combo {c.items.length} món</div>
              <h1 className="tensp">{c.name}</h1>
              <div className="pro-price">
                <div className="price">
                  Giá combo: <b>{vnd(c.price)}</b>
                </div>
                {saving > 0 && (
                  <div className="cv-combo-saving">
                    Mua lẻ <del>{vnd(c.itemsTotal)}</del> — tiết kiệm <b>{vnd(saving)}</b> (
                    {Math.round((saving / c.itemsTotal) * 100)}%)
                  </div>
                )}
              </div>
              <div className={`cv-combo-stock${c.availableQuantity > 0 ? "" : " out"}`}>
                {c.availableQuantity > 0 ? `Còn ${c.availableQuantity} bộ` : "Tạm hết hàng"}
              </div>
            </div>
            <ComboBuyBox comboId={c.id} available={c.availableQuantity} />
            <div className="cv-combo-items">
              <div className="cv-combo-items-title">
                Combo gồm {pieces} sản phẩm
              </div>
              {c.items.map((i) => (
                <Link key={i.product.id} className="cv-combo-item" href={productHref(i.product.slug)} prefetch={false}>
                  <span className="cv-combo-thumb">
                    {i.product.primaryImageUrl && <img loading="lazy" alt={i.product.name} width={72} src={i.product.primaryImageUrl} />}
                  </span>
                  <span className="cv-combo-info">
                    <span className="cv-combo-name">{i.product.name}</span>
                    <span className="cv-combo-price">{vnd(i.product.price)} / cái</span>
                  </span>
                  <span className="cv-combo-qty">×{i.quantity}</span>
                </Link>
              ))}
            </div>
          </div>
        </div>
        {c.description && (
          <div className="cv-combo-desc">
            <div className="cv-combo-items-title">Mô tả combo</div>
            <div className="contentview" dangerouslySetInnerHTML={{ __html: c.description }} />
          </div>
        )}
      </div>
    </div>
  );
}
