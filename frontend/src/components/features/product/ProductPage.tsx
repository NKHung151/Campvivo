/* eslint-disable @next/next/no-img-element */
import Link from "next/link";
import type { ProductCard } from "@/types/shop";
import type { ProductFull } from "@/types/product";
import { getPdpKb, getPdpStatic } from "@/services/catalogService";
import { parseSpecs } from "@/lib/specs";
import { productHref } from "@/lib/format";
import { Breadcrumb } from "@/components/features/layout/Breadcrumb";
import { SmartLink } from "@/components/ui/SmartLink";
import { BrandStrip } from "@/components/features/catalog/LandingView";
import { ProductGallery } from "@/components/features/product/ProductGallery";
import { ProductInfo } from "@/components/features/product/ProductInfo";
import { PdpDyn } from "@/components/features/product/PdpDyn";
import { ProductTabs } from "@/components/features/product/ProductTabs";
import { ViewedStrip } from "@/components/features/product/ViewedStrip";

function pickByCategory<T>(rules: { keys: string[] }[], name: string | null, fallback: T, get: (i: number) => T): T {
  const n = (name ?? "").toLowerCase();
  if (n) for (let i = 0; i < rules.length; i++) if (rules[i].keys.some((k) => n.includes(k))) return get(i);
  return fallback;
}

const GENERIC_TIPS = [
  "🎯 Chọn đúng size giúp thoải mái và an toàn khi hoạt động",
  "🔍 Kiểm tra chất liệu và thông số kỹ thuật trước khi mua",
  "⚖️ Cân nhắc trọng lượng — nhẹ hơn giúp bền sức hơn khi dùng lâu",
];

export function ProductPage({ p }: { p: ProductFull }) {
  const kb = getPdpKb();
  const st = getPdpStatic();
  const journeys = pickByCategory(kb.journeyRules, p.categoryName, kb.genericJourneys, (i) => kb.journeyRules[i].journeys);
  const tips = pickByCategory(kb.educationTips, p.categoryName, GENERIC_TIPS, (i) => kb.educationTips[i].tips);
  const specs = parseSpecs(p.specsHtml).filter(([k, v]) => !/\d+(\.\d+)?\s*(V|W|A|mA|Hz|mAh|kWh|Wh)\b/i.test(`${k} ${v}`));
  const related: ProductCard[] = p.related.items
    .filter((r) => r.slug)
    .map((r) => ({
      slug: r.slug!,
      name: r.name,
      brand: r.brand,
      img: r.img,
      price: r.price,
      oldPrice: r.oldPrice,
      memberPrice: null,
      discount: r.discount,
      discountClass: r.discountClass,
      installment: false,
      rating: r.rating,
      sold: null,
      wepoint: null,
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
          {!p.discontinued && (
            <PdpDyn
              journeys={journeys}
              heroImg={p.catImg}
              tips={tips}
              specs={specs}
              articles={p.articles}
              social={p.social}
              fbt={p.fbt}
              reviews={p.reviews}
            />
          )}
          <div className="flx">
            <div id="ProductDetailCol1">
              <div id="ProductDetailCol3">
                <ProductTabs
                  slug={p.slug}
                  descriptionHtml={p.descriptionHtml}
                  specsHtml={p.specsHtml}
                  guideHtml={p.guideHtml}
                  rating={p.rating}
                  reviews={p.reviews}
                />
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
                              <div className="stars" style={{ width: `${it.rating}%` }} />
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
        {p.discontinued && (
          <p style={{ margin: "10px 0 30px" }}>
            Sản phẩm này đã ngừng kinh doanh. <Link href={p.breadcrumbs.at(-1)?.href ?? "/products"}>Xem các sản phẩm cùng danh mục »</Link>
          </p>
        )}
      </div>
    </>
  );
}
