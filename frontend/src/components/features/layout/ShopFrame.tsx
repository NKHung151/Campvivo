import { ShopProvider } from "@/components/features/shop/ShopProvider";
import { LoginPopup } from "@/components/features/account/LoginPopup";
import { FloatingChrome } from "@/components/features/layout/FloatingChrome";
import "@/styles/shop/base.css";
import "@/styles/shop/home.css";
import "@/styles/shop/shop.css";
import "@/styles/shop/brand.css";
import "@/styles/shop/news.css";
import "@/styles/shop/info.css";
import "@/styles/shop/guides.css";
import "@/styles/shop/search.css";
import "@/styles/shop/journal.css";
import "@/styles/shop/custom.css";

/** Client store + global overlays + shop stylesheets, shared by every storefront layout. */
export function ShopFrame({ children }: { children: React.ReactNode }) {
  return (
    <ShopProvider>
      {children}
      <LoginPopup />
      <FloatingChrome />
    </ShopProvider>
  );
}
