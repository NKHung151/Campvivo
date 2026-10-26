import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { MyOrderDetail } from "@/components/features/account/MyOrderDetail";

export const metadata: Metadata = { title: "Chi tiết đơn hàng | Campvivo", robots: { index: false } };

export default function MyOrderDetailRoute({ params }: { params: { id: string } }) {
  return (
    <Shell page="shop">
      <MyOrderDetail id={Number(params.id)} />
    </Shell>
  );
}
