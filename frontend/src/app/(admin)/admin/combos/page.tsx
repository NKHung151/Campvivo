"use client";

import { Suspense } from "react";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ComboListView } from "@/components/admin/combo/ComboListView";

/** /admin/combos — danh sách combo (FR-15). */
export default function AdminCombosPage() {
  return (
    <RequireRole roles={ADMIN_ONLY}>
      {/* useSearchParams cần Suspense */}
      <Suspense>
        <ComboListView />
      </Suspense>
    </RequireRole>
  );
}
