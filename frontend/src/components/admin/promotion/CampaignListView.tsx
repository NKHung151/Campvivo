"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Alert, App, Button, Card, Dropdown, Empty, Image, Input, Segmented, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { TableProps } from "antd";
import { ClearOutlined, DeleteOutlined, EditOutlined, ExportOutlined, MoreOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { getErrorMessage } from "@/lib/errorMessages";
import { discountLabel } from "@/lib/promotion";
import { deleteCampaign, listCampaigns } from "@/services/admin/adminCampaignService";
import type { Pagination } from "@/types/api/common";
import type { AdminCampaignSummary, CampaignStatus } from "@/types/api/promotion";

const PAGE_SIZES = [10, 20, 50];
const DEFAULT_LIMIT = 20;

export const CAMPAIGN_STATUS: Record<CampaignStatus, { label: string; color: string }> = {
  RUNNING: { label: "Đang chạy", color: "green" },
  UPCOMING: { label: "Sắp diễn ra", color: "blue" },
  ENDED: { label: "Đã kết thúc", color: "default" },
  INACTIVE: { label: "Đã tắt", color: "red" },
};

const fmtDateTime = (iso: string) =>
  new Date(iso).toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });

/** Danh sách chiến dịch giảm giá cho admin (FR-17). Bộ lọc, trang nằm trên URL. */
export function CampaignListView() {
  const { message, modal } = App.useApp();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const q = params.get("q") ?? "";
  const status = (params.get("status") ?? "all") as CampaignStatus | "all";
  const page = Math.max(1, Number(params.get("page")) || 1);
  const limit = PAGE_SIZES.includes(Number(params.get("limit"))) ? Number(params.get("limit")) : DEFAULT_LIMIT;

  const [rows, setRows] = useState<AdminCampaignSummary[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState(q);

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
    listCampaigns({ q: q || undefined, status: status === "all" ? undefined : status, page, limit })
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

  const remove = (c: AdminCampaignSummary) =>
    modal.confirm({
      title: `Xoá chiến dịch "${c.name}"?`,
      content: "Giá các sản phẩm trong chiến dịch trở về giá gốc ngay. Đơn hàng đã đặt không bị ảnh hưởng. Muốn tạm dừng thì hãy tắt chiến dịch thay vì xoá.",
      okText: "Xoá",
      okButtonProps: { danger: true },
      cancelText: "Huỷ",
      onOk: async () => {
        try {
          await deleteCampaign(c.id);
          message.success(`Đã xoá chiến dịch "${c.name}".`);
          load();
        } catch (e) {
          message.error(getErrorMessage(e));
        }
      },
    });

  const columns: TableProps<AdminCampaignSummary>["columns"] = [
    {
      title: "Chiến dịch",
      key: "campaign",
      render: (_, c) => (
        <div className="cv-prod-cell">
          {c.bannerUrl ? (
            <Image src={c.bannerUrl} alt={c.name} width={80} height={45} style={{ objectFit: "cover" }} className="cv-prod-thumb" />
          ) : (
            <div className="cv-img-empty">—</div>
          )}
          <div className="cv-prod-info">
            <Link href={`/admin/campaigns/${c.id}`} className="cv-prod-name" title={c.name}>
              {c.name}
            </Link>
            <Typography.Text type="secondary" className="cv-prod-slug">
              /promotions/{c.slug}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    { title: "Mức giảm", key: "discount", width: 170, render: (_, c) => discountLabel(c) },
    { title: "Sản phẩm", dataIndex: "productCount", width: 100, align: "right" },
    {
      title: "Thời gian",
      key: "period",
      width: 190,
      render: (_, c) => (
        <div className="cv-variant-note">
          <div>Từ {fmtDateTime(c.startDate)}</div>
          <div>Đến {fmtDateTime(c.endDate)}</div>
        </div>
      ),
    },
    {
      title: "Trạng thái",
      dataIndex: "status",
      width: 130,
      render: (s: CampaignStatus) => <Tag color={CAMPAIGN_STATUS[s].color}>{CAMPAIGN_STATUS[s].label}</Tag>,
    },
    {
      key: "actions",
      width: 56,
      fixed: "right",
      render: (_, c) => (
        <Dropdown
          trigger={["click"]}
          placement="bottomRight"
          menu={{
            items: [
              { key: "edit", icon: <EditOutlined />, label: <Link href={`/admin/campaigns/${c.id}`}>Sửa</Link> },
              {
                key: "view",
                icon: <ExportOutlined />,
                disabled: c.status !== "RUNNING",
                label: (
                  <Tooltip title={c.status !== "RUNNING" ? "Trang chỉ hiển thị khi chiến dịch đang chạy" : undefined}>
                    <a href={`/promotions/${c.slug}`} target="_blank" rel="noreferrer">
                      Xem trang
                    </a>
                  </Tooltip>
                ),
              },
              { type: "divider" },
              { key: "delete", icon: <DeleteOutlined />, label: "Xoá", danger: true, onClick: () => remove(c) },
            ],
          }}
        >
          <Button type="text" icon={<MoreOutlined />} aria-label={`Thao tác với ${c.name}`} />
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
        title="Chiến dịch giảm giá"
        tags={pagination ? <Tag bordered={false}>{pagination.totalItems}</Tag> : null}
        actions={
          <Link href="/admin/campaigns/new">
            <Button type="primary" icon={<PlusOutlined />}>
              Tạo chiến dịch
            </Button>
          </Link>
        }
      />
      <Card>
        <div className="cv-filter-bar">
          <Input.Search
            allowClear
            placeholder="Tìm tên hoặc slug chiến dịch"
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
            options={[{ value: "all", label: "Tất cả" }, ...Object.entries(CAMPAIGN_STATUS).map(([value, s]) => ({ value, label: s.label }))]}
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
            message="Không tải được danh sách chiến dịch"
            description={error}
            action={
              <Button size="small" onClick={load}>
                Thử lại
              </Button>
            }
          />
        ) : (
          <Table<AdminCampaignSummary>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 900 }}
            sticky={{ offsetHeader: 64 }}
            rowClassName={(c) => (c.isActive ? "" : "cv-row-inactive")}
            locale={{
              emptyText: (
                <Empty description={filtered ? "Không có chiến dịch nào khớp bộ lọc." : "Chưa có chiến dịch nào."}>
                  {filtered ? (
                    <Button onClick={clear}>Xoá bộ lọc</Button>
                  ) : (
                    <Link href="/admin/campaigns/new">
                      <Button type="primary">Tạo chiến dịch đầu tiên</Button>
                    </Link>
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
              showTotal: (t, [from, to]) => `${from}–${to} / ${t} chiến dịch`,
              onChange: (p, size) => setQuery({ page: size !== limit ? 1 : p, limit: size }),
            }}
          />
        )}
      </Card>
    </>
  );
}
