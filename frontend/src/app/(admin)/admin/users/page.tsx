"use client";

import { Suspense } from "react";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { UserListView } from "@/components/admin/user/UserListView";

/** /admin/users — danh sách, tìm, chi tiết, sửa vai trò / khoá người dùng (FR-14), chỉ Admin. */
export default function AdminUsersPage() {
  return (
    <RequireRole roles={ADMIN_ONLY}>
      {/* useSearchParams cần Suspense */}
      <Suspense>
        <UserListView />
      </Suspense>
    </RequireRole>
  );
}
