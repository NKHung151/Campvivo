import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { ProductPage } from "@/components/features/product/ProductPage";
import { getProduct, routeSlug } from "@/services/catalogService";

type Props = { params: { slug: string } };

export function generateMetadata({ params }: Props): Metadata {
  const p = getProduct(routeSlug(params.slug));
  return p ? { title: `${p.name} | Campvivo` } : {};
}

export default function ProductDetailPage({ params }: Props) {
  const p = getProduct(routeSlug(params.slug));
  if (!p) notFound();
  return (
    <Shell page="shop">
      <ProductPage p={p} />
    </Shell>
  );
}
