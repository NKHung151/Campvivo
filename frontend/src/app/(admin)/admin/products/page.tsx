"use client";

import { Suspense } from "react";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ProductListView } from "@/components/admin/catalog/ProductListView";

/** /admin/products — danh sách sản phẩm (FR-06). */
export default function AdminProductsPage() {
  return (
    <RequireRole roles={ADMIN_ONLY}>
      {/* useSearchParams cần Suspense */}
      <Suspense>
        <ProductListView />
      </Suspense>
    </RequireRole>
  );
}
