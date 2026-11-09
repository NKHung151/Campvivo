"use client";

import { useLayoutEffect, useRef, useState } from "react";
import "@/styles/chat.css";
import { MAX_MESSAGE_LENGTH, normalizeContent } from "@/lib/support";
import { fmtDateTime } from "@/lib/orderStatus";
import { getErrorMessage } from "@/lib/errorMessages";
import type { SupportMessage } from "@/types/api/support";
import type { SocketState } from "@/components/features/support/useSupportSocket";

interface Props {
  /** Cũ → mới. */
  messages: SupportMessage[];
  /** true nếu người xem là nhân viên: tin `isFromStaff` là "của mình". */
  viewerIsStaff: boolean;
  loading: boolean;
  /** Lỗi tải hội thoại (không phải lỗi gửi). */
  error: string | null;
  onRetry: () => void;
  hasMore: boolean;
  loadingOlder: boolean;
  onLoadOlder: () => void;
  /** Gửi tin; ném lỗi (ApiError) để khung giữ nguyên nội dung đang gõ và báo lỗi. */
  onSend: (content: string) => Promise<void>;
  connection: SocketState;
  emptyText: string;
}

const CONNECTION_LABEL: Record<SocketState, string> = {
  online: "Đang kết nối trực tiếp",
  connecting: "Đang kết nối…",
  offline: "Mất kết nối trực tiếp, đang thử lại. Tin vẫn gửi được.",
};

/** Khung chat dùng chung: danh sách tin cuộn được + ô nhập. Hiển thị mọi nội dung dưới dạng text thuần. */
export function ChatPanel(p: Props) {
  const listRef = useRef<HTMLDivElement>(null);
  const prev = useRef<{ firstId: number | null; lastId: number | null; height: number; nearBottom: boolean }>({
    firstId: null,
    lastId: null,
    height: 0,
    nearBottom: true,
  });
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);

  const first = p.messages[0]?.id ?? null;
  const last = p.messages[p.messages.length - 1]?.id ?? null;
  const lastMine = p.messages.length > 0 && p.messages[p.messages.length - 1].isFromStaff === p.viewerIsStaff;

  // Cuộn: lần đầu và khi có tin mới ở cuối (đang ở gần đáy hoặc chính mình gửi) → xuống đáy; khi tải tin CŨ chèn lên đầu →
  // giữ nguyên vị trí đang đọc thay vì bị đẩy lên.
  useLayoutEffect(() => {
    const el = listRef.current;
    if (!el) return;
    const before = prev.current;
    if (before.lastId === null) {
      el.scrollTop = el.scrollHeight;
    } else if (first !== null && before.firstId !== null && first < before.firstId) {
      el.scrollTop += el.scrollHeight - before.height;
    } else if (last !== before.lastId && (before.nearBottom || lastMine)) {
      el.scrollTop = el.scrollHeight;
    }
    prev.current = { firstId: first, lastId: last, height: el.scrollHeight, nearBottom: before.nearBottom };
  }, [first, last, lastMine]);

  function onScroll() {
    const el = listRef.current;
    if (el) prev.current.nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
  }

  const content = normalizeContent(draft);
  const tooLong = draft.trim().length > MAX_MESSAGE_LENGTH;
  const canSend = content !== null && !sending;

  async function submit() {
    if (!canSend || content === null) return;
    setSending(true);
    setSendError(null);
    try {
      await p.onSend(content);
      setDraft("");
    } catch (e) {
      setSendError(getErrorMessage(e));
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="cv-chat">
      <div className="cv-chat__status" role="status">
        <span className={`cv-chat__dot is-${p.connection}`} aria-hidden />
        {CONNECTION_LABEL[p.connection]}
      </div>

      <div className="cv-chat__list" ref={listRef} onScroll={onScroll}>
        {p.loading ? (
          <div className="cv-chat__notice">Đang tải tin nhắn…</div>
        ) : p.error ? (
          <div className="cv-chat__notice is-error">
            {p.error}{" "}
            <button type="button" className="cv-chat__more" onClick={p.onRetry}>
              Thử lại
            </button>
          </div>
        ) : (
          <>
            {p.hasMore && (
              <button type="button" className="cv-chat__more" onClick={p.onLoadOlder} disabled={p.loadingOlder}>
                {p.loadingOlder ? "Đang tải…" : "Xem tin cũ hơn"}
              </button>
            )}
            {p.messages.length === 0 && <div className="cv-chat__empty">{p.emptyText}</div>}
            {p.messages.map((m) => (
              <div key={m.id} className={`cv-msg ${m.isFromStaff === p.viewerIsStaff ? "is-mine" : "is-theirs"}`}>
                <div className="cv-msg__bubble">{m.content}</div>
                <div className="cv-msg__meta">
                  {m.isFromStaff ? "Nhân viên · " : ""}
                  {fmtDateTime(m.createdAt)}
                </div>
              </div>
            ))}
          </>
        )}
      </div>

      <div className="cv-chat__composer">
        <div className="cv-chat__row">
          <textarea
            className="cv-chat__input"
            rows={2}
            value={draft}
            placeholder="Nhập tin nhắn… (Enter để gửi, Shift+Enter xuống dòng)"
            aria-label="Nội dung tin nhắn"
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              // isComposing: đang gõ dấu tiếng Việt (Telex/VNI) thì Enter chốt từ, không được gửi.
              if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
                e.preventDefault();
                void submit();
              }
            }}
          />
          <button type="button" className="cv-chat__send" disabled={!canSend} onClick={() => void submit()}>
            {sending ? "Đang gửi…" : "Gửi"}
          </button>
        </div>
        <div className="cv-chat__hint">
          <span className={sendError || tooLong ? "is-error" : ""}>
            {sendError ?? (tooLong ? `Tối đa ${MAX_MESSAGE_LENGTH} ký tự.` : "")}
          </span>
          <span className={tooLong ? "is-error" : ""}>
            {draft.trim().length}/{MAX_MESSAGE_LENGTH}
          </span>
        </div>
      </div>
    </div>
  );
}
