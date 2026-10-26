import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { BrandHeader } from "@/components/features/catalog/BrandHeader";
import { ListingView } from "@/components/features/catalog/ListingView";
import { getBrandPage, routeSlug } from "@/services/catalogService";

type Props = { params: { slug: string } };

const pageOf = (raw: string) => getBrandPage(routeSlug(raw));

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const b = (await pageOf(params.slug))?.brand;
  return b ? { title: `${b.name} | Campvivo` } : {};
}

export default async function BrandPage({ params }: Props) {
  const page = await pageOf(params.slug);
  if (!page) notFound();
  const b = page.brand;
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
        products={page.products}
        bottomHtml={b.bottomHtml}
        commentsKey={`brand:${b.slug}`}
        enableCompare={false}
        gridId="BrandCategory"
      />
    </Shell>
  );
}
