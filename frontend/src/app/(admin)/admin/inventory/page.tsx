"use client";

import { Suspense } from "react";
import { STAFF } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { InventoryView } from "@/components/admin/inventory/InventoryView";

/** /admin/inventory — tồn kho sản phẩm + combo, nhập/xuất/điều chỉnh, nhật ký (FR-08). ADMIN và EMPLOYEE. */
export default function AdminInventoryPage() {
  return (
    <RequireRole roles={STAFF}>
      {/* useSearchParams cần Suspense */}
      <Suspense>
        <InventoryView />
      </Suspense>
    </RequireRole>
  );
}
