"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Alert, App, Button, Card, Dropdown, Empty, Input, Rate, Select, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { MenuProps, TableProps } from "antd";
import {
  ClearOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  MoreOutlined,
  ReloadOutlined,
  SafetyOutlined,
} from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { useAdmin } from "@/components/admin/shell/AdminSession";
import { HideReviewModal } from "@/components/admin/review/HideReviewModal";
import { getErrorMessage } from "@/lib/errorMessages";
import { productHref } from "@/lib/format";
import { fmtDateTime } from "@/lib/orderStatus";
import { clearSensitive, deleteReview, listReviews, unhideReview } from "@/services/admin/adminReviewService";
import type { ItemType, Pagination } from "@/types/api/common";
import type { AdminReview, AdminReviewStatus } from "@/types/api/review";

const PAGE_SIZES = [20, 50, 100];
const DEFAULT_LIMIT = 20;
const STATUSES: { value: AdminReviewStatus; label: string }[] = [
  { value: "VISIBLE", label: "Đang hiển thị" },
  { value: "SENSITIVE", label: "Đang làm mờ" },
  { value: "HIDDEN", label: "Đã ẩn" },
];
const ITEM_TYPES: { value: ItemType; label: string }[] = [
  { value: "PRODUCT", label: "Sản phẩm" },
  { value: "COMBO", label: "Combo" },
];

const itemHref = (r: AdminReview) =>
  r.item.slug ? (r.item.type === "PRODUCT" ? productHref(r.item.slug) : `/combos/${r.item.slug}`) : null;

/** Trạng thái hiển thị của một đánh giá: ẩn > đang mờ > đã đánh dấu an toàn > bình thường. */
function StatusTag({ r }: { r: AdminReview }) {
  if (r.isHidden) {
    return (
      <Tooltip
        title={
          <>
            Lý do: {r.hiddenReason}
            <br />
            {r.hiddenByName ? `Ẩn bởi ${r.hiddenByName}` : ""}
            {r.hiddenAt ? ` lúc ${fmtDateTime(r.hiddenAt)}` : ""}
          </>
        }
      >
        <Tag color="red">Đã ẩn</Tag>
      </Tooltip>
    );
  }
  if (r.moderationStatus === "SENSITIVE") return <Tag color="orange">Đang làm mờ</Tag>;
  if (r.moderationStatus === "CLEARED") return <Tag color="blue">Đã đánh dấu an toàn</Tag>;
  return <Tag color="green">Hiển thị</Tag>;
}

/**
 * /admin/reviews — kiểm duyệt đánh giá (FR-18, D-31, D-33). Lọc theo từ khoá (khách / mặt hàng, gõ không dấu được), loại,
 * số sao, trạng thái; bộ lọc + trang nằm trên URL. Hành động: Ẩn (bắt buộc lý do) / Sửa lý do, Hiện lại, Đánh dấu an toàn
 * (chỉ khi đang mờ), Xoá (chỉ Admin, có xác nhận).
 */
