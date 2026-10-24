"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Alert, App, Button, Card, Dropdown, Empty, Image, Input, Segmented, Select, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { TableProps } from "antd";
import { ClearOutlined, EditOutlined, EyeInvisibleOutlined, EyeOutlined, MoreOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { getErrorMessage } from "@/lib/errorMessages";
import { vnd } from "@/lib/format";
import { foldText } from "@/lib/slug";
import { getCategoryTree } from "@/services/admin/adminProductService";
import { changeComboStatus, listCombos } from "@/services/admin/adminComboService";
import { PATH_SEPARATOR } from "@/components/admin/catalog/categoryTree";
import type { AdminComboSort, AdminComboSummary, CategoryNode } from "@/types/api/catalog";
import type { Pagination } from "@/types/api/common";

/** Khớp app.inventory.low-stock-threshold (backend, mặc định 5). */
const LOW_STOCK = 5;
const PAGE_SIZES = [10, 20, 50];
const DEFAULT_LIMIT = 20;
const DEFAULT_SORT: AdminComboSort = "UPDATED_DESC";

const SORT_OPTIONS: { value: AdminComboSort; label: string }[] = [
  { value: "UPDATED_DESC", label: "Mới cập nhật" },
  { value: "CREATED_DESC", label: "Mới thêm" },
  { value: "NAME_ASC", label: "Tên A → Z" },
  { value: "PRICE_ASC", label: "Giá thấp → cao" },
  { value: "PRICE_DESC", label: "Giá cao → thấp" },
  { value: "STOCK_ASC", label: "Tồn kho ít → nhiều" },
];

const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });

