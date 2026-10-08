import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { ArticleList } from "@/components/features/blog/News";
import { getArticleFilters, getArticleIndex } from "@/services/catalogService";

type Props = { params: { n: string } };

export const metadata: Metadata = { title: "Kinh nghiệm outdoor | Campvivo" };

export default function BlogPagedPage({ params }: Props) {
  const page = Number(params.n);
  if (!Number.isInteger(page) || page < 1) notFound();
  return (
    <Shell page="news">
      <ArticleList title="Kinh nghiệm outdoor" articles={getArticleIndex()} page={page} filters={getArticleFilters()} basePath="/blog" />
    </Shell>
  );
}
