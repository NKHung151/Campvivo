"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Card, DatePicker, Empty, Input, Segmented, Select, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { TableProps } from "antd";
import { ClearOutlined, ReloadOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { getErrorMessage } from "@/lib/errorMessages";
import { vnd } from "@/lib/format";
import { ORDER_STATUS, ORDER_STATUSES, PAYMENT_METHOD_LABEL, PAYMENT_STATUS, fmtDateTime } from "@/lib/orderStatus";
import { listOrders } from "@/services/admin/adminOrderService";
import type { OrderStatus, Pagination, PaymentMethod } from "@/types/api/common";
import type { AdminOrderSummary } from "@/types/api/order";

const PAGE_SIZES = [10, 20, 50];
const DEFAULT_LIMIT = 20;

/**
 * /admin/orders — danh sách đơn toàn hệ thống (FR-13): lọc trạng thái, phương thức, khoảng ngày; tìm mã đơn / SĐT /
 * email / họ tên khách. Bộ lọc và trang nằm trên URL để F5 / chia sẻ link vẫn giữ.
 */
export function OrderListView() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const status = (params.get("status") ?? "ALL") as OrderStatus | "ALL";
  const paymentMethod = (params.get("paymentMethod") ?? undefined) as PaymentMethod | undefined;
  const q = params.get("q") ?? "";
  const from = params.get("from") ?? undefined;
  const to = params.get("to") ?? undefined;
  const page = Math.max(1, Number(params.get("page")) || 1);
  const limit = PAGE_SIZES.includes(Number(params.get("limit"))) ? Number(params.get("limit")) : DEFAULT_LIMIT;

  const [rows, setRows] = useState<AdminOrderSummary[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState(q);

  useEffect(() => setSearch(q), [q]);

  const setQuery = useCallback(
    (patch: Record<string, string | number | undefined>) => {
      const next = new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(patch)) {
        const isDefault = v === undefined || v === "" || (k === "status" && v === "ALL") || (k === "page" && v === 1) || (k === "limit" && v === DEFAULT_LIMIT);
        if (isDefault) next.delete(k);
        else next.set(k, String(v));
      }
      const qs = next.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    },
    [params, pathname, router],
  );

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    listOrders({ status: status === "ALL" ? undefined : status, paymentMethod, q: q || undefined, from, to, page, limit })
      .then((res) => {
        if (res.data.length === 0 && page > 1 && res.pagination.totalPages > 0) {
          setQuery({ page: res.pagination.totalPages });
          return;
        }
        setRows(res.data);
        setPagination(res.pagination);
      })
      .catch((e) => setError(getErrorMessage(e)))
      .finally(() => setLoading(false));
  }, [status, paymentMethod, q, from, to, page, limit, setQuery]);

  useEffect(load, [load]);

  const filtered = status !== "ALL" || !!paymentMethod || !!q || !!from || !!to;
  const clear = () => setQuery({ status: "ALL", paymentMethod: undefined, q: undefined, from: undefined, to: undefined, page: 1 });

  const columns: TableProps<AdminOrderSummary>["columns"] = [
    {
      title: "Mã đơn",
      key: "code",
      width: 200,
      fixed: "left",
      render: (_, o) => (
        <div>
          <Link href={`/admin/orders/${o.id}`} style={{ fontWeight: 600 }}>
            {o.orderCode}
          </Link>
          <div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {fmtDateTime(o.createdAt)}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: "Khách hàng / người nhận",
      key: "customer",
      width: 240,
      render: (_, o) => (
        <div>
          <div>{o.customer?.fullName ?? "—"}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {o.customer?.email}
          </Typography.Text>
          <div style={{ fontSize: 12 }}>
            Nhận: {o.recipientName} · {o.recipientPhone}
          </div>
        </div>
      ),
    },
    {
      title: "Sản phẩm",
      key: "items",
      width: 280,
      render: (_, o) => (
        <Space>
          {o.firstItemImageUrl && <img src={o.firstItemImageUrl} alt="" width={40} height={40} style={{ objectFit: "cover", borderRadius: 4 }} />}
          <div>
            <div title={o.firstItemName ?? undefined} style={{ maxWidth: 200, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
              {o.firstItemName}
            </div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {o.itemCount} món
            </Typography.Text>
          </div>
        </Space>
      ),
    },
    { title: "Tổng tiền", dataIndex: "finalAmount", width: 130, align: "right", render: (v: number) => <b>{vnd(v)}</b> },
    {
      title: "Thanh toán",
      key: "payment",
      width: 170,
      render: (_, o) => (
        <div>
          <div>{PAYMENT_METHOD_LABEL[o.paymentMethod]}</div>
          {o.paymentStatus && <Tag color={PAYMENT_STATUS[o.paymentStatus].color}>{PAYMENT_STATUS[o.paymentStatus].label}</Tag>}
        </div>
      ),
    },
    {
      title: "Trạng thái",
      dataIndex: "status",
      width: 150,
      render: (s: OrderStatus) => <Tag color={ORDER_STATUS[s].color}>{ORDER_STATUS[s].label}</Tag>,
    },
    {
      title: "Vận đơn",
      dataIndex: "shippingOrderCode",
      width: 120,
      render: (v: string | null) => v ?? <Typography.Text type="secondary">—</Typography.Text>,
    },
  ];

  return (
    <>
      <PageHeader breadcrumb={[{ label: "Đơn hàng" }]} title="Danh sách đơn hàng" />
      <Card>
        <div className="cv-filter-bar">
          <Input.Search
            allowClear
            placeholder="Tìm mã đơn, SĐT người nhận, email hoặc tên khách"
            className="cv-filter-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={(v) => setQuery({ q: v.trim(), page: 1 })}
          />
          <Select
            allowClear
            placeholder="Phương thức thanh toán"
            className="cv-filter-item"
            value={paymentMethod}
            onChange={(v?: PaymentMethod) => setQuery({ paymentMethod: v, page: 1 })}
            options={(Object.keys(PAYMENT_METHOD_LABEL) as PaymentMethod[]).map((m) => ({ value: m, label: PAYMENT_METHOD_LABEL[m] }))}
          />
          <DatePicker.RangePicker
            className="cv-filter-item"
            format="DD/MM/YYYY"
            placeholder={["Từ ngày", "Đến ngày"]}
            value={from && to ? [dayjs(from), dayjs(to)] : null}
            onChange={(r) =>
              setQuery({ from: r?.[0]?.format("YYYY-MM-DD"), to: r?.[1]?.format("YYYY-MM-DD"), page: 1 })
            }
          />
        </div>

        <div className="cv-table-toolbar">
          <div style={{ maxWidth: "100%", overflowX: "auto" }}>
            <Segmented
              value={status}
              onChange={(v) => setQuery({ status: v as string, page: 1 })}
              options={[{ value: "ALL", label: "Tất cả" }, ...ORDER_STATUSES.map((s) => ({ value: s, label: ORDER_STATUS[s].label }))]}
            />
          </div>
          <Space wrap>
            {filtered && (
              <Button icon={<ClearOutlined />} onClick={clear}>
                Xoá bộ lọc
              </Button>
            )}
            <Tooltip title="Tải lại">
              <Button icon={<ReloadOutlined />} onClick={load} aria-label="Tải lại" />
            </Tooltip>
          </Space>
        </div>

        {error ? (
          <Alert
            type="error"
            showIcon
            message="Không tải được danh sách đơn hàng"
            description={error}
            action={
              <Button size="small" onClick={load}>
                Thử lại
              </Button>
            }
          />
        ) : (
          <Table<AdminOrderSummary>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 1300 }}
            sticky={{ offsetHeader: 64 }}
            onRow={(o) => ({ onDoubleClick: () => router.push(`/admin/orders/${o.id}`) })}
            locale={{
              emptyText: (
                <Empty description={filtered ? "Không có đơn hàng nào khớp bộ lọc." : "Chưa có đơn hàng nào."}>
                  {filtered && <Button onClick={clear}>Xoá bộ lọc</Button>}
                </Empty>
              ),
            }}
            pagination={{
              current: pagination?.currentPage ?? page,
              pageSize: limit,
              total: pagination?.totalItems ?? 0,
              showSizeChanger: true,
              pageSizeOptions: PAGE_SIZES,
              showTotal: (t, [a, b]) => `${a}–${b} / ${t} đơn`,
              onChange: (p, size) => setQuery({ page: size !== limit ? 1 : p, limit: size }),
            }}
          />
        )}
      </Card>
    </>
  );
}
