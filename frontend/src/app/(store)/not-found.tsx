import { ShopFrame } from "@/components/features/layout/ShopFrame";
import { NotFoundView } from "@/components/features/layout/NotFoundView";

// Unmatched URLs render outside the (shop) layout, so wrap the storefront frame here too.
export default function NotFound() {
  return (
    <ShopFrame>
      <NotFoundView />
    </ShopFrame>
  );
}
