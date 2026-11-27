"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Alert, App, Button, Card, Col, Descriptions, Image, Input, Modal, Result, Row, Skeleton, Space, Table, Tag, Typography } from "antd";
import { CheckOutlined, CloseOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { getErrorMessage } from "@/lib/errorMessages";
import { vnd } from "@/lib/format";
import { ORDER_STATUS, PAYMENT_METHOD_LABEL, PAYMENT_STATUS, fmtDateTime } from "@/lib/orderStatus";
import { RETURN_STATUS } from "@/lib/returns";
import { isApiError } from "@/services/apiError";
import { getReturn, processReturn } from "@/services/admin/adminReturnService";
import type { AdminReturnDetail, OrderDetail } from "@/types/api/order";

type Load = { status: "loading" } | { status: "ready"; data: AdminReturnDetail } | { status: "error"; message: string; notFound: boolean };
type Action = "APPROVE" | "REJECT";

/**
 * /admin/returns/[id] — xử lý yêu cầu trả hàng (FR-19b, D-41): xem lý do, ảnh khách chụp, đơn hàng; Duyệt (cộng lại tồn
 * kho, đơn → Đã trả hàng; hoàn tiền ngoài hệ thống) hoặc Từ chối (bắt buộc ghi chú cho khách).
 */
export function ReturnDetailView({ id }: { id: number }) {
  const { message } = App.useApp();
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [action, setAction] = useState<Action | null>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);

  const reload = useCallback(() => {
    if (!Number.isInteger(id) || id <= 0) {
      setLoad({ status: "error", message: "Không tìm thấy yêu cầu trả hàng.", notFound: true });
      return;
    }
    getReturn(id)
      .then((data) => setLoad({ status: "ready", data }))
      .catch((e) => setLoad({ status: "error", message: getErrorMessage(e), notFound: isApiError(e) && e.status === 404 }));
  }, [id]);

  useEffect(reload, [reload]);

  if (load.status === "loading") {
    return (
      <Card>
        <Skeleton active paragraph={{ rows: 10 }} />
      </Card>
    );
  }
  if (load.status === "error") {
    return (
      <Card>
        <Result
          status={load.notFound ? "404" : "error"}
          title={load.notFound ? "Không tìm thấy yêu cầu trả hàng" : "Không tải được yêu cầu trả hàng"}
          subTitle={load.message}
          extra={
            <Space>
              <Button href="/admin/returns">Về danh sách</Button>
              {!load.notFound && (
                <Button type="primary" onClick={reload}>
                  Thử lại
                </Button>
              )}
            </Space>
          }
        />
      </Card>
    );
  }

  const { request: r, order: o, customer } = load.data;
  const pending = r.status === "PENDING";
  const itemCount = o.items.reduce((s, i) => s + i.quantity, 0);

  const submit = async () => {
    if (!action) return;
    if (action === "REJECT" && !note.trim()) {
      message.error("Vui lòng nhập lý do từ chối để gửi cho khách");
      return;
    }
    setBusy(true);
    try {
      const data = await processReturn(r.id, action, note.trim() || undefined);
      setLoad({ status: "ready", data });
      message.success(action === "APPROVE" ? "Đã duyệt trả hàng, tồn kho đã được cộng lại" : "Đã từ chối yêu cầu trả hàng");
      setAction(null);
      setNote("");
    } catch (e) {
      // Nhân viên khác vừa xử lý → tải lại để thấy trạng thái mới (user-flows.md mục 12)
      if (isApiError(e) && e.errorCode === "INVALID_STATUS_TRANSITION") {
        message.warning("Yêu cầu đã có người xử lý. Đã tải lại trạng thái mới nhất.");
        setAction(null);
        reload();
      } else {
        message.error(isApiError(e) && Object.values(e.fieldErrors)[0] ? Object.values(e.fieldErrors)[0] : getErrorMessage(e));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        breadcrumb={[{ label: "Đơn hàng", href: "/admin/orders" }, { label: "Yêu cầu trả hàng", href: "/admin/returns" }, { label: `#${r.id}` }]}
        title={`Yêu cầu trả hàng #${r.id}`}
        tags={<Tag color={RETURN_STATUS[r.status].color}>{RETURN_STATUS[r.status].label}</Tag>}
        actions={
          pending && (
            <Space wrap>
              <Button danger icon={<CloseOutlined />} onClick={() => setAction("REJECT")}>
                Từ chối
              </Button>
              <Button type="primary" icon={<CheckOutlined />} onClick={() => setAction("APPROVE")}>
                Duyệt trả hàng
              </Button>
            </Space>
          )
        }
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card title="Khách yêu cầu">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="Lý do">
                <b>{r.reasonLabel}</b>
              </Descriptions.Item>
              <Descriptions.Item label="Mô tả">{r.description ?? <Typography.Text type="secondary">Không có</Typography.Text>}</Descriptions.Item>
              <Descriptions.Item label="Phương án">Trả hàng và hoàn tiền (trả nguyên đơn)</Descriptions.Item>
              <Descriptions.Item label="Tiền hoàn">
                <b style={{ color: "#c7370f" }}>{vnd(r.refundAmount)}</b>
                <Typography.Text type="secondary"> (tạm tính − giảm giá, không gồm phí ship)</Typography.Text>
              </Descriptions.Item>
              <Descriptions.Item label="Gửi lúc">{fmtDateTime(r.createdAt)}</Descriptions.Item>
              {r.processedAt && <Descriptions.Item label="Xử lý lúc">{fmtDateTime(r.processedAt)}</Descriptions.Item>}
              {r.note && <Descriptions.Item label="Ghi chú nhân viên">{r.note}</Descriptions.Item>}
            </Descriptions>
            <Typography.Text strong>Ảnh khách gửi</Typography.Text>
            <div style={{ marginTop: 8 }}>
              <Image.PreviewGroup>
                <Space wrap>
                  {r.imageUrls.map((url, i) => (
                    <Image key={url} src={url} alt={`Ảnh ${i + 1}`} width={140} height={140} style={{ objectFit: "cover", borderRadius: 6 }} />
                  ))}
                </Space>
              </Image.PreviewGroup>
            </div>
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="Đơn hàng">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="Mã đơn">
                <Link href={`/admin/orders/${o.id}`}>{o.orderCode}</Link>
              </Descriptions.Item>
              <Descriptions.Item label="Trạng thái">
                <Tag color={ORDER_STATUS[o.status].color}>{ORDER_STATUS[o.status].label}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Thanh toán">
                {PAYMENT_METHOD_LABEL[o.paymentMethod]} · {PAYMENT_STATUS[o.payment.status].label}
              </Descriptions.Item>
              <Descriptions.Item label="Hoàn tất lúc">{fmtDateTime(o.completedAt)}</Descriptions.Item>
              <Descriptions.Item label="Hạn trả hàng">{fmtDateTime(o.returnDeadline)}</Descriptions.Item>
              <Descriptions.Item label="Khách hàng">
                {customer ? (
                  <>
                    {customer.fullName} · {customer.email}
                    {customer.phone && ` · ${customer.phone}`}
                  </>
                ) : (
                  "—"
                )}
              </Descriptions.Item>
              <Descriptions.Item label="Người nhận">
                {o.recipientName} · {o.recipientPhone}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        <Col span={24}>
          <Card title={`Hàng trả (${itemCount} món)`}>
            <ItemsTable order={o} />
          </Card>
        </Col>
      </Row>

      <Modal
        open={action !== null}
        title={action === "APPROVE" ? "Duyệt yêu cầu trả hàng" : "Từ chối yêu cầu trả hàng"}
        okText={action === "APPROVE" ? "Duyệt" : "Từ chối"}
        okButtonProps={{ danger: action === "REJECT", loading: busy }}
        cancelText="Đóng"
        onOk={() => void submit()}
        onCancel={() => !busy && setAction(null)}
        destroyOnClose
      >
        {action === "APPROVE" ? (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message={`Cộng lại ${itemCount} món vào tồn kho và chuyển đơn ${o.orderCode} sang "Đã trả hàng".`}
            description={`Hoàn ${vnd(r.refundAmount)} cho khách ngoài hệ thống (chuyển khoản / tiền mặt). Thao tác không hoàn tác được.`}
          />
        ) : (
          <Typography.Paragraph type="secondary">Khách sẽ thấy lý do này và không gửi lại yêu cầu được cho đơn này.</Typography.Paragraph>
        )}
        <Input.TextArea
          rows={3}
          maxLength={1000}
          showCount
          placeholder={action === "APPROVE" ? "Ghi chú cho khách (không bắt buộc)" : "Lý do từ chối (bắt buộc)"}
          value={note}
          onChange={(e) => setNote(e.target.value)}
        />
      </Modal>
    </>
  );
}

function ItemsTable({ order }: { order: OrderDetail }) {
  return (
    <Table
      rowKey={(i) => `${i.type}-${i.itemId}-${i.variantId ?? ""}`}
      size="small"
      pagination={false}
      dataSource={order.items}
      scroll={{ x: 520 }}
      columns={[
        {
          title: "Mặt hàng",
          key: "name",
          render: (_, i) => (
            <Space>
              {i.imageUrl && <img src={i.imageUrl} alt="" width={44} height={44} style={{ objectFit: "cover", borderRadius: 4 }} />}
              <div>
                {i.name}
                {i.type === "COMBO" && <Tag style={{ marginLeft: 6 }}>Combo</Tag>}
                {i.variantTitle && <div style={{ color: "#888", fontSize: 12 }}>Phân loại: {i.variantTitle}</div>}
              </div>
            </Space>
          ),
        },
        { title: "Đơn giá", dataIndex: "unitPrice", align: "right", width: 120, render: (v: number) => vnd(v) },
        { title: "SL", dataIndex: "quantity", align: "center", width: 60 },
        { title: "Thành tiền", dataIndex: "lineTotal", align: "right", width: 130, render: (v: number) => vnd(v) },
      ]}
    />
  );
}
