import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { VnpayReturn } from "@/components/features/cart/VnpayReturn";

export const metadata: Metadata = { title: "Kết quả thanh toán | Campvivo", robots: { index: false } };

// Return URL của VNPay (VNP_RETURN_URL) — D-28
export default function VnpayReturnPage() {
  return (
    <Shell page="shop">
      <VnpayReturn />
    </Shell>
  );
}
