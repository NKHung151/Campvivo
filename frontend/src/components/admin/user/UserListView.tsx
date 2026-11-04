"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Card, Empty, Input, Select, Space, Table, Tag, Tooltip, Typography } from "antd";
import type { TableProps } from "antd";
import { ClearOutlined, ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { UserDrawer } from "@/components/admin/user/UserDrawer";
import { ROLE_LABEL, ROLES } from "@/components/admin/user/userLabels";
import { getErrorMessage } from "@/lib/errorMessages";
import { fmtDateTime } from "@/lib/orderStatus";
import { listUsers } from "@/services/admin/adminUserService";
import type { AdminUserSummary } from "@/types/api/adminUser";
import type { Pagination, Role } from "@/types/api/common";

const PAGE_SIZES = [12, 24, 48];
const DEFAULT_LIMIT = 12;

/**
 * /admin/users — danh sách người dùng (FR-14, chỉ Admin): tìm theo email / họ tên, lọc vai trò và trạng thái, phân trang.
 * Bộ lọc, trang và người đang mở (`?user=`) nằm trên URL để F5 / chia sẻ link vẫn giữ. Bấm một dòng mở ngăn kéo chi tiết và sửa.
 */
export function UserListView() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  const q = params.get("q") ?? "";
  const role = (ROLES as string[]).includes(params.get("role") ?? "") ? (params.get("role") as Role) : undefined;
  const activeParam = params.get("isActive");
  const isActive = activeParam === "true" ? true : activeParam === "false" ? false : undefined;
  const page = Math.max(1, Number(params.get("page")) || 1);
  const limit = PAGE_SIZES.includes(Number(params.get("limit"))) ? Number(params.get("limit")) : DEFAULT_LIMIT;
  const selectedId = Number(params.get("user")) > 0 ? Number(params.get("user")) : null;

  const [rows, setRows] = useState<AdminUserSummary[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState(q);

  useEffect(() => setSearch(q), [q]);

  const setQuery = useCallback(
    (patch: Record<string, string | number | boolean | undefined>) => {
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
    listUsers({ q: q || undefined, role, isActive, page, limit })
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
  }, [q, role, isActive, page, limit, setQuery]);

  useEffect(load, [load]);

  const filtered = !!q || !!role || isActive !== undefined;
  const clear = () => setQuery({ q: undefined, role: undefined, isActive: undefined, page: 1 });
  const open = (id: number) => setQuery({ user: id });

  const columns: TableProps<AdminUserSummary>["columns"] = [
    {
      title: "Người dùng",
      key: "user",
      width: 280,
      fixed: "left",
      render: (_, u) => (
        <div>
          <Typography.Link onClick={() => open(u.id)} style={{ fontWeight: 600 }}>
            {u.fullName}
          </Typography.Link>
          <div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {u.email}
            </Typography.Text>
          </div>
        </div>
      ),
    },
    { title: "Số điện thoại", dataIndex: "phone", width: 140, render: (v: string | null) => v ?? <Typography.Text type="secondary">—</Typography.Text> },
    { title: "Vai trò", dataIndex: "role", width: 140, render: (r: Role) => <Tag color={ROLE_LABEL[r].color}>{ROLE_LABEL[r].label}</Tag> },
    {
      title: "Trạng thái",
      dataIndex: "isActive",
      width: 130,
      render: (a: boolean) => (a ? <Tag color="green">Hoạt động</Tag> : <Tag color="red">Đã khoá</Tag>),
    },
    {
      title: "Email",
      dataIndex: "emailVerified",
      width: 140,
      render: (v: boolean) => (v ? <Tag color="green">Đã xác thực</Tag> : <Tag>Chưa xác thực</Tag>),
    },
    { title: "Ngày tạo", dataIndex: "createdAt", width: 160, render: (v: string) => fmtDateTime(v) },
    {
      title: "",
      key: "actions",
      width: 90,
      render: (_, u) => (
        <Button size="small" onClick={() => open(u.id)}>
          Xem / sửa
        </Button>
      ),
    },
  ];

  return (
    <>
      <PageHeader breadcrumb={[{ label: "Hệ thống" }]} title="Người dùng" />
      <Card>
        <div className="cv-filter-bar">
          <Input.Search
            allowClear
            placeholder="Tìm theo email hoặc họ tên"
            className="cv-filter-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={(v) => setQuery({ q: v.trim(), page: 1 })}
          />
          <Select
            allowClear
            placeholder="Vai trò"
            className="cv-filter-item"
            value={role}
            onChange={(v?: Role) => setQuery({ role: v, page: 1 })}
            options={ROLES.map((r) => ({ value: r, label: ROLE_LABEL[r].label }))}
          />
          <Select
            allowClear
            placeholder="Trạng thái"
            className="cv-filter-item"
            value={isActive}
            onChange={(v?: boolean) => setQuery({ isActive: v, page: 1 })}
            options={[
              { value: true, label: "Đang hoạt động" },
              { value: false, label: "Đã khoá" },
            ]}
          />
        </div>

        <div className="cv-table-toolbar">
          <Typography.Text type="secondary">{pagination ? `${pagination.totalItems} người dùng` : ""}</Typography.Text>
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
            message="Không tải được danh sách người dùng"
            description={error}
            action={
              <Button size="small" onClick={load}>
                Thử lại
              </Button>
            }
          />
        ) : (
          <Table<AdminUserSummary>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 1000 }}
            sticky={{ offsetHeader: 64 }}
            onRow={(u) => ({ onDoubleClick: () => open(u.id) })}
            locale={{
              emptyText: (
                <Empty description={filtered ? "Không có người dùng nào khớp bộ lọc." : "Chưa có người dùng nào."}>
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
              showTotal: (t, [a, b]) => `${a}–${b} / ${t} người dùng`,
              onChange: (p, size) => setQuery({ page: size !== limit ? 1 : p, limit: size }),
            }}
          />
        )}
      </Card>

      <UserDrawer userId={selectedId} onClose={() => setQuery({ user: undefined })} onSaved={load} />
    </>
  );
}
