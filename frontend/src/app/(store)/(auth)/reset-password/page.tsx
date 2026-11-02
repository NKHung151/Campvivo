import type { Metadata } from "next";
import { ResetPasswordForm } from "@/components/features/auth/ResetPasswordForm";

export const metadata: Metadata = {
  title: "Đặt lại mật khẩu | Campvivo",
  robots: { index: false, follow: false },
};

type Param = string | string[] | undefined;
const first = (value: Param) => (Array.isArray(value) ? value[0] : value);

/** /reset-password?email=<email>&redirect=<đường dẫn nội bộ> — docs/03-san-pham/pages.md (nhóm Auth). */
export default function ResetPasswordPage({ searchParams }: { searchParams: { email?: Param; redirect?: Param } }) {
  return <ResetPasswordForm email={first(searchParams.email) ?? null} redirect={first(searchParams.redirect) ?? null} />;
}
