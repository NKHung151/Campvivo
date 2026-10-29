"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Card, Col, Descriptions, Empty, List, Result, Row, Skeleton, Space, Switch, Tag, Typography } from "antd";
import { DeleteOutlined, DownloadOutlined, EditOutlined, ReloadOutlined, SwapOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { getErrorMessage } from "@/lib/errorMessages";
import { isApiError } from "@/services/apiError";
import { getDocument } from "@/services/admin/adminChatbotService";
import type { ChatbotDocument, ChatbotDocumentDetail } from "@/types/api/chatbot";
import { DocumentEditModal } from "./DocumentEditModal";
import { DocumentUploadModal } from "./DocumentUploadModal";
import { RetrievalTester } from "./RetrievalTester";
import { fileKind, fileSize, fmtDateTime, POLL_MS, StatusTag } from "./documentUi";
import { useDocumentActions } from "./useDocumentActions";

const LIST = "/admin/chatbot/documents";

/** /admin/chatbot/documents/[id] — thông tin, các đoạn bot đọc được, hỏi thử. */
export function DocumentDetailView({ id }: { id: number }) {
  const router = useRouter();
  const [doc, setDoc] = useState<ChatbotDocumentDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<{ message: string; notFound: boolean } | null>(null);
  const [editing, setEditing] = useState(false);
  const [replacing, setReplacing] = useState(false);

  const load = useCallback(
    (quiet = false) => {
      if (!quiet) setLoading(true);
      return getDocument(id)
        .then((d) => {
          setDoc(d);
          setError(null);
        })
        .catch((e) => {
          if (!quiet) setError({ message: getErrorMessage(e), notFound: isApiError(e) && e.status === 404 });
        })
        .finally(() => setLoading(false));
    },
    [id],
  );

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (doc?.status !== "PROCESSING") return;
    const timer = window.setInterval(() => void load(true), POLL_MS);
    return () => window.clearInterval(timer);
  }, [doc?.status, load]);

  // Sau thao tác: lấy lại chi tiết (kèm các đoạn mới)
  const changed = (updated: ChatbotDocument) => {
    setDoc((d) => (d ? { ...d, ...updated } : d));
    void load(true);
  };
  const actions = useDocumentActions({ onChanged: changed, onDeleted: () => router.push(LIST) });

  if (loading && !doc) {
    return (
      <Card>
        <Skeleton active />
      </Card>
    );
  }
  if (error && !doc) {
    return error.notFound ? (
      <Result status="404" title="Không tìm thấy tài liệu" extra={<Button onClick={() => router.push(LIST)}>Về danh sách</Button>} />
    ) : (
      <Alert type="error" showIcon message="Không tải được tài liệu" description={error.message} action={<Button onClick={() => void load()}>Thử lại</Button>} />
    );
  }
  if (!doc) return null;

  const processing = doc.status === "PROCESSING";

  return (
    <>
      <PageHeader
        breadcrumb={[{ label: "Chatbot AI" }, { label: "Tài liệu", href: LIST }]}
        title={doc.title}
        tags={
          <Space size={6}>
            <StatusTag doc={doc} />
            {!doc.isActive && <Tag>Đã tắt</Tag>}
          </Space>
        }
        actions={
          <>
            <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>
              Sửa
            </Button>
            <Button icon={<SwapOutlined />} disabled={processing} onClick={() => setReplacing(true)}>
              Thay file
            </Button>
            <Button icon={<ReloadOutlined />} disabled={processing} onClick={() => void actions.reindex(doc)}>
              Nạp lại
            </Button>
            <Button icon={<DownloadOutlined />} onClick={() => void actions.download(doc)}>
              Tải file gốc
            </Button>
            <Button danger icon={<DeleteOutlined />} onClick={() => actions.remove(doc)}>
              Xoá
            </Button>
          </>
        }
      />
      {doc.status === "FAILED" && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Xử lý tài liệu lỗi — chatbot chưa dùng được tài liệu này"
          description={doc.errorMessage}
        />
      )}
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card title="Thông tin" size="small">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="File">{doc.fileName}</Descriptions.Item>
              <Descriptions.Item label="Loại / dung lượng">
                {fileKind(doc.fileName)} · {fileSize(doc.sizeBytes)}
              </Descriptions.Item>
              <Descriptions.Item label="Mô tả">{doc.description || <Typography.Text type="secondary">—</Typography.Text>}</Descriptions.Item>
              <Descriptions.Item label="Chatbot dùng">
                <Switch size="small" checked={doc.isActive} onChange={(v) => void actions.toggle(doc, v)} />
              </Descriptions.Item>
              <Descriptions.Item label="Tải lên">{fmtDateTime(doc.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="Cập nhật">{fmtDateTime(doc.updatedAt)}</Descriptions.Item>
            </Descriptions>
          </Card>
          <Card
            title={`Nội dung bot đọc được (${doc.chunkCount} đoạn)`}
            size="small"
            style={{ marginTop: 16 }}
            extra={doc.chunkCount > doc.chunks.length ? <Typography.Text type="secondary">Hiện {doc.chunks.length} đoạn đầu</Typography.Text> : null}
          >
            {processing ? (
              <Skeleton active paragraph={{ rows: 4 }} />
            ) : (
              <List
                dataSource={doc.chunks}
                locale={{ emptyText: <Empty description="Chưa có đoạn nào." /> }}
                renderItem={(c) => (
                  <List.Item>
                    <div style={{ width: "100%" }}>
                      <Space size={6}>
                        <Tag bordered={false}>#{c.index + 1}</Tag>
                        {c.page !== null && <Tag bordered={false}>Trang {c.page}</Tag>}
                      </Space>
                      <Typography.Paragraph ellipsis={{ rows: 4, expandable: true, symbol: "Xem thêm" }} style={{ marginTop: 6, marginBottom: 0, whiteSpace: "pre-line" }}>
                        {c.content}
                      </Typography.Paragraph>
                    </div>
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <RetrievalTester highlightDocumentId={doc.id} />
        </Col>
      </Row>

      <DocumentEditModal
        doc={editing ? doc : null}
        onClose={() => setEditing(false)}
        onSaved={(d) => {
          setEditing(false);
          changed(d);
        }}
      />
      <DocumentUploadModal
        open={replacing}
        replace={doc}
        onClose={() => setReplacing(false)}
        onDone={(d) => {
          setReplacing(false);
          changed(d);
        }}
      />
    </>
  );
}
