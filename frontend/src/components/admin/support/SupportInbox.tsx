"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { Alert, Button, Empty, Spin, Tag, Typography } from "antd";
import { ArrowLeftOutlined, ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { ChatPanel } from "@/components/features/support/ChatPanel";
import { useConversationMessages } from "@/components/features/support/useConversationMessages";
import { useSupportSocket } from "@/components/features/support/useSupportSocket";
import { getErrorMessage } from "@/lib/errorMessages";
import { fmtDateTime } from "@/lib/orderStatus";
import { listConversations, listMessages, reply } from "@/services/admin/adminSupportService";
import type { SupportConversation, SupportMessage } from "@/types/api/support";

const PAGE_LIMIT = 20;

/** Đưa hội thoại có tin vừa tới lên đầu, thay tin cuối; khách chưa có trong danh sách đã tải thì trả null để người gọi tải lại. */
function bumpConversation(items: SupportConversation[], message: SupportMessage): SupportConversation[] | null {
  const found = items.find((c) => c.customer.id === message.customerId);
  if (!found) return null;
  return [{ ...found, lastMessage: message }, ...items.filter((c) => c !== found)];
}

/**
 * /admin/support — hộp thư hỗ trợ khách hàng (FR-26, EMPLOYEE và ADMIN). Cột trái: hội thoại, tin mới nhất đứng đầu; cột phải: tin của
 * khách đang chọn (`?customer=` trên URL để F5 / chia sẻ link vẫn giữ). Tin của khách đến ngay qua WebSocket; trả lời gửi bằng REST.
 * Không có "chưa đọc" (KI-02): hội thoại có tin cuối là của khách được gắn nhãn "Chờ trả lời".
 */
export function SupportInbox() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const selectedId = Number(params.get("customer")) > 0 ? Number(params.get("customer")) : null;

  const [items, setItems] = useState<SupportConversation[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadFirstPage = useCallback((silent = false) => {
    if (!silent) setLoading(true);
    setError(null);
    listConversations({ page: 1, limit: PAGE_LIMIT })
      .then((res) => {
        setItems(res.data);
        setPage(1);
        setTotalPages(res.pagination.totalPages);
      })
      .catch((e) => {
        if (!silent) setError(getErrorMessage(e));
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => loadFirstPage(), [loadFirstPage]);

  const loadMore = () => {
    setLoadingMore(true);
    listConversations({ page: page + 1, limit: PAGE_LIMIT })
      .then((res) => {
        // Tin mới trong lúc cuộn có thể đẩy một hội thoại sang trang sau: bỏ bản trùng.
        setItems((cur) => [...cur, ...res.data.filter((c) => !cur.some((x) => x.customer.id === c.customer.id))]);
        setPage(res.pagination.currentPage);
        setTotalPages(res.pagination.totalPages);
      })
      .catch((e) => setError(getErrorMessage(e)))
      .finally(() => setLoadingMore(false));
  };

  const conv = useConversationMessages(
    selectedId ? (query) => listMessages(selectedId, query) : null,
    selectedId,
  );
  const { add, refresh } = conv;

  const selectedRef = useRef(selectedId);
  selectedRef.current = selectedId;

  const noteMessage = useCallback(
    (message: SupportMessage) => {
      setItems((cur) => {
        const next = bumpConversation(cur, message);
        if (next === null) {
          loadFirstPage(true); // khách mới nhắn lần đầu: cần tên và email nên tải lại danh sách
          return cur;
        }
        return next;
      });
    },
    [loadFirstPage],
  );

  const connection = useSupportSocket({
    enabled: true,
    onMessage: (message) => {
      noteMessage(message);
      if (message.customerId === selectedRef.current) add(message);
    },
    onConnected: () => {
      loadFirstPage(true);
      refresh();
    },
  });

  const select = (id: number | null) => {
    const next = new URLSearchParams(params.toString());
    if (id === null) next.delete("customer");
    else next.set("customer", String(id));
    const qs = next.toString();
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  };

  const send = async (content: string) => {
    if (selectedId === null) return;
    const saved = await reply(selectedId, content);
    add(saved);
    noteMessage(saved);
  };

  const selected = items.find((c) => c.customer.id === selectedId)?.customer;

  return (
    <>
      <PageHeader
        breadcrumb={[{ label: "Bán hàng" }]}
        title="Hỗ trợ khách hàng"
        actions={<Button icon={<ReloadOutlined />} onClick={() => loadFirstPage()} aria-label="Tải lại danh sách" />}
      />
      <div className={`cv-inbox${selectedId ? " has-selection" : ""}`}>
        <aside className="cv-inbox__list">
          {loading ? (
            <div style={{ padding: 32, textAlign: "center" }}>
              <Spin />
            </div>
          ) : error && items.length === 0 ? (
            <Alert
              type="error"
              showIcon
              message="Không tải được danh sách hội thoại"
              description={error}
              action={
                <Button size="small" onClick={() => loadFirstPage()}>
                  Thử lại
                </Button>
              }
            />
          ) : items.length === 0 ? (
            <Empty description="Chưa có khách nào nhắn tin." />
          ) : (
            <>
              {items.map((c) => (
                <button
                  key={c.customer.id}
                  type="button"
                  className={`cv-inbox__item${c.customer.id === selectedId ? " is-active" : ""}`}
                  onClick={() => select(c.customer.id)}
                >
                  <span className="cv-inbox__name">
                    <Typography.Text strong ellipsis style={{ maxWidth: 170 }}>
                      {c.customer.fullName}
                    </Typography.Text>
                    {!c.lastMessage.isFromStaff && <Tag color="orange">Chờ trả lời</Tag>}
                  </span>
                  <span className="cv-inbox__preview">
                    {c.lastMessage.isFromStaff ? "Bạn: " : ""}
                    {c.lastMessage.content}
                  </span>
                  <span className="cv-inbox__time">{fmtDateTime(c.lastMessage.createdAt)}</span>
                </button>
              ))}
              {page < totalPages && (
                <Button block loading={loadingMore} onClick={loadMore}>
                  Xem thêm hội thoại
                </Button>
              )}
            </>
          )}
        </aside>

        <section className="cv-inbox__thread">
          {selectedId === null ? (
            <div className="cv-inbox__placeholder">
              <Empty description="Chọn một hội thoại để xem và trả lời." />
            </div>
          ) : (
            <>
              <div className="cv-inbox__head">
                <Button className="cv-inbox__back" icon={<ArrowLeftOutlined />} onClick={() => select(null)} aria-label="Về danh sách" />
                <div>
                  <Typography.Text strong>{selected?.fullName ?? `Khách #${selectedId}`}</Typography.Text>
                  {selected && (
                    <div>
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {selected.email}
                      </Typography.Text>
                    </div>
                  )}
                </div>
              </div>
              <ChatPanel
                messages={conv.messages}
                viewerIsStaff
                loading={conv.loading}
                error={conv.error}
                onRetry={conv.reload}
                hasMore={conv.hasMore}
                loadingOlder={conv.loadingOlder}
                onLoadOlder={conv.loadOlder}
                onSend={send}
                connection={connection}
                emptyText="Hội thoại chưa có tin nhắn."
              />
            </>
          )}
        </section>
      </div>
    </>
  );
}
