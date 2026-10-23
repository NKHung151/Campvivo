import { notFound } from "next/navigation";
import { findNavItem } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { UnderConstruction } from "@/components/admin/common/UnderConstruction";

export default function AdminPlaceholderPage({ params }: { params: { slug: string[] } }) {
  const item = findNavItem(`/admin/${params.slug.join("/")}`);
  if (!item) notFound();
  return (
    <RequireRole roles={item.roles}>
      <UnderConstruction title={item.label} />
    </RequireRole>
  );
}
