import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { ArticleList } from "@/components/features/blog/News";
import { getJournal, routeSlug } from "@/services/catalogService";
import { journalHref } from "@/lib/format";

type Props = { params: { slug: string } };

export function generateMetadata({ params }: Props): Metadata {
  const c = getJournal().categories[routeSlug(params.slug)];
  return c ? { title: `${c.name} | Outdoor Journal | Campvivo` } : {};
}

export default function JournalCategoryPage({ params }: Props) {
  const j = getJournal();
  const slug = routeSlug(params.slug);
  const c = j.categories[slug];
  if (!c) notFound();
  const href = `/journal/category/${slug}`;
  return (
    <Shell page="journal">
      <ArticleList
        title={c.name}
        articles={c.items}
        page={1}
        filters={j.filters}
        basePath={href}
        perPage={1000}
        breadcrumbs={[...j.breadcrumbs, { name: c.name, href }]}
        hrefFor={journalHref}
      />
    </Shell>
  );
}
