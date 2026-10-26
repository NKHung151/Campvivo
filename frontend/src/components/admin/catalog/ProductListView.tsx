"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, App, Button, Card, Dropdown, Empty, Image, Input, Segmented, Select, Space, Table, Tag, Tooltip, TreeSelect, Typography } from "antd";
import type { TableProps } from "antd";
import {
  ClearOutlined,
  EditOutlined,
  EyeInvisibleOutlined,
  EyeOutlined,
  ExportOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { getErrorMessage } from "@/lib/errorMessages";
import { vnd } from "@/lib/format";
import { changeProductStatus, getBrands, getCategoryTree, listProducts } from "@/services/admin/adminProductService";
import { PATH_SEPARATOR, categoryTreeSelectProps, toCategoryTreeData } from "@/components/admin/catalog/categoryTree";
import type { AdminProductSort, AdminProductSummary, Brand, CategoryNode, StockFilter } from "@/types/api/catalog";
import type { Pagination } from "@/types/api/common";

/** Khớp app.inventory.low-stock-threshold (backend, mặc định 5). */
const LOW_STOCK = 5;
const PAGE_SIZES = [10, 20, 50];
const DEFAULT_LIMIT = 20;
const DEFAULT_SORT: AdminProductSort = "UPDATED_DESC";

const SORT_OPTIONS: { value: AdminProductSort; label: string }[] = [
  { value: "UPDATED_DESC", label: "Mới cập nhật" },
  { value: "CREATED_DESC", label: "Mới thêm" },
  { value: "NAME_ASC", label: "Tên A → Z" },
  { value: "PRICE_ASC", label: "Giá thấp → cao" },
  { value: "PRICE_DESC", label: "Giá cao → thấp" },
  { value: "STOCK_ASC", label: "Tồn kho ít → nhiều" },
];
const STOCK_OPTIONS: { value: StockFilter; label: string }[] = [
  { value: "IN_STOCK", label: "Còn hàng" },
  { value: "LOW_STOCK", label: `Sắp hết (≤ ${LOW_STOCK})` },
  { value: "OUT_OF_STOCK", label: "Hết hàng" },
];

const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric" });
const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });

/**
 * Danh sách sản phẩm cho admin. Mọi bộ lọc, sắp xếp, trang nằm trên URL để F5 / chia sẻ link vẫn giữ.
 * "Xoá" sản phẩm = ẩn bằng công tắc trạng thái (BR-14).
 */
