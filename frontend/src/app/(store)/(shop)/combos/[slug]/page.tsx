import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Shell } from "@/components/features/layout/Shell";
import { ComboPage } from "@/components/features/combo/ComboPage";
import { getComboDetail } from "@/services/catalogApi";

type Props = { params: { slug: string } };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const c = await getComboDetail(params.slug);
  return c ? { title: `${c.name} | Campvivo` } : {};
}

export default async function ComboDetailPage({ params }: Props) {
  const c = await getComboDetail(params.slug);
  if (!c) notFound();
  return (
    <Shell page="shop">
      <ComboPage c={c} />
    </Shell>
  );
}
