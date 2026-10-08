import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { ArticleDetail } from "@/components/features/blog/News";
import { getJournalArticle, routeSlug } from "@/services/catalogService";

type Props = { params: { slug: string } };

export function generateMetadata({ params }: Props): Metadata {
  const a = getJournalArticle(routeSlug(params.slug));
  return a ? { title: `${a.title} | Campvivo` } : {};
}

export default function JournalArticlePage({ params }: Props) {
  const a = getJournalArticle(routeSlug(params.slug));
  if (!a) notFound();
  return (
    <Shell page="journal">
      <ArticleDetail a={a} wrapperClass="ServiceDetail" />
    </Shell>
  );
}
