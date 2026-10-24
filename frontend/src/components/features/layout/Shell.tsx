import { getHome, getMenu } from "@/services/catalogService";
import { Footer } from "@/components/features/layout/Footer";
import { Header } from "@/components/features/layout/Header";
import { PromoBar } from "@/components/features/layout/PromoBar";

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
      <PromoBar slides={home.promoBar} />
      {children}
      <Footer />
    </div>
  );
}
