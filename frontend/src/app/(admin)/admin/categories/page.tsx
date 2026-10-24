"use client";

import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { CategoryManager } from "@/components/admin/catalog/CategoryManager";

/** /admin/categories — quản lý danh mục đa cấp + khung thông số (FR-05, D-21). */
export default function AdminCategoriesPage() {
  return (
    <RequireRole roles={ADMIN_ONLY}>
      <CategoryManager />
    </RequireRole>
  );
}
