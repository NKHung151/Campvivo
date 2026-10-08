import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { SearchView } from "@/components/features/catalog/SearchView";
import { searchArticles, searchProducts } from "@/services/catalogService";

type Props = { searchParams: { q?: string } };

export function generateMetadata({ searchParams }: Props): Metadata {
  return { title: searchParams.q ? `Tìm kiếm: ${searchParams.q} | Campvivo` : "Tìm kiếm | Campvivo" };
}

export default function SearchPage({ searchParams }: Props) {
  const q = (searchParams.q ?? "").trim();
  return (
    <Shell page="search" query={q}>
      <SearchView query={q} products={q ? searchProducts(q) : []} articles={q ? searchArticles(q) : []} />
    </Shell>
  );
}
