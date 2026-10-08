import { Suspense } from "react";
import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { OrderSuccess } from "@/components/features/cart/OrderSuccess";

export const metadata: Metadata = { title: "Đặt hàng thành công | Campvivo" };

export default function OrderSuccessPage() {
  return (
    <Shell page="shop">
      <Suspense>
        <OrderSuccess />
      </Suspense>
    </Shell>
  );
}
