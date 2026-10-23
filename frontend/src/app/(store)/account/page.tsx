import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { AccountPage } from "@/components/features/account/AccountPage";

export const metadata: Metadata = { title: "Tài khoản | Campvivo" };

export default function AccountRoute() {
  return (
    <Shell page="shop">
      <AccountPage />
    </Shell>
  );
}
