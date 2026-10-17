import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { ListingView } from "@/components/features/catalog/ListingView";
import { LandingView } from "@/components/features/catalog/LandingView";
import { cardsFor, getCategories, routeSlug } from "@/services/catalogService";

type Props = { params: { slug: string } };

const categoryOf = (raw: string) => getCategories()[routeSlug(raw)] ?? null;

export function generateMetadata({ params }: Props): Metadata {
  const c = categoryOf(params.slug);
  return c ? { title: `${c.name} | Campvivo` } : {};
}

export default function CategoryPage({ params }: Props) {
  const c = categoryOf(params.slug);
  if (!c) notFound();
  if (c.kind === "landing") {
    return (
      <Shell page="shop">
        <LandingView
          tiles={c.tiles}
          carousels={c.carousels.map((k) => ({ ...k, items: cardsFor(k.items) }))}
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
        products={cardsFor(c.products)}
        bottomHtml={c.bottomHtml}
        commentsKey={`cat:${c.slug}`}
      />
    </Shell>
  );
}
