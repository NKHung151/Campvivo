"use client";

import { OwlCarousel } from "@/components/ui/OwlCarousel";
import { SmartLink } from "@/components/ui/SmartLink";

export function PromoBar({ slides }: { slides: { href: string; html: string }[] }) {
  return (
    <div id="BannerPromotionHeader">
      <OwlCarousel className="BannerPromotionHeader" items={1} loop nav dots={false} autoplay={4000} navText={["‹", "›"]}>
        {slides.map((s, i) => (
          <SmartLink key={i} href={s.href}>
            <span dangerouslySetInnerHTML={{ __html: s.html }} />
          </SmartLink>
        ))}
      </OwlCarousel>
    </div>
  );
}
