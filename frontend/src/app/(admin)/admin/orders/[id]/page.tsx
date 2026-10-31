"use client";

import { STAFF } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { OrderDetailView } from "@/components/admin/order/OrderDetailView";

/** /admin/orders/[id] — chi tiết và xử lý đơn (FR-13), Employee + Admin. */
export default function AdminOrderDetailPage({ params }: { params: { id: string } }) {
  return (
    <RequireRole roles={STAFF}>
      <OrderDetailView id={Number(params.id)} />
    </RequireRole>
  );
}
