import type { Metadata } from "next";
import { LoginForm } from "@/components/features/auth/LoginForm";

export const metadata: Metadata = {
  title: "Đăng nhập | Campvivo",
  robots: { index: false, follow: false },
};

/** /login?redirect=<đường dẫn nội bộ> — docs/03-san-pham/pages.md (nhóm Auth). */
export default function LoginPage({ searchParams }: { searchParams: { redirect?: string | string[] } }) {
  const raw = Array.isArray(searchParams.redirect) ? searchParams.redirect[0] : searchParams.redirect;
  return <LoginForm redirect={raw ?? null} />;
}