export function ReviewListView() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const { message, modal } = App.useApp();
  const { user } = useAdmin();
  const isAdmin = user?.role === "ADMIN";

  const q = params.get("q") ?? "";
  const itemType = ITEM_TYPES.some((t) => t.value === params.get("itemType")) ? (params.get("itemType") as ItemType) : undefined;
  const ratingParam = Number(params.get("rating"));
  const rating = ratingParam >= 1 && ratingParam <= 5 ? ratingParam : undefined;
  const status = STATUSES.some((s) => s.value === params.get("status")) ? (params.get("status") as AdminReviewStatus) : undefined;
  const page = Math.max(1, Number(params.get("page")) || 1);
  const limit = PAGE_SIZES.includes(Number(params.get("limit"))) ? Number(params.get("limit")) : DEFAULT_LIMIT;

  const [rows, setRows] = useState<AdminReview[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState(q);
  const [hiding, setHiding] = useState<AdminReview | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => setSearch(q), [q]);

  const setQuery = useCallback(
    (patch: Record<string, string | number | undefined>) => {
      const next = new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(patch)) {
        const isDefault = v === undefined || v === "" || (k === "page" && v === 1) || (k === "limit" && v === DEFAULT_LIMIT);
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
    listReviews({ q: q || undefined, itemType, rating, status, page, limit })
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
  }, [q, itemType, rating, status, page, limit, setQuery]);

  useEffect(load, [load]);

  const filtered = !!q || !!itemType || !!rating || !!status;
  const clear = () => setQuery({ q: undefined, itemType: undefined, rating: undefined, status: undefined, page: 1 });

  /** Thay dòng tại chỗ; nếu dòng không còn khớp bộ lọc trạng thái thì tải lại trang hiện tại. */
  const replaceRow = (updated: AdminReview) => {
    const stillMatches =
      !status ||
      (status === "HIDDEN" && updated.isHidden) ||
      (status === "VISIBLE" && !updated.isHidden) ||
      (status === "SENSITIVE" && !updated.isHidden && updated.moderationStatus === "SENSITIVE");
    if (stillMatches) setRows((rs) => rs.map((r) => (r.id === updated.id ? updated : r)));
    else load();
  };

  const act = async (r: AdminReview, run: () => Promise<AdminReview>, ok: string) => {
    setBusyId(r.id);
    try {
      replaceRow(await run());
      message.success(ok);
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setBusyId(null);
    }
  };

  const confirmDelete = (r: AdminReview) =>
    modal.confirm({
      title: "Xoá vĩnh viễn đánh giá này?",
      content: `Đánh giá ${r.rating}★ của ${r.user?.fullName ?? "khách"} sẽ bị xoá hẳn và không khôi phục được. Khách có thể viết lại.`,
      okText: "Xoá",
      okButtonProps: { danger: true },
      cancelText: "Huỷ",
      onOk: async () => {
        try {
          await deleteReview(r.id);
          message.success("Đã xoá đánh giá");
          load();
        } catch (e) {
          message.error(getErrorMessage(e));
        }
      },
    });

  const columns: TableProps<AdminReview>["columns"] = [
    {
      title: "Mặt hàng",
      key: "item",
      width: 220,
      render: (_, r) => {
        const href = itemHref(r);
        return (
          <div>
            {href ? (
              <Typography.Link href={href} target="_blank" rel="noopener noreferrer" style={{ fontWeight: 600 }}>
                {r.item.name}
              </Typography.Link>
            ) : (
              <Typography.Text strong>{r.item.name ?? `#${r.item.id}`}</Typography.Text>
            )}
            <div>
              <Tag style={{ marginTop: 4 }}>{r.item.type === "PRODUCT" ? "Sản phẩm" : "Combo"}</Tag>
            </div>
          </div>
        );
      },
    },
    {
      title: "Khách hàng",
      key: "user",
      width: 200,
      render: (_, r) =>
        r.user ? (
          <div>
            <div style={{ fontWeight: 600 }}>{r.user.fullName}</div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {r.user.email}
            </Typography.Text>
          </div>
        ) : (
          <Typography.Text type="secondary">—</Typography.Text>
        ),
    },
    {
      title: "Sao",
      dataIndex: "rating",
      width: 130,
      render: (v: number) => <Rate disabled value={v} style={{ fontSize: 13 }} aria-label={`${v} sao`} />,
    },
    {
      title: "Nội dung",
      key: "content",
      width: 320,
      render: (_, r) =>
        r.content ? (
          <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: "pre-line" }} ellipsis={{ rows: 2, expandable: true, symbol: "Xem đủ" }}>
            {r.content}
          </Typography.Paragraph>
        ) : (
          <Typography.Text type="secondary" italic>
            Không có nhận xét
          </Typography.Text>
        ),
    },
    {
      title: "Từ bị bắt",
      dataIndex: "flaggedTerms",
      width: 160,
      render: (terms: string[]) =>
        terms.length ? (
          <Space size={[4, 4]} wrap>
            {terms.map((t) => (
              <Tag key={t} color="volcano">
                {t}
              </Tag>
            ))}
          </Space>
        ) : (
          <Typography.Text type="secondary">—</Typography.Text>
        ),
    },
    { title: "Trạng thái", key: "status", width: 160, render: (_, r) => <StatusTag r={r} /> },
    { title: "Ngày viết", dataIndex: "createdAt", width: 150, render: (v: string) => fmtDateTime(v) },
    {
      key: "actions",
      width: 56,
      fixed: "right",
      render: (_, r) => {
        const items: MenuProps["items"] = [
          r.isHidden
            ? { key: "unhide", icon: <EyeOutlined />, label: "Hiện lại", onClick: () => act(r, () => unhideReview(r.id), "Đã hiện lại đánh giá") }
            : { key: "hide", icon: <EyeInvisibleOutlined />, label: "Ẩn đánh giá", onClick: () => setHiding(r) },
          ...(r.isHidden ? [{ key: "reason", icon: <EditOutlined />, label: "Sửa lý do ẩn", onClick: () => setHiding(r) }] : []),
          ...(r.moderationStatus === "SENSITIVE"
            ? [
                {
                  key: "clear",
                  icon: <SafetyOutlined />,
                  label: "Đánh dấu an toàn (bỏ làm mờ)",
                  onClick: () => act(r, () => clearSensitive(r.id), "Đã đánh dấu an toàn"),
                },
              ]
            : []),
          ...(isAdmin
            ? [
                { type: "divider" as const },
                { key: "delete", icon: <DeleteOutlined />, label: "Xoá vĩnh viễn", danger: true, onClick: () => confirmDelete(r) },
              ]
            : []),
        ];
        return (
          <Dropdown trigger={["click"]} placement="bottomRight" menu={{ items }}>
            <Button type="text" icon={<MoreOutlined />} loading={busyId === r.id} aria-label={`Thao tác với đánh giá #${r.id}`} />
          </Dropdown>
        );
      },
    },
  ];

  return (
    <>
      <PageHeader breadcrumb={[{ label: "Bán hàng" }]} title="Đánh giá" />
      <Card>
        <div className="cv-filter-bar">
          <Input.Search
            allowClear
            placeholder="Tìm theo tên / email khách hoặc tên mặt hàng"
            className="cv-filter-search"
            maxLength={100}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={(v) => setQuery({ q: v.trim(), page: 1 })}
          />
          <Select
            allowClear
            placeholder="Loại"
            className="cv-filter-item"
            value={itemType}
            onChange={(v?: ItemType) => setQuery({ itemType: v, page: 1 })}
            options={ITEM_TYPES}
          />
          <Select
            allowClear
            placeholder="Số sao"
            className="cv-filter-item"
            value={rating}
            onChange={(v?: number) => setQuery({ rating: v, page: 1 })}
            options={[5, 4, 3, 2, 1].map((n) => ({ value: n, label: `${n} sao` }))}
          />
          <Select
            allowClear
            placeholder="Trạng thái"
            className="cv-filter-item"
            value={status}
            onChange={(v?: AdminReviewStatus) => setQuery({ status: v, page: 1 })}
            options={STATUSES}
          />
        </div>

        <div className="cv-table-toolbar">
          <Typography.Text type="secondary">{pagination ? `${pagination.totalItems} đánh giá` : ""}</Typography.Text>
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
            message="Không tải được danh sách đánh giá"
            description={error}
            action={
              <Button size="small" onClick={load}>
                Thử lại
              </Button>
            }
          />
        ) : (
          <Table<AdminReview>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 1500 }}
            sticky={{ offsetHeader: 64 }}
            locale={{
              emptyText: (
                <Empty description={filtered ? "Không có đánh giá nào khớp bộ lọc." : "Chưa có đánh giá nào."}>
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
              showTotal: (t, [a, b]) => `${a}–${b} / ${t} đánh giá`,
              onChange: (p, size) => setQuery({ page: size !== limit ? 1 : p, limit: size }),
            }}
          />
        )}
      </Card>

      <HideReviewModal
        review={hiding}
        onClose={() => setHiding(null)}
        onDone={(updated) => {
          setHiding(null);
          replaceRow(updated);
        }}
      />
    </>
  );
}
