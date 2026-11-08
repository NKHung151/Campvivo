"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Alert, App, Button, Card, Dropdown, Empty, Image, Input, Segmented, Select, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { TableProps } from "antd";
import { ClearOutlined, CloudUploadOutlined, DeleteOutlined, EditOutlined, MoreOutlined, PlusOutlined, ReloadOutlined, StopOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { changeArticleStatus, deleteArticle, listArticles } from "@/services/admin/adminArticleService";
import { ARTICLE_CATEGORIES, ARTICLE_CATEGORY, fmtDate, fmtDateTime } from "@/components/admin/article/articleLabels";
import type { ArticleCategory, Pagination } from "@/types/api/common";
import type { AdminArticleSort, AdminArticleSummary } from "@/types/api/article";

const PAGE_SIZES = [10, 20, 50];
const DEFAULT_LIMIT = 10;
const SORTS: { value: AdminArticleSort; label: string }[] = [
  { value: "UPDATED_DESC", label: "Sửa gần nhất" },
  { value: "CREATED_DESC", label: "Tạo gần nhất" },
  { value: "TITLE_ASC", label: "Tiêu đề A → Z" },
];
type StatusFilter = "all" | "published" | "draft";

/** Danh sách bài viết cho admin (FR-21b). Bộ lọc, sắp xếp, trang nằm trên URL. */
export function ArticleListView() {
  const { message, modal } = App.useApp();
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const q = params.get("q") ?? "";
  const status = (["published", "draft"].includes(params.get("status") ?? "") ? params.get("status") : "all") as StatusFilter;
  const category = (ARTICLE_CATEGORIES as string[]).includes(params.get("category") ?? "") ? (params.get("category") as ArticleCategory) : undefined;
  const sort = (SORTS.some((s) => s.value === params.get("sort")) ? params.get("sort") : "UPDATED_DESC") as AdminArticleSort;
  const page = Math.max(1, Number(params.get("page")) || 1);
  const limit = PAGE_SIZES.includes(Number(params.get("limit"))) ? Number(params.get("limit")) : DEFAULT_LIMIT;

  const [rows, setRows] = useState<AdminArticleSummary[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState(q);
  const [busyId, setBusyId] = useState<number | null>(null);

  useEffect(() => setSearch(q), [q]);

  const setQuery = useCallback(
    (patch: Record<string, string | number | undefined>) => {
      const next = new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(patch)) {
        const isDefault =
          v === undefined ||
          v === "" ||
          (k === "status" && v === "all") ||
          (k === "sort" && v === "UPDATED_DESC") ||
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
    listArticles({
      q: q || undefined,
      category,
      isPublished: status === "all" ? undefined : status === "published",
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
  }, [q, category, status, sort, page, limit, setQuery]);

  useEffect(load, [load]);

  const toggle = async (a: AdminArticleSummary) => {
    setBusyId(a.id);
    try {
      const saved = await changeArticleStatus(a.id, !a.isPublished);
      message.success(saved.isPublished ? `Đã đăng "${a.title}".` : `Đã gỡ "${a.title}" xuống nháp.`);
      // Đang lọc theo trạng thái thì bài đổi trạng thái rời khỏi danh sách
      if (status === "all") setRows((list) => list.map((r) => (r.id === a.id ? saved : r)));
      else load();
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
        modal.warning({
          title: "Chưa đăng được bài",
          content: (
            <>
              <p>Bài cần đủ các mục sau trước khi đăng:</p>
              <ul style={{ paddingLeft: 20, margin: 0 }}>
                {Object.values(e.fieldErrors).map((m) => (
                  <li key={m}>{m}</li>
                ))}
              </ul>
            </>
          ),
          okText: "Mở bài để sửa",
          onOk: () => router.push(`/admin/articles/${a.id}`),
        });
      } else {
        message.error(getErrorMessage(e));
      }
    } finally {
      setBusyId(null);
    }
  };

  const remove = (a: AdminArticleSummary) =>
    modal.confirm({
      title: `Xoá bài "${a.title}"?`,
      content: a.isPublished
        ? "Bài đang hiển thị trên cửa hàng sẽ mất ngay và không khôi phục được. Muốn tạm ẩn thì hãy gỡ bài xuống nháp."
        : "Bài nháp sẽ bị xoá hẳn và không khôi phục được.",
      okText: "Xoá",
      okButtonProps: { danger: true },
      cancelText: "Huỷ",
      onOk: async () => {
        try {
          await deleteArticle(a.id);
          message.success(`Đã xoá bài "${a.title}".`);
          load();
        } catch (e) {
          message.error(getErrorMessage(e));
        }
      },
    });

  const columns: TableProps<AdminArticleSummary>["columns"] = [
    {
      title: "Bài viết",
      key: "article",
      render: (_, a) => (
        <div className="cv-prod-cell">
          {a.thumbnailUrl ? (
            <Image src={a.thumbnailUrl} alt={a.title} width={80} height={45} style={{ objectFit: "cover" }} className="cv-prod-thumb" />
          ) : (
            <Tooltip title="Chưa có ảnh đại diện">
              <div className="cv-img-empty cv-article-thumb-empty">—</div>
            </Tooltip>
          )}
          <div className="cv-prod-info">
            <Link href={`/admin/articles/${a.id}`} className="cv-prod-name" title={a.title}>
              {a.title}
            </Link>
            <Typography.Text type="secondary" className="cv-prod-slug" ellipsis={{ tooltip: a.summary }}>
              {a.summary || <i>Chưa có tóm tắt</i>}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    {
      title: "Loại",
      dataIndex: "category",
      width: 100,
      render: (c: ArticleCategory) => (
        <Tooltip title={ARTICLE_CATEGORY[c].hint}>
          <Tag bordered={false} color={ARTICLE_CATEGORY[c].color}>
            {ARTICLE_CATEGORY[c].label}
          </Tag>
        </Tooltip>
      ),
    },
    {
      title: "Trạng thái",
      dataIndex: "isPublished",
      width: 120,
      render: (p: boolean) => (p ? <span className="cv-status is-on">Đã đăng</span> : <span className="cv-status is-off">Nháp</span>),
    },
    { title: "Ngày đăng", dataIndex: "publishedAt", width: 115, render: (d: string | null) => fmtDate(d) },
    {
      title: "Cập nhật",
      key: "updated",
      width: 170,
      render: (_, a) => (
        <div className="cv-variant-note">
          <div>{fmtDateTime(a.updatedAt)}</div>
          <Typography.Text type="secondary">{a.authorName}</Typography.Text>
        </div>
      ),
    },
    {
      key: "actions",
      width: 56,
      fixed: "right",
      render: (_, a) => (
        <Dropdown
          trigger={["click"]}
          placement="bottomRight"
          disabled={busyId === a.id}
          menu={{
            items: [
              { key: "edit", icon: <EditOutlined />, label: <Link href={`/admin/articles/${a.id}`}>Sửa</Link> },
              a.isPublished
                ? { key: "unpublish", icon: <StopOutlined />, label: "Gỡ xuống nháp", onClick: () => toggle(a) }
                : { key: "publish", icon: <CloudUploadOutlined />, label: "Đăng bài", onClick: () => toggle(a) },
              { type: "divider" },
              { key: "delete", icon: <DeleteOutlined />, label: "Xoá", danger: true, onClick: () => remove(a) },
            ],
          }}
        >
          <Button type="text" icon={<MoreOutlined />} loading={busyId === a.id} aria-label={`Thao tác với ${a.title}`} />
        </Dropdown>
      ),
    },
  ];

  const filtered = Boolean(q || status !== "all" || category);
  const clear = () => setQuery({ q: undefined, status: "all", category: undefined, page: 1 });

  return (
    <>
      <PageHeader
        breadcrumb={[{ label: "Nội dung" }]}
        title="Bài viết"
        tags={pagination ? <Tag bordered={false}>{pagination.totalItems}</Tag> : null}
        actions={
          <Link href="/admin/articles/new">
            <Button type="primary" icon={<PlusOutlined />}>
              Viết bài mới
            </Button>
          </Link>
        }
      />
      <Card>
        <div className="cv-filter-bar">
          <Input.Search
            allowClear
            placeholder="Tìm theo tiêu đề (gõ không dấu được)"
            className="cv-filter-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={(v) => setQuery({ q: v.trim(), page: 1 })}
          />
          <Select<ArticleCategory | "">
            value={category ?? ""}
            onChange={(v) => setQuery({ category: v || undefined, page: 1 })}
            style={{ minWidth: 180 }}
            options={[{ value: "", label: "Tất cả loại bài" }, ...ARTICLE_CATEGORIES.map((c) => ({ value: c, label: ARTICLE_CATEGORY[c].label }))]}
            aria-label="Lọc theo loại bài"
          />
          <Select<AdminArticleSort>
            value={sort}
            onChange={(v) => setQuery({ sort: v, page: 1 })}
            style={{ minWidth: 170 }}
            options={SORTS}
            aria-label="Sắp xếp"
          />
        </div>

        <div className="cv-table-toolbar">
          <Segmented<StatusFilter>
            value={status}
            onChange={(v) => setQuery({ status: v, page: 1 })}
            options={[
              { value: "all", label: "Tất cả" },
              { value: "published", label: "Đã đăng" },
              { value: "draft", label: "Nháp" },
            ]}
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
            message="Không tải được danh sách bài viết"
            description={error}
            action={
              <Button size="small" onClick={load}>
                Thử lại
              </Button>
            }
          />
        ) : (
          <Table<AdminArticleSummary>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 900 }}
            sticky={{ offsetHeader: 64 }}
            locale={{
              emptyText: (
                <Empty description={filtered ? "Không có bài viết nào khớp bộ lọc." : "Chưa có bài viết nào."}>
                  {filtered ? (
                    <Button onClick={clear}>Xoá bộ lọc</Button>
                  ) : (
                    <Link href="/admin/articles/new">
                      <Button type="primary">Viết bài đầu tiên</Button>
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
              showTotal: (t, [from, to]) => `${from}–${to} / ${t} bài`,
              onChange: (p, size) => setQuery({ page: size !== limit ? 1 : p, limit: size }),
            }}
          />
        )}
      </Card>
    </>
  );
}
