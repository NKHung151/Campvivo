import { getHome, getMenu } from "@/services/catalogService";
import { serverGet } from "@/services/serverApi";
import { campaignPromoSlide, interleave, voucherPromoSlide } from "@/lib/promotion";
import { Footer } from "@/components/features/layout/Footer";
import { Header } from "@/components/features/layout/Header";
import { PromoBar } from "@/components/features/layout/PromoBar";
import type { CampaignSummary, PublicVoucher } from "@/types/api/promotion";

/**
 * PromoBar: xen kẽ chiến dịch đang chạy (FR-17) và mã công khai (D-29); cả hai rỗng/lỗi → slide mặc định.
 * Cache 60s để không gọi API mỗi lần render; một nguồn lỗi không làm mất nguồn còn lại.
 */
async function promoSlides(fallback: { href: string; html: string }[]) {
  const [campaigns, vouchers] = await Promise.all([
    serverGet<CampaignSummary[]>("/campaigns", { revalidate: 60 }).catch(() => []),
    serverGet<PublicVoucher[]>("/vouchers/public", { revalidate: 60 }).catch(() => []),
  ]);
  const slides = interleave(campaigns.map(campaignPromoSlide), vouchers.map(voucherPromoSlide));
  return slides.length ? slides : fallback;
}

/**
 * Page frame shared by every shop route. `page` selects which
 * page stylesheet applies (each is scoped to `.pg-<page>`).
 */
export async function Shell({
  page,
  query,
  children,
}: {
  page: "home" | "shop" | "info" | "news" | "brand" | "guides" | "search" | "journal";
  query?: string;
  children: React.ReactNode;
}) {
  const home = getHome();
  return (
    <div className={`pg-${page}`}>
      <Header menu={await getMenu()} initialQuery={query} />
      <PromoBar slides={await promoSlides(home.promoBar)} />
      {children}
      <Footer />
    </div>
  );
}
