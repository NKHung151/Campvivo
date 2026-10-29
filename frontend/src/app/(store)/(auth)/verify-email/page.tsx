import type { Metadata } from "next";
import { VerifyEmailForm } from "@/components/features/auth/VerifyEmailForm";

export const metadata: Metadata = {
  title: "Xác thực email | Campvivo",
  robots: { index: false, follow: false },
};

type Param = string | string[] | undefined;
const first = (value: Param) => (Array.isArray(value) ? value[0] : value);

/** /verify-email?email=<email>&redirect=<đường dẫn nội bộ> — docs/03-san-pham/pages.md (nhóm Auth). */
export default function VerifyEmailPage({ searchParams }: { searchParams: { email?: Param; redirect?: Param } }) {
  return <VerifyEmailForm email={first(searchParams.email) ?? null} redirect={first(searchParams.redirect) ?? null} />;
}
