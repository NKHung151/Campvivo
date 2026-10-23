import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { InfoPageView } from "@/components/features/content/InfoPage";
import { getInfoPages, routeSlug } from "@/services/catalogService";

type Props = { params: { slug: string } };

const pageOf = (raw: string) => getInfoPages().pages[routeSlug(raw)] ?? null;

export function generateMetadata({ params }: Props): Metadata {
  const p = pageOf(params.slug);
  return p ? { title: `${p.title} | Campvivo` } : {};
}

export default function InfoPageRoute({ params }: Props) {
  const p = pageOf(params.slug);
  if (!p) notFound();
  return (
    <Shell page="info">
      <InfoPageView page={p} nav={getInfoPages().nav} />
    </Shell>
  );
}
