import Link from "next/link";

/** Khung chung cho các trang xác thực (/login, và /register, /verify-email... khi làm): thẻ giữa trang, có đường về cửa hàng. */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="cv-auth">
      <div className="cv-auth__brand">
        <Link href="/">Campvivo</Link>
      </div>
      <div className="cv-auth__card">{children}</div>
      <p className="cv-auth__back">
        <Link href="/">← Về trang chủ</Link>
      </p>
    </main>
  );
}
