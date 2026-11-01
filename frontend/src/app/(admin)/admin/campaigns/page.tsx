"use client";

import { Suspense } from "react";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { CampaignListView } from "@/components/admin/promotion/CampaignListView";

/** /admin/campaigns — danh sách chiến dịch giảm giá (FR-17). */
export default function AdminCampaignsPage() {
  return (
    <RequireRole roles={ADMIN_ONLY}>
      {/* useSearchParams cần Suspense */}
      <Suspense>
        <CampaignListView />
      </Suspense>
    </RequireRole>
  );
}
