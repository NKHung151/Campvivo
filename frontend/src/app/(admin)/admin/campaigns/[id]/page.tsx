"use client";

import { useCallback, useEffect, useState } from "react";
import { App, Button, Card, Result, Skeleton, Space, Tag } from "antd";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { CampaignForm } from "@/components/admin/promotion/CampaignForm";
import { CAMPAIGN_STATUS } from "@/components/admin/promotion/CampaignListView";
import { getCampaign, updateCampaign } from "@/services/admin/adminCampaignService";
import type { AdminCampaignDetail } from "@/types/api/promotion";

/** /admin/campaigns/[id] — sửa chiến dịch giảm giá (FR-17). */
export default function EditCampaignPage({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  const { message } = App.useApp();
  const [campaign, setCampaign] = useState<AdminCampaignDetail | null>(null);
  const [error, setError] = useState<ApiError | Error | null>(null);
  // Đổi key sau khi lưu → form khởi tạo lại từ dữ liệu server
  const [version, setVersion] = useState(0);

  const load = useCallback(() => {
    setError(null);
    setCampaign(null);
    if (!Number.isInteger(id) || id <= 0) {
      setError(new ApiError({ status: 404, errorCode: "NOT_FOUND", message: "Không tìm thấy chiến dịch." }));
      return;
    }
    getCampaign(id)
      .then(setCampaign)
      .catch((e) => setError(e instanceof Error ? e : new Error(getErrorMessage(e))));
  }, [id]);

  useEffect(load, [load]);

  let body: React.ReactNode;
  if (error) {
    const notFound = error instanceof ApiError && error.errorCode === "NOT_FOUND";
    body = (
      <Card>
        <Result
          status={notFound ? "404" : "error"}
          title={notFound ? "Không tìm thấy chiến dịch" : "Không tải được chiến dịch"}
          subTitle={notFound ? "Chiến dịch không tồn tại hoặc đã bị xoá." : getErrorMessage(error)}
          extra={
            <Space>
              <Button href="/admin/campaigns">Về danh sách</Button>
              {!notFound && (
                <Button type="primary" onClick={load}>
                  Thử lại
                </Button>
              )}
            </Space>
          }
        />
      </Card>
    );
  } else if (!campaign) {
    body = (
      <Card>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  } else {
    const s = CAMPAIGN_STATUS[campaign.status];
    body = (
      <CampaignForm
        key={version}
        initial={campaign}
        title={campaign.name}
        tags={<Tag color={s.color}>{s.label}</Tag>}
        submitText="Lưu thay đổi"
        onSubmit={async (req) => {
          const saved = await updateCampaign(campaign.id, req);
          setCampaign(saved);
          setVersion((v) => v + 1);
          message.success("Đã lưu chiến dịch.");
        }}
      />
    );
  }

  return <RequireRole roles={ADMIN_ONLY}>{body}</RequireRole>;
}
