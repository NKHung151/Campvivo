/* eslint-disable @next/next/no-img-element */
import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { SmartLink } from "@/components/ui/SmartLink";
import { BrandHeader } from "@/components/features/catalog/BrandHeader";
import { BrandStrip } from "@/components/features/catalog/LandingView";
import { CategoryComments } from "@/components/features/catalog/CategoryComments";
import { cardsFor, getBrandIndex } from "@/services/catalogService";

export const metadata: Metadata = { title: "Thương hiệu | Campvivo" };

export default function BrandIndexPage() {
  const idx = getBrandIndex();
  return (
    <Shell page="brand">
      <BrandHeader img="https://wetrek.vn/pic/banner/10bc13c9-c9d1-4266-a6d9-874e48b52625.jpg" video="2zS8G3OORUQ" cls="h550px" />
      <div className="pageContent">
        <div id="BrandIndex">
          <div id="SubBrandCate">
            <div
              id="headerSubPageCate"
              style={{ background: "url(/assets/css/icon/brand.jpg) no-repeat left", paddingLeft: 110, backgroundSize: "contain" }}
            >
              <h1 className="inherit">{idx.title}</h1>
              <div>{idx.desc}</div>
            </div>
            <div className="SubListCatePage">
              {idx.items.map((b) => (
                <SmartLink className="khungAnhCrop" href={b.href} key={b.href}>
                  <img loading="lazy" alt={b.name} className="lazy" src={b.img} />
                  <h2 className="brandName">{b.name}</h2>
                </SmartLink>
              ))}
            </div>
          </div>
          <div id="SubBrandCateIndex">
            {idx.carousels.map((c) => (
              <BrandStrip key={c.title} title={c.title} href={c.href} items={cardsFor(c.items)} />
            ))}
          </div>
        </div>
        <CategoryComments storageKey="brand:__index__" />
      </div>
    </Shell>
  );
}
