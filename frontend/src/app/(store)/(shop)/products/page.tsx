import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { LandingView } from "@/components/features/catalog/LandingView";
import { getAllProductsLanding } from "@/services/catalogService";

export const metadata: Metadata = { title: "Sản phẩm | Campvivo" };

export default async function ProductsPage() {
  const c = await getAllProductsLanding();
  if (!c) return null;
  return (
    <Shell page="shop">
      <LandingView tiles={c.tiles} carousels={c.carousels} commentsKey="cat:__all__" />
    </Shell>
  );
}
