import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { ListingView } from "@/components/features/catalog/ListingView";
import { LandingView } from "@/components/features/catalog/LandingView";
import { getCategoryPage, routeSlug } from "@/services/catalogService";

type Props = { params: { slug: string } };

const pageOf = (raw: string) => getCategoryPage(routeSlug(raw));

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const c = (await pageOf(params.slug))?.category;
  return c ? { title: `${c.name} | Campvivo` } : {};
}

export default async function CategoryPage({ params }: Props) {
  const page = await pageOf(params.slug);
  if (!page) notFound();
  const c = page.category;
  if (c.kind === "landing") {
    return (
      <Shell page="shop">
        <LandingView
          tiles={c.tiles}
          carousels={page.carousels}
          commentsKey={`cat:${c.slug}`}
        />
      </Shell>
    );
  }
  return (
    <Shell page="shop">
      {c.banner && (
        <div
          id="CommonProductIndexHeader"
          style={{ height: 450, background: `url(${c.banner}) no-repeat top center`, backgroundSize: "auto 100%" }}
        />
      )}
      <ListingView
        title={c.name}
        breadcrumbs={c.breadcrumbs}
        intro={c.intro}
        sideCats={c.sideCats}
        subCats={c.subCats}
        filters={c.filters}
        guide={c.guide}
        products={page.products}
        bottomHtml={c.bottomHtml}
        commentsKey={`cat:${c.slug}`}
      />
    </Shell>
  );
}
