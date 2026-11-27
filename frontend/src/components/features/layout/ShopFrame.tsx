import { ShopProvider } from "@/components/features/shop/ShopProvider";
import { FloatingChrome } from "@/components/features/layout/FloatingChrome";
import { StoreNotifications } from "@/components/features/notification/StoreNotifications";

/** Client store + global overlays, shared by every storefront layout. Stylesheets load in app/(store)/layout.tsx. */
export function ShopFrame({ children }: { children: React.ReactNode }) {
  return (
    <ShopProvider>
      <StoreNotifications>
        {children}
        <FloatingChrome />
      </StoreNotifications>
    </ShopProvider>
  );
}