export function ProductListView() {
  const { message, modal } = App.useApp();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const q = params.get("q") ?? "";
  const categoryId = params.get("categoryId") ? Number(params.get("categoryId")) : undefined;
  const brand = params.get("brand") ?? undefined;
  const status = (params.get("status") ?? "all") as "all" | "active" | "inactive";
  const stock = (params.get("stock") ?? undefined) as StockFilter | undefined;
  const sort = (params.get("sort") ?? DEFAULT_SORT) as AdminProductSort;
  const page = Math.max(1, Number(params.get("page")) || 1);
  const limit = PAGE_SIZES.includes(Number(params.get("limit"))) ? Number(params.get("limit")) : DEFAULT_LIMIT;

  const [rows, setRows] = useState<AdminProductSummary[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tree, setTree] = useState<CategoryNode[]>([]);
  const [brands, setBrands] = useState<Brand[]>([]);
  const [togglingId, setTogglingId] = useState<number | null>(null);
  const [search, setSearch] = useState(q);

  useEffect(() => setSearch(q), [q]);
  useEffect(() => {
    getCategoryTree()
      .then(setTree)
      .catch(() => setTree([]));
    getBrands()
      .then(setBrands)
      .catch(() => setBrands([]));
  }, []);

  const treeData = useMemo(() => toCategoryTreeData(tree), [tree]);

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
    listProducts({
      q: q || undefined,
      categoryId,
      brand,
      isActive: status === "all" ? undefined : status === "active",
      stock,
      sort,
      page,
      limit,
    })
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
  }, [q, categoryId, brand, status, stock, sort, page, limit, setQuery]);

  useEffect(load, [load]);

  const toggle = (p: AdminProductSummary, next: boolean) => {
    const run = async () => {
      setTogglingId(p.id);
      try {
        const updated = await changeProductStatus(p.id, next);
        setRows((rs) => rs.map((r) => (r.id === p.id ? updated : r)));
        message.success(next ? `Đã hiện "${p.name}" trên cửa hàng.` : `Đã ẩn "${p.name}" khỏi cửa hàng.`);
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
      title: "Ẩn sản phẩm khỏi cửa hàng?",
      content: `"${p.name}" sẽ không hiển thị và không đặt mua được nữa. Đơn hàng cũ không bị ảnh hưởng; có thể hiện lại bất cứ lúc nào.`,
      okText: "Ẩn sản phẩm",
      okButtonProps: { danger: true },
      cancelText: "Huỷ",
      onOk: run,
    });
  };

  const columns: TableProps<AdminProductSummary>["columns"] = [
    {
      title: "Sản phẩm",
      key: "product",
      render: (_, p) => (
        <div className="cv-prod-cell">
          {p.primaryImageUrl ? (
            <Image src={p.primaryImageUrl} alt={p.name} width={52} height={52} className="cv-prod-thumb" />
          ) : (
            <div className="cv-img-empty">—</div>
          )}
          <div className="cv-prod-info">
            <Link href={`/admin/products/${p.id}`} className="cv-prod-name" title={p.name}>
              {p.name}
            </Link>
            <div className="cv-prod-meta">
              {p.brand && <span className="cv-prod-brand">{p.brand}</span>}
              <Typography.Text type="secondary" ellipsis={{ tooltip: `/products/${p.slug}` }} className="cv-prod-slug">
                /products/{p.slug}
              </Typography.Text>
            </div>
          </div>
        </div>
      ),
    },
    {
      title: "Danh mục",
      key: "category",
      width: 220,
      render: (_, p) => {
        const parents = p.categoryPath.slice(0, -1);
        return (
          <Tooltip title={p.categoryPath.join(PATH_SEPARATOR)}>
            <div className="cv-cat-cell">
              {parents.length > 0 && <div className="cv-cat-parents">{parents.join(PATH_SEPARATOR)}</div>}
              <div className="cv-cat-leaf">{p.categoryName}</div>
            </div>
          </Tooltip>
        );
      },
    },
    {
      title: "Giá bán",
      dataIndex: "price",
      width: 130,
      align: "right",
      render: (v: number) => <span className="cv-price">{vnd(v, " ₫")}</span>,
    },
    {
      title: "Tồn kho",
      key: "stock",
      width: 150,
      render: (_, p) => {
        const available = p.availableQuantity;
        const tag =
          available <= 0 ? (
            <Tag color="red">Hết hàng</Tag>
          ) : available <= LOW_STOCK ? (
            <Tag color="orange">Sắp hết · {available}</Tag>
          ) : (
            <Tag color="green">Còn {available}</Tag>
          );
        return (
          <Tooltip title={`Tồn kho ${p.stockQuantity} · Đang giữ chỗ ${p.reservedQuantity} · Khả dụng ${available}`}>
            <div>
              {tag}
            </div>
          </Tooltip>
        );
      },
    },
    {
      title: "Trạng thái",
      dataIndex: "isActive",
      width: 120,
      render: (active: boolean) => <span className={`cv-status ${active ? "is-on" : "is-off"}`}>{active ? "Đang bán" : "Đã ẩn"}</span>,
    },
    {
      title: "Cập nhật",
      dataIndex: "updatedAt",
      width: 110,
      render: (v: string) => (
        <div className="cv-date-cell">
          <div>{fmtDate(v)}</div>
          <Typography.Text type="secondary">{fmtTime(v)}</Typography.Text>
        </div>
      ),
    },
    {
      key: "actions",
      width: 56,
      fixed: "right",
      render: (_, p) => (
        <Dropdown
          trigger={["click"]}
          placement="bottomRight"
          menu={{
            items: [
              { key: "edit", icon: <EditOutlined />, label: <Link href={`/admin/products/${p.id}`}>Sửa</Link> },
              p.isActive
                ? {
                    key: "view",
                    icon: <ExportOutlined />,
                    label: (
                      <a href={`/products/${p.slug}`} target="_blank" rel="noreferrer">
                        Xem trên cửa hàng
                      </a>
                    ),
                  }
                : null,
              { type: "divider" },
              p.isActive
                ? { key: "hide", icon: <EyeInvisibleOutlined />, label: "Ẩn khỏi cửa hàng", danger: true, onClick: () => toggle(p, false) }
                : { key: "show", icon: <EyeOutlined />, label: "Hiện lại trên cửa hàng", onClick: () => toggle(p, true) },
            ],
          }}
        >
          <Button type="text" icon={<MoreOutlined />} loading={togglingId === p.id} aria-label={`Thao tác với ${p.name}`} />
        </Dropdown>
      ),
    },
  ];

  const filtered = Boolean(q || categoryId || brand || stock || status !== "all");
  const total = pagination?.totalItems ?? 0;

  return (
    <>
      <PageHeader
        title="Sản phẩm"
        tags={pagination ? <Tag bordered={false}>{pagination.totalItems}</Tag> : null}
        actions={
          <Link href="/admin/products/new">
            <Button type="primary" icon={<PlusOutlined />}>
              Thêm sản phẩm
            </Button>
          </Link>
        }
      />
      <Card>
        <div className="cv-filter-bar">
          <Input.Search
            allowClear
            placeholder="Tìm tên, thương hiệu, slug (gõ không dấu được)"
            className="cv-filter-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={(v) => setQuery({ q: v.trim(), page: 1 })}
          />
          <TreeSelect
            {...categoryTreeSelectProps}
            allowClear
            placeholder="Tất cả danh mục"
            className="cv-filter-item"
            treeData={treeData}
            value={categoryId}
            onChange={(v?: number) => setQuery({ categoryId: v, page: 1 })}
          />
          <Select
            allowClear
            showSearch
            placeholder="Tất cả thương hiệu"
            className="cv-filter-item"
            value={brand}
            onChange={(v?: string) => setQuery({ brand: v, page: 1 })}
            optionFilterProp="label"
            options={brands.map((b) => ({ value: b.name, label: b.name, count: b.productCount }))}
            optionRender={(o) => (
              <div className="cv-opt-count">
                <span>{o.label}</span>
                <Typography.Text type="secondary">{(o.data as { count: number }).count}</Typography.Text>
              </div>
            )}
          />
          <Select
            allowClear
            placeholder="Tình trạng kho"
            className="cv-filter-item cv-filter-sm"
            value={stock}
            onChange={(v?: StockFilter) => setQuery({ stock: v, page: 1 })}
            options={STOCK_OPTIONS}
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
              <Button
                icon={<ClearOutlined />}
                onClick={() => setQuery({ q: undefined, categoryId: undefined, brand: undefined, stock: undefined, status: "all", page: 1 })}
              >
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
            message="Không tải được danh sách sản phẩm"
            description={error}
            action={
              <Button size="small" onClick={load}>
                Thử lại
              </Button>
            }
          />
        ) : (
          <Table<AdminProductSummary>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 1000 }}
            sticky={{ offsetHeader: 64 }}
            rowClassName={(p) => (p.isActive ? "" : "cv-row-inactive")}
            locale={{
              emptyText: (
                <Empty description={filtered ? "Không có sản phẩm nào khớp bộ lọc." : "Chưa có sản phẩm nào."}>
                  {filtered ? (
                    <Button
                      onClick={() => setQuery({ q: undefined, categoryId: undefined, brand: undefined, stock: undefined, status: "all", page: 1 })}
                    >
                      Xoá bộ lọc
                    </Button>
                  ) : (
                    <Link href="/admin/products/new">
                      <Button type="primary">Thêm sản phẩm đầu tiên</Button>
                    </Link>
                  )}
                </Empty>
              ),
            }}
            pagination={{
              current: pagination?.currentPage ?? page,
              pageSize: limit,
              total,
              showSizeChanger: true,
              pageSizeOptions: PAGE_SIZES,
              showTotal: (t, [from, to]) => `${from}–${to} / ${t} sản phẩm`,
              onChange: (p, size) => setQuery({ page: size !== limit ? 1 : p, limit: size }),
            }}
          />
        )}
      </Card>
    </>
  );
}
