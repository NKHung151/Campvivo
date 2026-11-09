/* eslint-disable @next/next/no-img-element */
import Link from "next/link";
import type { ProductCard } from "@/types/shop";
import type { ProductFull } from "@/types/product";
import { getPdpStatic } from "@/services/catalogService";
import { productHref } from "@/lib/format";
import { Breadcrumb } from "@/components/features/layout/Breadcrumb";
import { SmartLink } from "@/components/ui/SmartLink";
import { BrandStrip } from "@/components/features/catalog/LandingView";
import { ProductGallery } from "@/components/features/product/ProductGallery";
import { ProductInfo } from "@/components/features/product/ProductInfo";
import { ProductTabs } from "@/components/features/product/ProductTabs";
import { ViewedStrip } from "@/components/features/product/ViewedStrip";

export function ProductPage({ p }: { p: ProductFull }) {
  const st = getPdpStatic();
  const related: ProductCard[] = p.related.items
    .filter((r) => r.slug)
    .map((r) => ({
      slug: r.slug!,
      name: r.name,
      brand: r.brand,
      img: r.img,
      price: r.price,
      oldPrice: r.oldPrice,
      discount: r.discount,
      discountClass: r.discountClass,
      installment: false,
      rating: r.rating,
      sold: null,
    }));

  return (
    <>
      <div className="pageContent">
        <Breadcrumb items={p.breadcrumbs} />
        <div id="ProductDetail">
          <div className="in4onTop">
            <ProductGallery images={p.images} name={p.name} />
            <ProductInfo p={p} st={st} />
          </div>
          <div className="box-product-hotDeals" />
          <div className="flx">
            <div id="ProductDetailCol1">
              <div id="ProductDetailCol3">
                <ProductTabs productId={p.id} descriptionHtml={p.descriptionHtml} specsHtml={p.specsHtml} />
              </div>
            </div>
            {p.sidebar.items.length > 0 && (
              <div id="ProductDetailCol2">
                <div className="stickyTop0">
                  <div id="SubProductOtherItems" className="SubProductOtherItems">
                    <div className="TopBar">{p.sidebar.title}</div>
                    <div className="GroupItems">
                      {p.sidebar.items.map((it, i) => {
                        const href = it.slug ? productHref(it.slug) : it.href;
                        return (
                          <SmartLink key={href + i} className={`itemOther itemOther${i % 2}`} href={href} title={it.name}>
                            <div className="imageOther">
                              <span className="khungAnhCrop0">
                                <img loading="lazy" alt={it.name} width={150} src={it.img} />
                              </span>
                            </div>
                            <div className="brandOther">{it.brand}</div>
                            <div className="titleOther">{it.name}</div>
                            <div className="core">
                              <div className="stars" style={{ width: `${it.rating ?? 0}%` }} />
                            </div>
                            <div className="priceOther">{it.price}</div>
                          </SmartLink>
                        );
                      })}
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
      <div className="pageContent">
        <BrandStrip id="SubProductOtherItems" title="SẢN PHẨM CÙNG LOẠI" items={related} />
        <ViewedStrip current={p.slug} />
      </div>
    </>
  );
}
