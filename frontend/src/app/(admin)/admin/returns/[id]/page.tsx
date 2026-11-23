"use client";

import { STAFF } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ReturnDetailView } from "@/components/admin/returns/ReturnDetailView";

/** /admin/returns/[id] — duyệt / từ chối yêu cầu trả hàng (FR-19b), Employee + Admin. */
export default function AdminReturnDetailPage({ params }: { params: { id: string } }) {
  return (
    <RequireRole roles={STAFF}>
      <ReturnDetailView id={Number(params.id)} />
    </RequireRole>
  );
}
