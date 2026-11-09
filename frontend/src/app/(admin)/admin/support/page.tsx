"use client";

import { Suspense } from "react";
import { STAFF } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { SupportInbox } from "@/components/admin/support/SupportInbox";

/** /admin/support — hộp thư chat hỗ trợ khách hàng (FR-26), EMPLOYEE và ADMIN. */
export default function AdminSupportPage() {
  return (
    <RequireRole roles={STAFF}>
      {/* useSearchParams cần Suspense */}
      <Suspense>
        <SupportInbox />
      </Suspense>
    </RequireRole>
  );
}
