import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { ListingView } from "@/components/features/catalog/ListingView";
import { getComboListing } from "@/services/catalogService";

type Props = { searchParams: { category?: string } };

export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
  const page = await getComboListing(searchParams.category);
  return { title: `${page?.title ?? "Combo"} | Campvivo` };
}

export default async function CombosPage({ searchParams }: Props) {
  const page = await getComboListing(searchParams.category);
  if (!page) notFound();
  return (
    <Shell page="shop">
      <ListingView
        title={page.title}
        breadcrumbs={page.breadcrumbs}
        intro="Bộ sản phẩm ghép sẵn cho từng chuyến đi — rẻ hơn mua lẻ từng món."
        sideCats={page.sideCats}
        subCats={[]}
        filters={[]}
        guide={null}
        products={page.products}
        bottomHtml={null}
        commentsKey="combos"
        emptyText="Chưa có combo nào đang bán."
        enableCompare={false}
      />
    </Shell>
  );
}
