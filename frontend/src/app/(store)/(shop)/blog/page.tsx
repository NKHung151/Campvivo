import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { ArticleList } from "@/components/features/blog/News";
import { getArticleFilters, getArticleIndex } from "@/services/catalogService";

export const metadata: Metadata = { title: "Kinh nghiệm outdoor | Campvivo" };

export default function BlogPage() {
  return (
    <Shell page="news">
      <ArticleList title="Kinh nghiệm outdoor" articles={getArticleIndex()} page={1} filters={getArticleFilters()} basePath="/blog" />
    </Shell>
  );
}
