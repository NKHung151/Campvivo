import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { ReturnRequestForm } from "@/components/features/account/ReturnRequestForm";

export const metadata: Metadata = { title: "Yêu cầu trả hàng | Campvivo", robots: { index: false } };

export default function ReturnRequestRoute({ params }: { params: { id: string } }) {
  return (
    <Shell page="shop">
      <ReturnRequestForm id={Number(params.id)} />
    </Shell>
  );
}
