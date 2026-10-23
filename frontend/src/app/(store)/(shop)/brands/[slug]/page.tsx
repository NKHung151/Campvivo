import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { BrandHeader } from "@/components/features/catalog/BrandHeader";
import { ListingView } from "@/components/features/catalog/ListingView";
import { cardsFor, getBrands, routeSlug } from "@/services/catalogService";

type Props = { params: { slug: string } };

const brandOf = (raw: string) => getBrands()[routeSlug(raw)] ?? null;

export function generateMetadata({ params }: Props): Metadata {
  const b = brandOf(params.slug);
  return b ? { title: `${b.name} | Campvivo` } : {};
}

export default function BrandPage({ params }: Props) {
  const b = brandOf(params.slug);
  if (!b) notFound();
  return (
    <Shell page="brand">
      {b.header && <BrandHeader img={b.header.img} video={b.header.video} cls={b.header.cls} />}
      <ListingView
        title={b.name}
        breadcrumbs={b.breadcrumbs}
        showTitleGroup={false}
        intro={b.intro}
        tabs={b.tabs}
        sideCats={b.sideCats}
        subCats={b.subCats}
        filters={b.filters}
        guide={b.guide}
        products={cardsFor(b.products)}
        bottomHtml={b.bottomHtml}
        commentsKey={`brand:${b.slug}`}
        enableCompare={false}
        gridId="BrandCategory"
      />
    </Shell>
  );
}
