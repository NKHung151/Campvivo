"use client";

import { useState } from "react";
import { Alert, Card, Empty, Input, List, Progress, Space, Tag, Typography } from "antd";
import { getErrorMessage } from "@/lib/errorMessages";
import { searchTest } from "@/services/admin/adminChatbotService";
import type { RetrievalHit } from "@/types/api/chatbot";

/**
 * Admin hỏi thử để xem bot tìm được đoạn tài liệu nào (trên mọi tài liệu đang bật) — không gọi model chat nên không
 * tốn lượt trả lời. `highlightDocumentId`: đánh dấu đoạn thuộc tài liệu đang xem.
 */
export function RetrievalTester({ highlightDocumentId }: { highlightDocumentId?: number }) {
  const [hits, setHits] = useState<RetrievalHit[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (question: string) => {
    if (!question.trim()) return;
    setLoading(true);
    setError(null);
    try {
      setHits(await searchTest(question.trim()));
    } catch (e) {
      setError(getErrorMessage(e));
      setHits(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card title="Hỏi thử" size="small">
      <Typography.Paragraph type="secondary">
        Nhập câu khách có thể hỏi để xem chatbot tìm được những đoạn nào trong các tài liệu đang bật.
      </Typography.Paragraph>
      <Input.Search placeholder="Vd. Đổi trả trong bao nhiêu ngày?" enterButton="Thử" loading={loading} onSearch={run} />
      {error && <Alert type="error" showIcon message={error} style={{ marginTop: 12 }} />}
      {hits !== null && (
        <List
          style={{ marginTop: 12 }}
          dataSource={hits}
          locale={{ emptyText: <Empty description="Không tìm được đoạn nào đủ liên quan — bot sẽ trả lời là chưa có thông tin." /> }}
          renderItem={(h) => (
            <List.Item>
              <div style={{ width: "100%" }}>
                <Space size={8} wrap>
                  <Typography.Text strong>{h.title}</Typography.Text>
                  {h.page !== null && <Tag bordered={false}>Trang {h.page}</Tag>}
                  {h.documentId === highlightDocumentId && <Tag color="blue">Tài liệu này</Tag>}
                  <Progress percent={Math.round(h.score * 100)} size="small" style={{ width: 140, margin: 0 }} />
                </Space>
                <Typography.Paragraph type="secondary" ellipsis={{ rows: 3, expandable: true, symbol: "Xem thêm" }} style={{ marginTop: 6, marginBottom: 0 }}>
                  {h.content}
                </Typography.Paragraph>
              </div>
            </List.Item>
          )}
        />
      )}
    </Card>
  );
}
