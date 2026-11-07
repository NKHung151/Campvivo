"use client";

import { Suspense } from "react";
import { STAFF } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { OrderListView } from "@/components/admin/order/OrderListView";

/** /admin/orders — danh sách đơn hàng (FR-13), Employee + Admin. */
export default function AdminOrdersPage() {
  return (
    <RequireRole roles={STAFF}>
      {/* useSearchParams cần Suspense */}
      <Suspense>
        <OrderListView />
      </Suspense>
    </RequireRole>
  );
}
