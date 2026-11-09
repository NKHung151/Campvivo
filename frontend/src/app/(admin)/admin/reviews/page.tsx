"use client";

import { Suspense } from "react";
import { STAFF } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ReviewListView } from "@/components/admin/review/ReviewListView";

/** /admin/reviews — kiểm duyệt đánh giá (FR-18, D-31): lọc, ẩn / hiện lại, đánh dấu an toàn; xoá chỉ Admin. */
export default function AdminReviewsPage() {
  return (
    <RequireRole roles={STAFF}>
      {/* useSearchParams cần Suspense */}
      <Suspense>
        <ReviewListView />
      </Suspense>
    </RequireRole>
  );
}
