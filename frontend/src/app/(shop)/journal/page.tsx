import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { ArticleList } from "@/components/features/blog/News";
import { getJournal } from "@/services/catalogService";
import { journalHref } from "@/lib/format";

export const metadata: Metadata = { title: "Outdoor Journal - Hành trình, điểm đến & kinh nghiệm outdoor | Campvivo" };

export default function JournalPage() {
  const j = getJournal();
  return (
    <Shell page="journal">
      <ArticleList title={j.title} articles={j.items} page={1} filters={j.filters} basePath="/journal" breadcrumbs={j.breadcrumbs} hrefFor={journalHref} />
    </Shell>
  );
}
