import type { Metadata } from "next";
import { RegisterForm } from "@/components/features/auth/RegisterForm";

export const metadata: Metadata = {
  title: "Đăng ký | Campvivo",
  robots: { index: false, follow: false },
};

/** /register?redirect=<đường dẫn nội bộ> — docs/03-san-pham/pages.md (nhóm Auth). */
export default function RegisterPage({ searchParams }: { searchParams: { redirect?: string | string[] } }) {
  const raw = Array.isArray(searchParams.redirect) ? searchParams.redirect[0] : searchParams.redirect;
  return <RegisterForm redirect={raw ?? null} />;
}
