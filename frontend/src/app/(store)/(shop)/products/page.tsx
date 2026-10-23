import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { LandingView } from "@/components/features/catalog/LandingView";
import { cardsFor, getCategories } from "@/services/catalogService";

export const metadata: Metadata = { title: "Sản phẩm | Campvivo" };

export default function ProductsPage() {
  const c = getCategories()["__all__"];
  if (!c || c.kind !== "landing") return null;
  return (
    <Shell page="shop">
      <LandingView tiles={c.tiles} carousels={c.carousels.map((k) => ({ ...k, items: cardsFor(k.items) }))} commentsKey="cat:__all__" />
    </Shell>
  );
}
