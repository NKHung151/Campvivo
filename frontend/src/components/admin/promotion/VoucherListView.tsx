"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Alert, App, Button, Card, Dropdown, Empty, Input, Progress, Segmented, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { TableProps } from "antd";
import { ClearOutlined, DeleteOutlined, EditOutlined, MoreOutlined, PauseCircleOutlined, PlayCircleOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { VoucherFormModal } from "@/components/admin/promotion/VoucherFormModal";
import { getErrorMessage } from "@/lib/errorMessages";
import { discountLabel, minOrderLabel } from "@/lib/promotion";
import { ApiError } from "@/services/apiError";
import { deleteVoucher, listVouchers, updateVoucher } from "@/services/admin/adminVoucherService";
import type { Pagination } from "@/types/api/common";
import type { AdminVoucher, VoucherRequest, VoucherStatus } from "@/types/api/promotion";

const PAGE_SIZES = [10, 20, 50];
const DEFAULT_LIMIT = 20;

export const VOUCHER_STATUS: Record<VoucherStatus, { label: string; color: string }> = {
  ACTIVE: { label: "Đang hiệu lực", color: "green" },
  UPCOMING: { label: "Sắp diễn ra", color: "blue" },
  EXHAUSTED: { label: "Hết lượt", color: "orange" },
  EXPIRED: { label: "Hết hạn", color: "default" },
  INACTIVE: { label: "Đã tắt", color: "red" },
};

const fmtDateTime = (iso: string) =>
  new Date(iso).toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });

const toRequest = (v: AdminVoucher, patch: Partial<VoucherRequest>): VoucherRequest => ({
  code: v.code,
  discountType: v.discountType,
  discountValue: v.discountValue,
  maxDiscount: v.maxDiscount,
  minOrderValue: v.minOrderValue,
  quantity: v.quantity,
  startDate: v.startDate,
  expiryDate: v.expiryDate,
  isActive: v.isActive,
  isPublic: v.isPublic,
  ...patch,
});

