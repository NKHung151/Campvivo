import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { AccountPage } from "@/components/features/account/AccountPage";

export const metadata: Metadata = { title: "Hỗ trợ | Campvivo" };

/** /account/support — mở thẳng tab chat hỗ trợ trong khu tài khoản (FR-26). */
export default function AccountSupportRoute() {
  return (
    <Shell page="shop">
      <AccountPage initialTab="support" />
    </Shell>
  );
}
