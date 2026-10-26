"use client";

import { useCallback, useEffect, useState } from "react";
import { App, Button, Card, Result, Skeleton, Space } from "antd";
import { ADMIN_ONLY } from "@/lib/admin/nav";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { ComboForm } from "@/components/admin/combo/ComboForm";
import { ComboStockCard } from "@/components/admin/combo/ComboStockCard";
import { getCombo, updateCombo } from "@/services/admin/adminComboService";
import type { AdminComboDetail } from "@/types/api/catalog";

/** /admin/combos/[id] — sửa combo (FR-15). */
export default function EditComboPage({ params }: { params: { id: string } }) {
  const id = Number(params.id);
  const { message } = App.useApp();
  const [combo, setCombo] = useState<AdminComboDetail | null>(null);
  const [error, setError] = useState<ApiError | Error | null>(null);
  // Đổi key sau khi lưu → form khởi tạo lại từ dữ liệu server
  const [version, setVersion] = useState(0);

  const load = useCallback(() => {
    setError(null);
    setCombo(null);
    if (!Number.isInteger(id) || id <= 0) {
      setError(new ApiError({ status: 404, errorCode: "NOT_FOUND", message: "Không tìm thấy combo." }));
      return;
    }
    getCombo(id)
      .then(setCombo)
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
          title={notFound ? "Không tìm thấy combo" : "Không tải được combo"}
          subTitle={notFound ? "Combo không tồn tại." : getErrorMessage(error)}
          extra={
            <Space>
              <Button href="/admin/combos">Về danh sách</Button>
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
  } else if (!combo) {
    body = (
      <Card>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  } else {
    body = (
      <>
        <ComboForm
          key={version}
          initial={combo}
          title={combo.name}
          tags={combo.isActive ? <span className="cv-status is-on">Đang bán</span> : <span className="cv-status is-off">Đã ẩn</span>}
          packedSets={combo.stockQuantity}
          // Đóng gói/tháo chỉ cập nhật kho, không khởi tạo lại form (giữ thay đổi chưa lưu)
          stockPanel={<ComboStockCard combo={combo} onChange={setCombo} />}
          submitText="Lưu thay đổi"
          onSubmit={async (req) => {
            const saved = await updateCombo(combo.id, req);
            setCombo(saved);
            setVersion((v) => v + 1);
            message.success("Đã lưu combo.");
          }}
        />
      </>
    );
  }

  return <RequireRole roles={ADMIN_ONLY}>{body}</RequireRole>;
}
