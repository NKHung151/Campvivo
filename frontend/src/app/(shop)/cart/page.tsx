import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { CartPage } from "@/components/features/cart/CartPage";
import { getCartData } from "@/services/catalogService";

export const metadata: Metadata = { title: "Giỏ hàng | Campvivo" };

// Cart and checkout share one page (cart list + delivery/payment form).
export default function CartRoute() {
  const d = getCartData();
  return (
    <Shell page="shop">
      <CartPage csvcHtml={d.csvcHtml} pvcHtml={d.pvcHtml} provinces={d.provinces} />
    </Shell>
  );
}
