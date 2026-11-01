"use client";

import { useRouter } from "next/navigation";
import { App } from "antd";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { CampaignForm } from "@/components/admin/promotion/CampaignForm";
import { createCampaign } from "@/services/admin/adminCampaignService";

/** /admin/campaigns/new — tạo chiến dịch giảm giá (FR-17). */
export default function NewCampaignPage() {
  const router = useRouter();
  const { message } = App.useApp();

  return (
    <RequireRole roles={ADMIN_ONLY}>
      <CampaignForm
        title="Tạo chiến dịch"
        submitText="Tạo chiến dịch"
        onSubmit={async (body) => {
          const created = await createCampaign(body);
          message.success(`Đã tạo chiến dịch "${created.name}".`);
          router.push(`/admin/campaigns/${created.id}`);
        }}
      />
    </RequireRole>
  );
}
