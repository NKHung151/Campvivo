import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { ArticleDetail } from "@/components/features/blog/News";
import { getArticle, routeSlug } from "@/services/catalogService";

type Props = { params: { slug: string } };

export function generateMetadata({ params }: Props): Metadata {
  const a = getArticle(routeSlug(params.slug));
  return a ? { title: a.title } : {};
}

export default function ArticlePage({ params }: Props) {
  const a = getArticle(routeSlug(params.slug));
  if (!a) notFound();
  return (
    <Shell page="news">
      <ArticleDetail a={a} />
    </Shell>
  );
}
