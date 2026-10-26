import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { ArticleList } from "@/components/features/blog/News";
import { articlesInCategory, getArticleFilters, routeSlug } from "@/services/catalogService";

type Props = { params: { slug: string } };

function categoryOf(raw: string) {
  const href = `/blog/category/${routeSlug(raw)}`;
  return (
    getArticleFilters()
      .flatMap((f) => f.options)
      .find((o) => o.href === href) ?? null
  );
}

export function generateMetadata({ params }: Props): Metadata {
  const c = categoryOf(params.slug);
  return c ? { title: `${c.name} | Campvivo` } : {};
}

export default function BlogCategoryPage({ params }: Props) {
  const c = categoryOf(params.slug);
  if (!c?.href) notFound();
  return (
    <Shell page="news">
      <ArticleList title={c.name} articles={articlesInCategory(c.href)} page={1} filters={getArticleFilters()} basePath={c.href} perPage={1000} />
    </Shell>
  );
}
