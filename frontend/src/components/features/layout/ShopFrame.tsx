import { ShopProvider } from "@/components/features/shop/ShopProvider";
import { FloatingChrome } from "@/components/features/layout/FloatingChrome";

/** Client store + global overlays, shared by every storefront layout. Stylesheets load in app/(store)/layout.tsx. */
export function ShopFrame({ children }: { children: React.ReactNode }) {
  return (
    <ShopProvider>
      {children}
      <FloatingChrome />
    </ShopProvider>
  );
}
