"use client";

import { Suspense } from "react";
import { STAFF } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ReturnListView } from "@/components/admin/returns/ReturnListView";

/** /admin/returns — yêu cầu trả hàng (FR-19b), Employee + Admin. */
export default function AdminReturnsPage() {
  return (
    <RequireRole roles={STAFF}>
      {/* useSearchParams cần Suspense */}
      <Suspense>
        <ReturnListView />
      </Suspense>
    </RequireRole>
  );
}