/** Danh sách voucher cho admin (FR-16). Bộ lọc, trang nằm trên URL. Đã có đơn dùng → không xoá được, chỉ tắt. */
export function VoucherListView() {
  const { message, modal } = App.useApp();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const q = params.get("q") ?? "";
  const status = (params.get("status") ?? "all") as VoucherStatus | "all";
  const page = Math.max(1, Number(params.get("page")) || 1);
  const limit = PAGE_SIZES.includes(Number(params.get("limit"))) ? Number(params.get("limit")) : DEFAULT_LIMIT;

  const [rows, setRows] = useState<AdminVoucher[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [search, setSearch] = useState(q);
  const [editing, setEditing] = useState<AdminVoucher | null | undefined>(undefined); // undefined = đóng, null = thêm mới

  useEffect(() => setSearch(q), [q]);

  const setQuery = useCallback(
    (patch: Record<string, string | number | undefined>) => {
      const next = new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(patch)) {
        const isDefault = v === undefined || v === "" || (k === "status" && v === "all") || (k === "page" && v === 1) || (k === "limit" && v === DEFAULT_LIMIT);
        if (isDefault) next.delete(k);
        else next.set(k, String(v));
      }
      const qs = next.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    },
    [params, pathname, router]
  );

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    listVouchers({ q: q || undefined, status: status === "all" ? undefined : status, page, limit })
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
  }, [q, status, page, limit, setQuery]);

  useEffect(load, [load]);

  const setActive = async (v: AdminVoucher, isActive: boolean) => {
    setBusyId(v.id);
    try {
      const updated = await updateVoucher(v.id, toRequest(v, { isActive }));
      setRows((rs) => rs.map((r) => (r.id === v.id ? updated : r)));
      message.success(isActive ? `Đã bật mã ${v.code}.` : `Đã tắt mã ${v.code}. Khách không dùng được mã này nữa.`);
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setBusyId(null);
    }
  };

  const remove = (v: AdminVoucher) =>
    modal.confirm({
      title: `Xoá mã ${v.code}?`,
      content: "Mã bị xoá vĩnh viễn. Mã đã có đơn hàng sử dụng thì không xoá được — hãy tắt mã thay vì xoá.",
      okText: "Xoá",
      okButtonProps: { danger: true },
      cancelText: "Huỷ",
      onOk: async () => {
        try {
          await deleteVoucher(v.id);
          message.success(`Đã xoá mã ${v.code}.`);
          load();
        } catch (e) {
          if (e instanceof ApiError && e.errorCode === "VOUCHER_IN_USE") {
            modal.confirm({
              title: "Mã đã có đơn hàng sử dụng",
              content: v.isActive
                ? `Không thể xoá ${v.code} vì đã có đơn dùng. Bạn có muốn tắt mã để khách không dùng được nữa?`
                : `Không thể xoá ${v.code} vì đã có đơn dùng. Mã đang tắt nên khách không dùng được.`,
              okText: v.isActive ? "Tắt mã" : "Đã hiểu",
              cancelText: "Đóng",
              onOk: v.isActive ? () => setActive(v, false) : undefined,
            });
          } else {
            message.error(getErrorMessage(e));
          }
        }
      },
    });

  const columns: TableProps<AdminVoucher>["columns"] = [
    {
      title: "Mã",
      key: "code",
      render: (_, v) => (
        <Space direction="vertical" size={0}>
          <Typography.Link strong copyable={{ text: v.code }} onClick={() => setEditing(v)}>
            {v.code}
          </Typography.Link>
          {!v.isPublic && (
            <Tooltip title="Không hiện trong danh sách mã — khách phải tự nhập">
              <Tag bordered={false}>Mã ẩn</Tag>
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: "Ưu đãi",
      key: "discount",
      render: (_, v) => (
        <div>
          <div>{discountLabel(v)}</div>
          <Typography.Text type="secondary">{minOrderLabel(v.minOrderValue)}</Typography.Text>
        </div>
      ),
    },
    {
      title: "Đã dùng",
      key: "usage",
      width: 150,
      render: (_, v) => (
        <Tooltip title={`Còn ${v.quantity - v.usedCount} lượt`}>
          <div>
            {v.usedCount} / {v.quantity}
            <Progress percent={Math.round((v.usedCount / v.quantity) * 100)} showInfo={false} size="small" />
          </div>
        </Tooltip>
      ),
    },
    {
      title: "Hiệu lực",
      key: "period",
      width: 190,
      render: (_, v) => (
        <div className="cv-variant-note">
          <div>Từ {fmtDateTime(v.startDate)}</div>
          <div>Đến {fmtDateTime(v.expiryDate)}</div>
        </div>
      ),
    },
    {
      title: "Trạng thái",
      dataIndex: "status",
      width: 130,
      render: (s: VoucherStatus) => <Tag color={VOUCHER_STATUS[s].color}>{VOUCHER_STATUS[s].label}</Tag>,
    },
    {
      key: "actions",
      width: 56,
      fixed: "right",
      render: (_, v) => (
        <Dropdown
          trigger={["click"]}
          placement="bottomRight"
          menu={{
            items: [
              { key: "edit", icon: <EditOutlined />, label: "Sửa", onClick: () => setEditing(v) },
              v.isActive
                ? { key: "off", icon: <PauseCircleOutlined />, label: "Tắt mã", onClick: () => setActive(v, false) }
                : { key: "on", icon: <PlayCircleOutlined />, label: "Bật mã", onClick: () => setActive(v, true) },
              { type: "divider" },
              { key: "delete", icon: <DeleteOutlined />, label: "Xoá", danger: true, onClick: () => remove(v) },
            ],
          }}
        >
          <Button type="text" icon={<MoreOutlined />} loading={busyId === v.id} aria-label={`Thao tác với ${v.code}`} />
        </Dropdown>
      ),
    },
  ];

  const filtered = Boolean(q || status !== "all");
  const clear = () => setQuery({ q: undefined, status: "all", page: 1 });

  return (
    <>
      <PageHeader
        breadcrumb={[{ label: "Khuyến mãi" }]}
        title="Voucher"
        tags={pagination ? <Tag bordered={false}>{pagination.totalItems}</Tag> : null}
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing(null)}>
            Thêm voucher
          </Button>
        }
      />
      <Card>
        <div className="cv-filter-bar">
          <Input.Search
            allowClear
            placeholder="Tìm theo mã"
            className="cv-filter-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={(v) => setQuery({ q: v.trim(), page: 1 })}
          />
        </div>

        <div className="cv-table-toolbar">
          <Segmented
            value={status}
            onChange={(v) => setQuery({ status: v as string, page: 1 })}
            options={[{ value: "all", label: "Tất cả" }, ...Object.entries(VOUCHER_STATUS).map(([value, s]) => ({ value, label: s.label }))]}
          />
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
            message="Không tải được danh sách voucher"
            description={error}
            action={
              <Button size="small" onClick={load}>
                Thử lại
              </Button>
            }
          />
        ) : (
          <Table<AdminVoucher>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 900 }}
            sticky={{ offsetHeader: 64 }}
            rowClassName={(v) => (v.isActive ? "" : "cv-row-inactive")}
            locale={{
              emptyText: (
                <Empty description={filtered ? "Không có voucher nào khớp bộ lọc." : "Chưa có voucher nào."}>
                  {filtered ? (
                    <Button onClick={clear}>Xoá bộ lọc</Button>
                  ) : (
                    <Button type="primary" onClick={() => setEditing(null)}>
                      Tạo voucher đầu tiên
                    </Button>
                  )}
                </Empty>
              ),
            }}
            pagination={{
              current: pagination?.currentPage ?? page,
              pageSize: limit,
              total: pagination?.totalItems ?? 0,
              showSizeChanger: true,
              pageSizeOptions: PAGE_SIZES,
              showTotal: (t, [from, to]) => `${from}–${to} / ${t} voucher`,
              onChange: (p, size) => setQuery({ page: size !== limit ? 1 : p, limit: size }),
            }}
          />
        )}
      </Card>

      <VoucherFormModal
        open={editing !== undefined}
        voucher={editing ?? null}
        onClose={() => setEditing(undefined)}
        onSaved={() => {
          setEditing(undefined);
          load();
        }}
      />
    </>
  );
}
