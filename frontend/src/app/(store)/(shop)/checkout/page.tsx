import { redirect } from "next/navigation";

// Checkout lives on the cart page (see /cart); keep /checkout from pages.md working.
export default function CheckoutPage() {
  redirect("/cart");
}