/** Danh sách combo cho admin (FR-15). Bộ lọc, sắp xếp, trang nằm trên URL. "Xoá" = ẩn (BR-14). */
export function ComboListView() {
  const { message, modal } = App.useApp();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const q = params.get("q") ?? "";
  const categoryId = params.get("categoryId") ? Number(params.get("categoryId")) : undefined;
  const status = (params.get("status") ?? "all") as "all" | "active" | "inactive";
  const sort = (params.get("sort") ?? DEFAULT_SORT) as AdminComboSort;
  const page = Math.max(1, Number(params.get("page")) || 1);
  const limit = PAGE_SIZES.includes(Number(params.get("limit"))) ? Number(params.get("limit")) : DEFAULT_LIMIT;

  const [rows, setRows] = useState<AdminComboSummary[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tree, setTree] = useState<CategoryNode[]>([]);
  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [search, setSearch] = useState(q);

  useEffect(() => setSearch(q), [q]);
  useEffect(() => {
    getCategoryTree()
      .then(setTree)
      .catch(() => setTree([]));
  }, []);

  const setQuery = useCallback(
    (patch: Record<string, string | number | undefined>) => {
      const next = new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(patch)) {
        const isDefault =
          v === undefined ||
          v === "" ||
          (k === "status" && v === "all") ||
          (k === "sort" && v === DEFAULT_SORT) ||
          (k === "page" && v === 1) ||
          (k === "limit" && v === DEFAULT_LIMIT);
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
    listCombos({ q: q || undefined, categoryId, isActive: status === "all" ? undefined : status === "active", sort, page, limit })
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
  }, [q, categoryId, status, sort, page, limit, setQuery]);

  useEffect(load, [load]);

  const toggle = (c: AdminComboSummary, next: boolean) => {
    const run = async () => {
      setTogglingId(c.id);
      try {
        const updated = await changeComboStatus(c.id, next);
        setRows((rs) => rs.map((r) => (r.id === c.id ? updated : r)));
        message.success(next ? `Đã hiện "${c.name}" trên cửa hàng.` : `Đã ẩn "${c.name}" khỏi cửa hàng.`);
      } catch (e) {
        message.error(getErrorMessage(e));
      } finally {
        setTogglingId(null);
      }
    };
    if (next) {
      void run();
      return;
    }
    modal.confirm({
      title: "Ẩn combo khỏi cửa hàng?",
      content: `"${c.name}" sẽ không hiển thị và không đặt mua được nữa. Đơn hàng cũ không bị ảnh hưởng; có thể hiện lại bất cứ lúc nào.`,
      okText: "Ẩn combo",
      okButtonProps: { danger: true },
      cancelText: "Huỷ",
      onOk: run,
    });
  };

  const columns: TableProps<AdminComboSummary>["columns"] = [
    {
      title: "Combo",
      key: "combo",
      render: (_, c) => (
        <div className="cv-prod-cell">
          {c.primaryImageUrl ? (
            <Image src={c.primaryImageUrl} alt={c.name} width={52} height={52} className="cv-prod-thumb" />
          ) : (
            <div className="cv-img-empty">—</div>
          )}
          <div className="cv-prod-info">
            <Link href={`/admin/combos/${c.id}`} className="cv-prod-name" title={c.name}>
              {c.name}
            </Link>
            <Typography.Text type="secondary" className="cv-prod-slug">
              {c.itemCount} sản phẩm · /combos/{c.slug}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: "Danh mục",
      key: "category",
      width: 200,
      render: (_, c) => {
        const parents = c.categoryPath.slice(0, -1);
        return (
          <Tooltip title={c.categoryPath.join(PATH_SEPARATOR)}>
            <div className="cv-cat-cell">
              {parents.length > 0 && <div className="cv-cat-parents">{parents.join(PATH_SEPARATOR)}</div>}
              <div className="cv-cat-leaf">{c.categoryName}</div>
            </div>
          </Tooltip>
        );
      },
    },
    {
      title: "Giá combo",
      key: "price",
      width: 160,
      align: "right",
      render: (_, c) => {
        const saving = c.itemsTotal - c.comboPrice;
        return (
          <div>
            <span className="cv-price">{vnd(c.comboPrice, " ₫")}</span>
            <div className="cv-variant-note">
              {saving > 0 ? (
                <Tooltip title={`Tổng giá lẻ ${vnd(c.itemsTotal, " ₫")}`}>
                  <span style={{ color: "#389e0d" }}>Rẻ hơn lẻ {Math.round((saving / c.itemsTotal) * 100)}%</span>
                </Tooltip>
              ) : (
                <span style={{ color: "#d46b08" }}>Không rẻ hơn lẻ</span>
              )}
            </div>
          </div>
        );
      },
    },
    {
      title: "Tồn kho",
      key: "stock",
      width: 130,
      render: (_, c) => (
        <Tooltip title={`Tồn ${c.stockQuantity} · Giữ chỗ ${c.reservedQuantity} · Khả dụng ${c.availableQuantity}`}>
          {c.availableQuantity <= 0 ? (
            <Tag color="red">Hết hàng</Tag>
          ) : c.availableQuantity <= LOW_STOCK ? (
            <Tag color="orange">Sắp hết · {c.availableQuantity}</Tag>
          ) : (
            <Tag color="green">Còn {c.availableQuantity}</Tag>
          )}
        </Tooltip>
      ),
    },
    {
      title: "Trạng thái",
      dataIndex: "isActive",
      width: 120,
      render: (active: boolean) => <span className={`cv-status ${active ? "is-on" : "is-off"}`}>{active ? "Đang bán" : "Đã ẩn"}</span>,
    },
    { title: "Cập nhật", dataIndex: "updatedAt", width: 110, render: (v: string) => fmtDate(v) },
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
              { key: "edit", icon: <EditOutlined />, label: <Link href={`/admin/combos/${c.id}`}>Sửa</Link> },
              { type: "divider" },
              c.isActive
                ? { key: "hide", icon: <EyeInvisibleOutlined />, label: "Ẩn khỏi cửa hàng", danger: true, onClick: () => toggle(c, false) }
                : { key: "show", icon: <EyeOutlined />, label: "Hiện lại trên cửa hàng", onClick: () => toggle(c, true) },
            ],
          }}
        >
          <Button type="text" icon={<MoreOutlined />} loading={togglingId === c.id} aria-label={`Thao tác với ${c.name}`} />
        </Dropdown>
      ),
    },
  ];

  const filtered = Boolean(q || categoryId || status !== "all");
  const clear = () => setQuery({ q: undefined, categoryId: undefined, status: "all", page: 1 });

  return (
    <>
      <PageHeader
        title="Combo"
        tags={pagination ? <Tag bordered={false}>{pagination.totalItems}</Tag> : null}
        actions={
          <Link href="/admin/combos/new">
            <Button type="primary" icon={<PlusOutlined />}>
              Thêm combo
            </Button>
          </Link>
        }
      />
      <Card>
        <div className="cv-filter-bar">
          <Input.Search
            allowClear
            placeholder="Tìm tên, slug combo (gõ không dấu được)"
            className="cv-filter-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={(v) => setQuery({ q: v.trim(), page: 1 })}
          />
          <Select
            allowClear
            showSearch
            placeholder="Tất cả danh mục"
            className="cv-filter-item"
            filterOption={(input, o) => foldText(String(o?.label ?? "")).includes(foldText(input.trim()))}
            value={categoryId}
            onChange={(v?: number) => setQuery({ categoryId: v, page: 1 })}
            options={tree.map((c) => ({ value: c.id, label: c.name }))}
          />
        </div>

        <div className="cv-table-toolbar">
          <Segmented
            value={status}
            onChange={(v) => setQuery({ status: v as string, page: 1 })}
            options={[
              { value: "all", label: "Tất cả" },
              { value: "active", label: "Đang hiển thị" },
              { value: "inactive", label: "Đã ẩn" },
            ]}
          />
          <Space wrap>
            {filtered && (
              <Button icon={<ClearOutlined />} onClick={clear}>
                Xoá bộ lọc
              </Button>
            )}
            <Select value={sort} onChange={(v) => setQuery({ sort: v, page: 1 })} options={SORT_OPTIONS} style={{ width: 190 }} prefix="Sắp xếp:" />
            <Tooltip title="Tải lại">
              <Button icon={<ReloadOutlined />} onClick={load} aria-label="Tải lại" />
            </Tooltip>
          </Space>
        </div>

        {error ? (
          <Alert
            type="error"
            showIcon
            message="Không tải được danh sách combo"
            description={error}
            action={
              <Button size="small" onClick={load}>
                Thử lại
              </Button>
            }
          />
        ) : (
          <Table<AdminComboSummary>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 960 }}
            sticky={{ offsetHeader: 64 }}
            rowClassName={(c) => (c.isActive ? "" : "cv-row-inactive")}
            locale={{
              emptyText: (
                <Empty description={filtered ? "Không có combo nào khớp bộ lọc." : "Chưa có combo nào."}>
                  {filtered ? (
                    <Button onClick={clear}>Xoá bộ lọc</Button>
                  ) : (
                    <Link href="/admin/combos/new">
                      <Button type="primary">Tạo combo đầu tiên</Button>
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
              showTotal: (t, [from, to]) => `${from}–${to} / ${t} combo`,
              onChange: (p, size) => setQuery({ page: size !== limit ? 1 : p, limit: size }),
            }}
          />
        )}
      </Card>
    </>
  );
}
