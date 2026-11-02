import type { Metadata } from "next";
import { ForgotPasswordForm } from "@/components/features/auth/ForgotPasswordForm";

export const metadata: Metadata = {
  title: "Quên mật khẩu | Campvivo",
  robots: { index: false, follow: false },
};

/** /forgot-password?redirect=<đường dẫn nội bộ> — docs/03-san-pham/pages.md (nhóm Auth). */
export default function ForgotPasswordPage({ searchParams }: { searchParams: { redirect?: string | string[] } }) {
  const raw = Array.isArray(searchParams.redirect) ? searchParams.redirect[0] : searchParams.redirect;
  return <ForgotPasswordForm redirect={raw ?? null} />;
}
