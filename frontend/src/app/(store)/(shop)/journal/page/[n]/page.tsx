import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { ArticleList, PER_PAGE } from "@/components/features/blog/News";
import { getJournal } from "@/services/catalogService";
import { journalHref } from "@/lib/format";

type Props = { params: { n: string } };

export const metadata: Metadata = { title: "Outdoor Journal | Campvivo" };

export default function JournalPagedPage({ params }: Props) {
  const j = getJournal();
  const page = Number(params.n);
  if (!Number.isInteger(page) || page < 1 || (page - 1) * PER_PAGE >= j.items.length) notFound();
  return (
    <Shell page="journal">
      <ArticleList title={j.title} articles={j.items} page={page} filters={j.filters} basePath="/journal" breadcrumbs={j.breadcrumbs} hrefFor={journalHref} />
    </Shell>
  );
}
