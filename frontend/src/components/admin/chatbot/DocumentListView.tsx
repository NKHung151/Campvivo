"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Card, Dropdown, Empty, Input, Segmented, Space, Switch, Table, Tag, Tooltip, Typography } from "antd";
import type { TableProps } from "antd";
import {
  CloudUploadOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  MoreOutlined,
  ReloadOutlined,
  SwapOutlined,
} from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { getErrorMessage } from "@/lib/errorMessages";
import { listDocuments } from "@/services/admin/adminChatbotService";
import type { ChatbotDocument, ChatbotDocumentStatus } from "@/types/api/chatbot";
import type { Pagination } from "@/types/api/common";
import { DocumentEditModal } from "./DocumentEditModal";
import { DocumentUploadModal } from "./DocumentUploadModal";
import { fileKind, fileSize, fmtDateTime, POLL_MS, StatusTag } from "./documentUi";
import { useDocumentActions } from "./useDocumentActions";

const PAGE_SIZE = 20;
type ActiveFilter = "all" | "on" | "off";

/** /admin/chatbot/documents — tài liệu chatbot dùng để trả lời (FR-28). Tự cập nhật khi còn tài liệu đang xử lý. */
export function DocumentListView() {
  const [rows, setRows] = useState<ChatbotDocument[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState("");
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<ChatbotDocumentStatus | "ALL">("ALL");
  const [active, setActive] = useState<ActiveFilter>("all");
  const [page, setPage] = useState(1);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [replacing, setReplacing] = useState<ChatbotDocument | null>(null);
  const [editing, setEditing] = useState<ChatbotDocument | null>(null);

  const load = useCallback(
    (quiet = false) => {
      if (!quiet) setLoading(true);
      setError(null);
      return listDocuments({
        q: q || undefined,
        status: status === "ALL" ? undefined : status,
        isActive: active === "all" ? undefined : active === "on",
        page,
        limit: PAGE_SIZE,
      })
        .then((res) => {
          setRows(res.data);
          setPagination(res.pagination);
        })
        .catch((e) => {
          if (!quiet) setError(getErrorMessage(e));
        })
        .finally(() => setLoading(false));
    },
    [q, status, active, page],
  );

  useEffect(() => {
    void load();
  }, [load]);

  // Còn tài liệu đang xử lý → hỏi lại định kỳ (không hiện loading)
  const processing = rows.some((r) => r.status === "PROCESSING");
  useEffect(() => {
    if (!processing) return;
    const timer = window.setInterval(() => void load(true), POLL_MS);
    return () => window.clearInterval(timer);
  }, [processing, load]);

  const replaceRow = (doc: ChatbotDocument) => setRows((rs) => rs.map((r) => (r.id === doc.id ? doc : r)));
  const actions = useDocumentActions({ onChanged: replaceRow, onDeleted: () => void load() });

  const columns: TableProps<ChatbotDocument>["columns"] = [
    {
      title: "Tài liệu",
      key: "doc",
      render: (_, d) => (
        <div>
          <Link href={`/admin/chatbot/documents/${d.id}`} className="cv-prod-name" title={d.title}>
            {d.title}
          </Link>
          <Typography.Text type="secondary" className="cv-prod-slug" style={{ display: "block" }}>
            {d.fileName}
          </Typography.Text>
        </div>
      ),
    },
    { title: "Loại", key: "kind", width: 100, render: (_, d) => <Tag bordered={false}>{fileKind(d.fileName)}</Tag> },
    { title: "Dung lượng", dataIndex: "sizeBytes", width: 110, align: "right", render: (v: number) => fileSize(v) },
    {
      title: (
        <Tooltip title="Số đoạn văn bản bot đọc được từ tài liệu">
          <span>Số đoạn</span>
        </Tooltip>
      ),
      dataIndex: "chunkCount",
      width: 90,
      align: "right",
    },
    { title: "Xử lý", key: "status", width: 130, render: (_, d) => <StatusTag doc={d} /> },
    {
      title: "Bot dùng",
      key: "active",
      width: 100,
      render: (_, d) => (
        <Switch
          size="small"
          checked={d.isActive}
          onChange={(v) => void actions.toggle(d, v)}
          aria-label={d.isActive ? `Tắt ${d.title}` : `Bật ${d.title}`}
        />
      ),
    },
    { title: "Cập nhật", dataIndex: "updatedAt", width: 150, render: (v: string) => fmtDateTime(v) },
    {
      key: "actions",
      width: 56,
      fixed: "right",
      render: (_, d) => (
        <Dropdown
          trigger={["click"]}
          placement="bottomRight"
          menu={{
            items: [
              { key: "view", icon: <EyeOutlined />, label: <Link href={`/admin/chatbot/documents/${d.id}`}>Xem chi tiết</Link> },
              { key: "edit", icon: <EditOutlined />, label: "Sửa thông tin", onClick: () => setEditing(d) },
              { key: "replace", icon: <SwapOutlined />, label: "Thay file", disabled: d.status === "PROCESSING", onClick: () => setReplacing(d) },
              { key: "reindex", icon: <ReloadOutlined />, label: "Nạp lại", disabled: d.status === "PROCESSING", onClick: () => void actions.reindex(d) },
              { key: "download", icon: <DownloadOutlined />, label: "Tải file gốc", onClick: () => void actions.download(d) },
              { type: "divider" },
              { key: "delete", icon: <DeleteOutlined />, label: "Xoá", danger: true, onClick: () => actions.remove(d) },
            ],
          }}
        >
          <Button type="text" icon={<MoreOutlined />} aria-label={`Thao tác với ${d.title}`} />
        </Dropdown>
      ),
    },
  ];

  const filtered = Boolean(q || status !== "ALL" || active !== "all");
  const clear = () => {
    setQ("");
    setSearch("");
    setStatus("ALL");
    setActive("all");
    setPage(1);
  };

  return (
    <>
      <PageHeader
        breadcrumb={[{ label: "Chatbot AI" }]}
        title="Tài liệu cho chatbot"
        tags={pagination ? <Tag bordered={false}>{pagination.totalItems}</Tag> : null}
        actions={
          <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => setUploadOpen(true)}>
            Tải tài liệu lên
          </Button>
        }
      />
      <Card>
        <Typography.Paragraph type="secondary">
          Chatbot chỉ trả lời chính sách, hướng dẫn, kiến thức dựa trên các tài liệu đang <b>bật</b> và đã{" "}
          <b>xử lý xong</b> ở đây (kèm tên tài liệu khi trích dẫn). Giá và tồn kho sản phẩm bot tự tra trong cửa hàng.
        </Typography.Paragraph>
        <div className="cv-filter-bar">
          <Input.Search
            allowClear
            placeholder="Tìm theo tiêu đề hoặc tên file"
            className="cv-filter-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={(v) => {
              setQ(v.trim());
              setPage(1);
            }}
          />
        </div>
        <div className="cv-table-toolbar">
          <Space wrap>
            <Segmented
              value={status}
              onChange={(v) => {
                setStatus(v as ChatbotDocumentStatus | "ALL");
                setPage(1);
              }}
              options={[
                { value: "ALL", label: "Mọi trạng thái" },
                { value: "READY", label: "Sẵn sàng" },
                { value: "PROCESSING", label: "Đang xử lý" },
                { value: "FAILED", label: "Lỗi" },
              ]}
            />
            <Segmented
              value={active}
              onChange={(v) => {
                setActive(v as ActiveFilter);
                setPage(1);
              }}
              options={[
                { value: "all", label: "Tất cả" },
                { value: "on", label: "Đang bật" },
                { value: "off", label: "Đã tắt" },
              ]}
            />
          </Space>
          <Space wrap>
            {filtered && <Button onClick={clear}>Xoá bộ lọc</Button>}
            <Tooltip title="Tải lại">
              <Button icon={<ReloadOutlined />} onClick={() => void load()} aria-label="Tải lại" />
            </Tooltip>
          </Space>
        </div>

        {error ? (
          <Alert
            type="error"
            showIcon
            message="Không tải được danh sách tài liệu"
            description={error}
            action={
              <Button size="small" onClick={() => void load()}>
                Thử lại
              </Button>
            }
          />
        ) : (
          <Table<ChatbotDocument>
            rowKey="id"
            size="middle"
            columns={columns}
            dataSource={rows}
            loading={loading}
            scroll={{ x: 960 }}
            rowClassName={(d) => (d.isActive ? "" : "cv-row-inactive")}
            locale={{
              emptyText: (
                <Empty description={filtered ? "Không có tài liệu nào khớp bộ lọc." : "Chưa có tài liệu nào."}>
                  {filtered ? (
                    <Button onClick={clear}>Xoá bộ lọc</Button>
                  ) : (
                    <Button type="primary" onClick={() => setUploadOpen(true)}>
                      Tải tài liệu đầu tiên
                    </Button>
                  )}
                </Empty>
              ),
            }}
            pagination={{
              current: pagination?.currentPage ?? page,
              pageSize: PAGE_SIZE,
              total: pagination?.totalItems ?? 0,
              showTotal: (t, [from, to]) => `${from}–${to} / ${t} tài liệu`,
              onChange: setPage,
            }}
          />
        )}
      </Card>

      <DocumentUploadModal
        open={uploadOpen || replacing !== null}
        replace={replacing}
        onClose={() => {
          setUploadOpen(false);
          setReplacing(null);
        }}
        onDone={(doc) => {
          setUploadOpen(false);
          setReplacing(null);
          if (replacing) replaceRow(doc);
          else void load();
        }}
      />
      <DocumentEditModal
        doc={editing}
        onClose={() => setEditing(null)}
        onSaved={(doc) => {
          setEditing(null);
          replaceRow(doc);
        }}
      />
    </>
  );
}
