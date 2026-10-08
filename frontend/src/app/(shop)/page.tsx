/* eslint-disable @next/next/no-img-element */
import { Shell } from "@/components/features/layout/Shell";
import { SmartLink } from "@/components/ui/SmartLink";
import { BrandStrip, FlashSale, Hero, ProductGroups, SeoBlock, Campers } from "@/components/features/home/HomeSections";
import { getHome, getSkuMap } from "@/services/catalogService";

export default function HomePage() {
  const home = getHome();
  return (
    <Shell page="home">
      <Hero hero={home.hero} />
      <h1 id="homepage-slogan">
        {home.slogan.text} <em>{home.slogan.em}</em>
      </h1>
      <div className="pageContent">
        <div id="page">
          <BrandStrip logos={home.brandLogos} />
          <FlashSale data={home.flashSale} />
          <ProductGroups groups={home.groups} />
          <div className="TopCategory">
            {" "}
            <h2 className="heading">{home.topCategories.title}</h2>
            <div className="group_items">
              {home.topCategories.items.map((c) => (
                <SmartLink href={c.href} className="item" key={c.href}>
                  <img loading="lazy" alt={`${c.name} - Campvivo`} width={250} src={c.img} />
                  <h3 className="proName">{c.name}</h3>
                </SmartLink>
              ))}
            </div>
            <div className="showMore">
              {home.topCategories.more.text}{" "}
              <SmartLink href={home.topCategories.more.href} className="btShowMore">
                {home.topCategories.more.btn || "Xem thêm"}
              </SmartLink>
            </div>
          </div>
          <div id="AboveBannerSlide">
            {home.banners.map((b) => (
              <div className={`itemAdv${b.col !== "1" ? ` col-${b.col}` : " "}`} key={b.img}>
                <SmartLink className="imgAdv" href={b.href} title={b.title}>
                  <img loading="lazy" alt={b.title} width={b.col === "1" ? 1200 : b.col === "2" ? 600 : 400} src={b.img} />
                </SmartLink>
              </div>
            ))}
          </div>
          <Campers data={home.campers} skuMap={getSkuMap()} />
          <div className="PopularCategory">
            <div className="PopularLink">
              {home.popular.navs.map((n) => (
                <nav key={n.label}>
                  <label>{n.label}</label>
                  {n.links.map((l) => (
                    <SmartLink key={l.href + l.name} title={l.name} href={l.href}>
                      {l.name}
                    </SmartLink>
                  ))}
                </nav>
              ))}
            </div>
            <SmartLink className="PopularBanner" href={home.popular.banner.href}>
              <img loading="lazy" alt="Sống Cá Tính" src={home.popular.banner.img} />
            </SmartLink>
          </div>
        </div>
      </div>
      <SeoBlock html={home.seoHtml} storesTitle={home.storesTitle} stores={home.stores} />
    </Shell>
  );
}
