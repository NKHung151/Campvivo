"use client";
/* eslint-disable @next/next/no-img-element */

import { useCallback, useEffect, useState } from "react";
import { Alert, App, Button, Card, Col, Descriptions, Input, Modal, Result, Row, Skeleton, Space, Table, Tag, Timeline, Typography } from "antd";
import { CarOutlined, ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { getErrorMessage } from "@/lib/errorMessages";
import { vnd } from "@/lib/format";
import {
  ORDER_STATUS,
  PAYMENT_METHOD_LABEL,
  PAYMENT_STATUS,
  STAFF_ACTION_LABEL,
  fmtDateTime,
  ghnStatusLabel,
} from "@/lib/orderStatus";
import { isApiError } from "@/services/apiError";
import { changeOrderStatus, createShipment, getOrder, getShipmentTracking } from "@/services/admin/adminOrderService";
import type { OrderStatus } from "@/types/api/common";
import type { AdminOrderDetail, OrderDetail, ShipmentTracking } from "@/types/api/order";

type Load = { status: "loading" } | { status: "ready"; data: AdminOrderDetail } | { status: "error"; message: string; notFound: boolean };

/** Gợi ý nội dung xác nhận cho từng thao tác của nhân viên. */
const ACTION_HINT: Partial<Record<OrderStatus, string>> = {
  CONFIRMED: "Xác nhận đơn COD: trừ tồn kho và tự tạo vận đơn GHN (GHN thu hộ tổng tiền đơn).",
  CANCELLED: "Huỷ đơn COD đang chờ: trả lại hàng giữ chỗ. Bắt buộc ghi lý do (vd. không liên lạc được khách).",
  SHIPPING: "Đã bàn giao hàng cho đơn vị vận chuyển.",
  COMPLETED: "Khách đã nhận hàng. Đơn COD được ghi nhận đã thu tiền.",
};

/**
 * /admin/orders/[id] — chi tiết đơn cho Employee/Admin (FR-13): dòng hàng, tiền, người nhận, thanh toán, vận đơn GHN
 * (tạo lại, xem hành trình), lịch sử; chỉ hiện các nút chuyển trạng thái server cho phép (`allowedStatuses`).
 */
export function OrderDetailView({ id }: { id: number }) {
  const { message } = App.useApp();
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [action, setAction] = useState<OrderStatus | null>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [tracking, setTracking] = useState<ShipmentTracking | null>(null);
  const [trackingLoading, setTrackingLoading] = useState(false);

  const reload = useCallback(() => {
    if (!Number.isInteger(id) || id <= 0) {
      setLoad({ status: "error", message: "Không tìm thấy đơn hàng.", notFound: true });
      return;
    }
    getOrder(id)
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
          title={load.notFound ? "Không tìm thấy đơn hàng" : "Không tải được đơn hàng"}
          subTitle={load.message}
          extra={
            <Space>
              <Button href="/admin/orders">Về danh sách</Button>
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

  const { order: o, customer, allowedStatuses, canCreateShipment } = load.data;

  const submitAction = async () => {
    if (!action) return;
    if (action === "CANCELLED" && !note.trim()) {
      message.error("Vui lòng nhập lý do huỷ đơn.");
      return;
    }
    setBusy(true);
    try {
      await changeOrderStatus(o.id, action, note.trim() || undefined);
      message.success(`Đã chuyển đơn sang "${ORDER_STATUS[action].label}".`);
      setAction(null);
      setNote("");
      reload();
      // Vận đơn GHN được tạo bất đồng bộ sau khi xác nhận — tải lại thêm một lần để thấy mã vận đơn
      if (action === "CONFIRMED") setTimeout(reload, 2500);
    } catch (e) {
      message.error(getErrorMessage(e));
      // Người khác vừa xử lý → tải lại để thấy trạng thái mới
      if (isApiError(e) && e.errorCode === "INVALID_STATUS_TRANSITION") {
        setAction(null);
        reload();
      }
    } finally {
      setBusy(false);
    }
  };

  const retryShipment = async () => {
    setBusy(true);
    try {
      setLoad({ status: "ready", data: await createShipment(o.id) });
      message.success("Đã tạo vận đơn GHN.");
    } catch (e) {
      message.error(getErrorMessage(e));
      reload();
    } finally {
      setBusy(false);
    }
  };

  const loadTracking = async () => {
    setTrackingLoading(true);
    try {
      setTracking(await getShipmentTracking(o.id));
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setTrackingLoading(false);
    }
  };

  return (
    <>
      <PageHeader
        breadcrumb={[{ label: "Đơn hàng", href: "/admin/orders" }, { label: o.orderCode }]}
        title={`Đơn ${o.orderCode}`}
        tags={
          <>
            <Tag color={ORDER_STATUS[o.status].color}>{ORDER_STATUS[o.status].label}</Tag>
            <Tag color={PAYMENT_STATUS[o.payment.status].color}>{PAYMENT_STATUS[o.payment.status].label}</Tag>
          </>
        }
        actions={
          <>
            <Button icon={<ReloadOutlined />} onClick={reload} aria-label="Tải lại" />
            {allowedStatuses.map((s) => (
              <Button key={s} type={s === "CANCELLED" ? "default" : "primary"} danger={s === "CANCELLED"} onClick={() => setAction(s)}>
                {STAFF_ACTION_LABEL[s] ?? ORDER_STATUS[s].label}
              </Button>
            ))}
          </>
        }
      />
      {o.status === "PENDING" && o.paymentMethod === "VNPAY" && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="Đơn VNPay đang chờ khách thanh toán — hệ thống tự xác nhận khi VNPay báo kết quả, hoặc tự huỷ khi quá hạn."
        />
      )}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={16}>
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            <ItemsCard order={o} />
            <Card title="Lịch sử trạng thái" size="small">
              <Timeline
                items={[...o.history].reverse().map((h) => ({
                  color: ORDER_STATUS[h.newStatus].color,
                  children: (
                    <div>
                      <b>{ORDER_STATUS[h.newStatus].label}</b>{" "}
                      <Typography.Text type="secondary">{fmtDateTime(h.createdAt)}</Typography.Text>
                      {h.note && <div>{h.note}</div>}
                    </div>
                  ),
                }))}
              />
            </Card>
          </Space>
        </Col>
        <Col xs={24} lg={8}>
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            <Card title="Khách hàng & người nhận" size="small">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="Tài khoản">{customer ? `${customer.fullName} (${customer.email})` : "—"}</Descriptions.Item>
                <Descriptions.Item label="Người nhận">{o.recipientName}</Descriptions.Item>
                <Descriptions.Item label="SĐT">{o.recipientPhone}</Descriptions.Item>
                <Descriptions.Item label="Địa chỉ">{o.shippingAddress}</Descriptions.Item>
                {o.note && <Descriptions.Item label="Ghi chú">{o.note}</Descriptions.Item>}
                <Descriptions.Item label="Đặt lúc">{fmtDateTime(o.createdAt)}</Descriptions.Item>
              </Descriptions>
            </Card>
            <Card title="Thanh toán" size="small">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="Phương thức">{PAYMENT_METHOD_LABEL[o.paymentMethod]}</Descriptions.Item>
                <Descriptions.Item label="Trạng thái">{PAYMENT_STATUS[o.payment.status].label}</Descriptions.Item>
                {o.payment.paidAt && <Descriptions.Item label="Thanh toán lúc">{fmtDateTime(o.payment.paidAt)}</Descriptions.Item>}
              </Descriptions>
            </Card>
            <Card title={`Vận chuyển (${o.shipping.carrier})`} size="small">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="Mã vận đơn">{o.shipping.trackingCode ?? "Chưa tạo"}</Descriptions.Item>
              </Descriptions>
              {o.shipping.error && !o.shipping.trackingCode && (
                <Alert type="warning" showIcon style={{ margin: "8px 0" }} message="Tạo vận đơn thất bại" description={o.shipping.error} />
              )}
              <Space wrap style={{ marginTop: 8 }}>
                {canCreateShipment && (
                  <Button icon={<CarOutlined />} loading={busy} onClick={retryShipment}>
                    {o.shipping.error ? "Tạo lại vận đơn" : "Tạo vận đơn GHN"}
                  </Button>
                )}
                {o.shipping.trackingCode && (
                  <Button loading={trackingLoading} onClick={loadTracking}>
                    Xem hành trình
                  </Button>
                )}
              </Space>
              {tracking && (
                <div style={{ marginTop: 12 }}>
                  <div>
                    Trạng thái GHN: <b>{ghnStatusLabel(tracking.status)}</b>
                  </div>
                  {tracking.expectedDeliveryTime && <div>Dự kiến giao: {fmtDateTime(tracking.expectedDeliveryTime)}</div>}
                  <Timeline
                    style={{ marginTop: 8 }}
                    items={[...tracking.log].reverse().map((l) => ({
                      children: (
                        <span>
                          {ghnStatusLabel(l.status)} <Typography.Text type="secondary">{fmtDateTime(l.updatedAt)}</Typography.Text>
                        </span>
                      ),
                    }))}
                  />
                </div>
              )}
            </Card>
          </Space>
        </Col>
      </Row>

      <Modal
        open={action !== null}
        title={action ? STAFF_ACTION_LABEL[action] ?? ORDER_STATUS[action].label : ""}
        okText="Xác nhận"
        cancelText="Đóng"
        okButtonProps={{ danger: action === "CANCELLED", loading: busy }}
        onOk={submitAction}
        onCancel={() => {
          setAction(null);
          setNote("");
        }}
        destroyOnHidden
      >
        {action && <p>{ACTION_HINT[action]}</p>}
        <Input.TextArea
          rows={3}
          maxLength={500}
          showCount
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder={action === "CANCELLED" ? "Lý do huỷ (bắt buộc)" : "Ghi chú (không bắt buộc)"}
        />
      </Modal>
    </>
  );
}

function ItemsCard({ order: o }: { order: OrderDetail }) {
  return (
    <Card title={`Sản phẩm (${o.itemCount})`} size="small">
      <Table
        rowKey={(i) => `${i.type}-${i.itemId}`}
        size="small"
        pagination={false}
        dataSource={o.items}
        scroll={{ x: 560 }}
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
                </div>
              </Space>
            ),
          },
          { title: "Đơn giá", dataIndex: "unitPrice", align: "right", width: 120, render: (v: number) => vnd(v) },
          { title: "SL", dataIndex: "quantity", align: "center", width: 60 },
          { title: "Thành tiền", dataIndex: "lineTotal", align: "right", width: 130, render: (v: number) => vnd(v) },
        ]}
        summary={() => (
          <>
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={3} align="right">
                Tạm tính
              </Table.Summary.Cell>
              <Table.Summary.Cell index={1} align="right">
                {vnd(o.subtotal)}
              </Table.Summary.Cell>
            </Table.Summary.Row>
            {o.discountAmount > 0 && (
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={3} align="right">
                  Giảm giá {o.voucherCode ? `(${o.voucherCode})` : ""}
                </Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right">
                  -{vnd(o.discountAmount)}
                </Table.Summary.Cell>
              </Table.Summary.Row>
            )}
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={3} align="right">
                Phí vận chuyển
              </Table.Summary.Cell>
              <Table.Summary.Cell index={1} align="right">
                {o.shippingFee ? vnd(o.shippingFee) : "Miễn phí"}
              </Table.Summary.Cell>
            </Table.Summary.Row>
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={3} align="right">
                <b>Tổng thanh toán</b>
              </Table.Summary.Cell>
              <Table.Summary.Cell index={1} align="right">
                <b>{vnd(o.finalAmount)}</b>
              </Table.Summary.Cell>
            </Table.Summary.Row>
          </>
        )}
      />
    </Card>
  );
}
