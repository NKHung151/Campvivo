import type { Metadata } from "next";
import { LoginForm } from "@/components/features/auth/LoginForm";

export const metadata: Metadata = {
  title: "Đăng nhập | Campvivo",
  robots: { index: false, follow: false },
};

/** /login?redirect=<đường dẫn nội bộ> — docs/03-san-pham/pages.md (nhóm Auth). */
export default function LoginPage({
  searchParams,
}: {
  searchParams: { redirect?: string | string[]; reset?: string | string[]; changed?: string | string[] };
}) {
  const first = (value: string | string[] | undefined) => (Array.isArray(value) ? value[0] : value);
  // ?reset=1: vừa đặt lại mật khẩu (từ /reset-password); ?changed=1: vừa đổi mật khẩu (từ /account) → hiện thông báo thành công.
  const notice = first(searchParams.reset) === "1" ? "reset" : first(searchParams.changed) === "1" ? "changed" : null;
  return <LoginForm redirect={first(searchParams.redirect) ?? null} notice={notice} />;
}
