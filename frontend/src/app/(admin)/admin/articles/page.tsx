"use client";

import { Suspense } from "react";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ArticleListView } from "@/components/admin/article/ArticleListView";

/** /admin/articles — danh sách bài viết (FR-21b). */
export default function AdminArticlesPage() {
  return (
    <RequireRole roles={ADMIN_ONLY}>
      {/* useSearchParams cần Suspense */}
      <Suspense>
        <ArticleListView />
      </Suspense>
    </RequireRole>
  );
}
