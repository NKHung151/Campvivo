import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { AccountPage } from "@/components/features/account/AccountPage";

export const metadata: Metadata = { title: "Thông báo | Campvivo" };

/** /account/notifications — mở thẳng tab thông báo trong khu tài khoản (FR-22). */
export default function AccountNotificationsRoute() {
  return (
    <Shell page="shop">
      <AccountPage initialTab="notifications" />
    </Shell>
  );
}
