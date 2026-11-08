"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { ADMIN_ONLY, homeFor } from "@/lib/admin/nav";
import { useAdmin } from "@/components/admin/shell/AdminSession";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { DashboardView } from "@/components/admin/dashboard/DashboardView";

/** /admin — dashboard (FR-24, chỉ ADMIN). Nhân viên được chuyển tới trang đơn hàng (pages.md). */
export default function AdminDashboardPage() {
  const { ready, user } = useAdmin();
  const router = useRouter();

  useEffect(() => {
    if (ready && user?.role === "EMPLOYEE") router.replace(homeFor("EMPLOYEE"));
  }, [ready, user, router]);

  return (
    <RequireRole roles={ADMIN_ONLY}>
      <DashboardView />
    </RequireRole>
  );
}
