"use client";

import { useState } from "react";
import { App, Button, Card, InputNumber, Modal, Space, Table, Typography } from "antd";
import { InboxOutlined, ExportOutlined } from "@ant-design/icons";
import { getErrorMessage } from "@/lib/errorMessages";
import { ApiError } from "@/services/apiError";
import { packCombo, unpackCombo } from "@/services/admin/adminComboService";
import type { AdminComboDetail } from "@/types/api/catalog";

type Action = "pack" | "unpack";

/**
 * Kho combo (D-03, D-22): số bộ đã đóng gói / đang giữ chỗ / còn bán. "Đóng gói thêm" lấy hàng lẻ ra khỏi kho từng
 * sản phẩm để thành bộ combo; "Tháo bớt" trả hàng về (chỉ số bộ chưa bị giữ chỗ). Hộp thoại xem trước số hàng bị trừ/trả.
 */
export function ComboStockCard({ combo, onChange }: { combo: AdminComboDetail; onChange: (c: AdminComboDetail) => void }) {
  const { message } = App.useApp();
  const [action, setAction] = useState<Action | null>(null);
  const [sets, setSets] = useState(1);
  const [busy, setBusy] = useState(false);

  const open = (a: Action) => {
    setSets(1);
    setAction(a);
  };
  const max = action === "pack" ? combo.maxPackable : Math.max(0, combo.availableQuantity);

  const run = async () => {
    if (!action) return;
    setBusy(true);
    try {
      const updated = action === "pack" ? await packCombo(combo.id, sets) : await unpackCombo(combo.id, sets);
      onChange(updated);
      message.success(action === "pack" ? `Đã đóng gói ${sets} bộ.` : `Đã tháo ${sets} bộ, hàng đã trả về kho.`);
      setAction(null);
    } catch (e) {
      // Giữ câu cụ thể của server (thiếu bao nhiêu ở từng sản phẩm) thay vì câu chung theo mã lỗi
      message.error(e instanceof ApiError && e.message ? e.message : getErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const rows = combo.items.map((i) => {
    const change = i.quantity * sets;
    const now = i.productAvailableQuantity;
    return {
      key: i.productId,
      name: i.productName,
      perSet: i.quantity,
      change,
      now,
      after: action === "pack" ? now - change : now + change,
    };
  });

  return (
    <Card title="Kho combo" style={{ marginBottom: 20 }}>
      <div className="cv-mini-stats">
        <div>
          <span>Đã đóng gói</span>
          <b>{combo.stockQuantity}</b>
        </div>
        <div>
          <span>Giữ chỗ</span>
          <b>{combo.reservedQuantity}</b>
        </div>
        <div>
          <span>Còn bán</span>
          <b className={combo.availableQuantity > 0 ? "is-good" : "is-bad"}>{combo.availableQuantity}</b>
        </div>
      </div>
      <Space style={{ marginTop: 12 }} wrap>
        <Button type="primary" icon={<InboxOutlined />} onClick={() => open("pack")} disabled={combo.maxPackable <= 0}>
          Đóng gói thêm{combo.maxPackable > 0 ? ` (tối đa ${combo.maxPackable})` : ""}
        </Button>
        <Button icon={<ExportOutlined />} onClick={() => open("unpack")} disabled={combo.availableQuantity <= 0}>
          Tháo bớt
        </Button>
      </Space>

      <Modal
        open={action !== null}
        title={action === "pack" ? "Đóng gói thêm bộ combo" : "Tháo bớt bộ combo"}
        okText={action === "pack" ? `Đóng gói ${sets} bộ` : `Tháo ${sets} bộ`}
        okButtonProps={{ disabled: sets < 1 || sets > max, loading: busy }}
        cancelText="Huỷ"
        onOk={run}
        onCancel={() => setAction(null)}
        width={620}
        destroyOnHidden
      >
        <Space style={{ marginBottom: 12 }}>
          <span>Số bộ:</span>
          <InputNumber min={1} max={Math.max(1, max)} precision={0} value={sets} onChange={(v) => setSets(v ?? 1)} />
          <Typography.Text type="secondary">tối đa {max}</Typography.Text>
        </Space>
        <Table
          size="small"
          pagination={false}
          dataSource={rows}
          columns={[
            { title: "Sản phẩm", dataIndex: "name" },
            { title: "Mỗi bộ", dataIndex: "perSet", width: 70, align: "right" },
            {
              title: action === "pack" ? "Lấy ra" : "Trả về",
              dataIndex: "change",
              width: 80,
              align: "right",
              render: (v: number) => <b>{action === "pack" ? `−${v}` : `+${v}`}</b>,
            },
            { title: "Kho hiện tại", dataIndex: "now", width: 100, align: "right" },
            {
              title: "Sau khi làm",
              dataIndex: "after",
              width: 100,
              align: "right",
              render: (v: number) => <Typography.Text type={v < 0 ? "danger" : undefined}>{v}</Typography.Text>,
            },
          ]}
        />
      </Modal>
    </Card>
  );
}
