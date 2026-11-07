"use client";

import { Suspense } from "react";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { VoucherListView } from "@/components/admin/promotion/VoucherListView";

/** /admin/vouchers — quản lý voucher (FR-16). */
export default function AdminVouchersPage() {
  return (
    <RequireRole roles={ADMIN_ONLY}>
      {/* useSearchParams cần Suspense */}
      <Suspense>
        <VoucherListView />
      </Suspense>
    </RequireRole>
  );
}
