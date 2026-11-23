"use client";
/* eslint-disable @next/next/no-img-element */

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Card, Empty, Segmented, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { TableProps } from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { getErrorMessage } from "@/lib/errorMessages";
import { vnd } from "@/lib/format";
import { fmtDateTime } from "@/lib/orderStatus";
import { RETURN_STATUS, RETURN_STATUSES } from "@/lib/returns";
import { listReturns } from "@/services/admin/adminReturnService";
import type { Pagination, ReturnStatus } from "@/types/api/common";
import type { AdminReturnSummary } from "@/types/api/order";

const PAGE_SIZES = [10, 20, 50];
const DEFAULT_LIMIT = 20;

/**
 * /admin/returns — yêu cầu trả hàng (FR-19b, D-41): lọc trạng thái (mặc định Chờ duyệt), mới nhất trước. Bộ lọc và trang
 * nằm trên URL để F5 / chia sẻ link vẫn giữ.
 */
export function ReturnListView() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const status = (params.get("status") ?? "PENDING") as ReturnStatus | "ALL";
  const page = Math.max(1, Number(params.get("page")) || 1);
  const limit = PAGE_SIZES.includes(Number(params.get("limit"))) ? Number(params.get("limit")) : DEFAULT_LIMIT;

  const [rows, setRows] = useState<AdminReturnSummary[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const setQuery = useCallback(
    (patch: Record<string, string | number | undefined>) => {
      const next = new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(patch)) {
        const isDefault = v === undefined || (k === "status" && v === "PENDING") || (k === "page" && v === 1) || (k === "limit" && v === DEFAULT_LIMIT);
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
    listReturns({ status: status === "ALL" ? undefined : status, page, limit })
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
  }, [status, page, limit, setQuery]);

  useEffect(load, [load]);

  const columns: TableProps<AdminReturnSummary>["columns"] = [
    {
      title: "Yêu cầu",
      key: "id",
      width: 190,
      fixed: "left",
      render: (_, r) => (
        <div>
          <Link href={`/admin/returns/${r.id}`} style={{ fontWeight: 600 }}>
            #{r.id} · {r.orderCode}
          </Link>
          <div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {fmtDateTime(r.createdAt)}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: "Khách hàng",
      key: "customer",
      width: 220,
      render: (_, r) => (
        <div>
          <div>{r.customer?.fullName ?? "—"}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {r.customer?.email}
          </Typography.Text>
        </div>
      ),
    },
    {
      title: "Sản phẩm",
      key: "items",
      width: 260,
      render: (_, r) => (
        <Space>
          {r.firstItemImageUrl && <img src={r.firstItemImageUrl} alt="" width={40} height={40} style={{ objectFit: "cover", borderRadius: 4 }} />}
          <div>
            <div title={r.firstItemName ?? undefined} style={{ maxWidth: 190, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
              {r.firstItemName}
            </div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {r.itemCount} món
            </Typography.Text>
          </div>
        </Space>
      ),
    },
    {
      title: "Lý do",
      key: "reason",
      width: 220,
      render: (_, r) => (
        <Space>
          {r.firstImageUrl && <img src={r.firstImageUrl} alt="Ảnh khách gửi" width={40} height={40} style={{ objectFit: "cover", borderRadius: 4 }} />}
          <span>{r.reasonLabel}</span>
        </Space>
      ),
    },
    { title: "Tiền hoàn", dataIndex: "refundAmount", width: 130, align: "right", render: (v: number) => <b>{vnd(v)}</b> },
    {
      title: "Trạng thái",
      dataIndex: "status",
      width: 160,
      render: (s: ReturnStatus) => <Tag color={RETURN_STATUS[s].color}>{RETURN_STATUS[s].label}</Tag>,
    },
  ];

  return (
    <>
      <PageHeader breadcrumb={[{ label: "Đơn hàng", href: "/admin/orders" }, { label: "Yêu cầu trả hàng" }]} title="Yêu cầu trả hàng" />
      <Card>
        <div className="cv-table-toolbar">
          <div style={{ maxWidth: "100%", overflowX: "auto" }}>
            <Segmented
              value={status}
              onChange={(v) => setQuery({ status: v as string, page: 1 })}
              options={[...RETURN_STATUSES.map((s) => ({ value: s, label: RETURN_STATUS[s].label })), { value: "ALL", label: "Tất cả" }]}
            />
          </div>
          <Tooltip title="Tải lại">
            <Button icon={<ReloadOutlined />} onClick={load} aria-label="Tải lại" />
          </Tooltip>
        </div>

        {error ? (
          <Alert
            type="error"
            showIcon
            message="Không tải được danh sách yêu cầu trả hàng"
            description={error}
            action={
              <Button size="small" onClick={load}>
                Thử lại
              </Button>
            }
          />
        ) : (
          <Table<AdminReturnSummary>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 1180 }}
            sticky={{ offsetHeader: 64 }}
            onRow={(r) => ({ onDoubleClick: () => router.push(`/admin/returns/${r.id}`) })}
            locale={{ emptyText: <Empty description={status === "PENDING" ? "Không có yêu cầu nào chờ duyệt." : "Không có yêu cầu nào."} /> }}
            pagination={{
              current: pagination?.currentPage ?? page,
              pageSize: limit,
              total: pagination?.totalItems ?? 0,
              showSizeChanger: true,
              pageSizeOptions: PAGE_SIZES,
              showTotal: (t, [a, b]) => `${a}–${b} / ${t} yêu cầu`,
              onChange: (p, size) => setQuery({ page: size !== limit ? 1 : p, limit: size }),
            }}
          />
        )}
      </Card>
    </>
  );
}
