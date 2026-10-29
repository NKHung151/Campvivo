"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { getErrorMessage } from "@/lib/errorMessages";
import { isApiError } from "@/services/apiError";
import {
  createConversation,
  deleteConversation,
  getMessages,
  listConversations,
  sendMessage,
  streamError,
} from "@/services/chatbotService";
import type { ChatCard, ChatConversation, ChatMessage, ChatSource } from "@/types/api/chatbot";
import { ChatMessageView } from "./ChatMessageView";

/** Khớp app.chatbot.message-max-length (chatbot-service, mặc định 1000). */
const MAX_LENGTH = 1000;

const SUGGESTIONS = [
  "Lều 2 người dưới 3 triệu có mẫu nào?",
  "Nhà mình 4 người đi cắm trại 2 đêm, ngân sách khoảng 10 triệu thì nên mua gì?",
  "Chính sách đổi trả của cửa hàng thế nào?",
  "Đi leo núi trong ngày cần chuẩn bị những gì?",
];

interface Pending {
  content: string;
  cards: ChatCard[];
  sources: ChatSource[];
}

/** Khung chat với trợ lý (FR-28) — chỉ hiển thị cho CUSTOMER đã đăng nhập (ChatLauncher kiểm). */
export function ChatPanel({ onClose }: { onClose: () => void }) {
  const [conversations, setConversations] = useState<ChatConversation[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pending, setPending] = useState<Pending | null>(null);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastFailed, setLastFailed] = useState<string | null>(null);
  const [showList, setShowList] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);

  const openConversation = useCallback(async (id: string) => {
    setActiveId(id);
    setShowList(false);
    setError(null);
    setLoading(true);
    try {
      setMessages(await getMessages(id));
    } catch (e) {
      setError(getErrorMessage(e));
      setMessages([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // Mở panel: lấy danh sách hội thoại, mở hội thoại gần nhất
  useEffect(() => {
    let alive = true;
    listConversations()
      .then((page) => {
        if (!alive) return;
        setConversations(page.data);
        if (page.data.length > 0) void openConversation(page.data[0].id);
        else setLoading(false);
      })
      .catch((e) => {
        if (!alive) return;
        setError(getErrorMessage(e));
        setLoading(false);
      });
    return () => {
      alive = false;
      abortRef.current?.abort();
    };
  }, [openConversation]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: "end" });
  }, [messages, pending]);

  const startNew = () => {
    abortRef.current?.abort();
    setActiveId(null);
    setMessages([]);
    setPending(null);
    setError(null);
    setShowList(false);
    inputRef.current?.focus();
  };

  const remove = async (c: ChatConversation) => {
    if (!window.confirm(`Xoá cuộc trò chuyện "${c.title}"?`)) return;
    try {
      await deleteConversation(c.id);
      setConversations((cs) => cs.filter((x) => x.id !== c.id));
      if (c.id === activeId) startNew();
    } catch (e) {
      setError(getErrorMessage(e));
    }
  };

  const send = async (text: string) => {
    const content = text.trim();
    if (!content || sending) return;
    if (content.length > MAX_LENGTH) {
      setError(`Tin nhắn tối đa ${MAX_LENGTH} ký tự.`);
      return;
    }
    setSending(true);
    setError(null);
    setLastFailed(null);
    setInput("");
    const controller = new AbortController();
    abortRef.current = controller;

    let conversationId = activeId;
    try {
      if (!conversationId) {
        const created = await createConversation();
        conversationId = created.id;
        setActiveId(created.id);
        setConversations((cs) => [created, ...cs]);
      }
      const userMessage: ChatMessage = {
        id: -Date.now(),
        role: "USER",
        content,
        cards: [],
        sources: [],
        createdAt: new Date().toISOString(),
      };
      setMessages((ms) => [...ms, userMessage]);
      const draft: Pending = { content: "", cards: [], sources: [] };
      setPending({ ...draft });

      let failed: string | null = null;
      let doneId: number | null = null;
      await sendMessage(
        conversationId,
        content,
        (e) => {
          if (e.type === "delta") draft.content += e.text;
          else if (e.type === "cards") draft.cards = e.cards;
          else if (e.type === "sources") draft.sources = e.sources;
          else if (e.type === "done") doneId = e.messageId;
          else if (e.type === "error") failed = getErrorMessage(streamError(e));
          setPending({ ...draft });
        },
        controller.signal,
      );

      if (failed || doneId === null) {
        setError(failed ?? "Trợ lý chưa trả lời xong. Vui lòng thử lại.");
        setLastFailed(content);
      } else {
        const id: number = doneId;
        setMessages((ms) => [
          ...ms,
          { id, role: "ASSISTANT", content: draft.content, cards: draft.cards, sources: draft.sources, createdAt: new Date().toISOString() },
        ]);
        const title = content.length > 60 ? `${content.slice(0, 59)}…` : content;
        setConversations((cs) => {
          const current = cs.find((c) => c.id === conversationId);
          const rest = cs.filter((c) => c.id !== conversationId);
          return current ? [{ ...current, title: current.title === "Cuộc trò chuyện mới" ? title : current.title }, ...rest] : cs;
        });
      }
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") return;
      // Lỗi trước khi bot trả lời (hết lượt, quá dài...): bỏ tin vừa thêm, trả lại chữ vào ô nhập
      setMessages((ms) => ms.filter((m) => m.id > 0));
      setInput(content);
      setError(getErrorMessage(e));
      if (isApiError(e) && e.status === 0) setLastFailed(content);
    } finally {
      setPending(null);
      setSending(false);
      abortRef.current = null;
    }
  };

  const retry = () => {
    if (!lastFailed) return;
    const text = lastFailed;
    // Tin lỗi đã được lưu ở server: hiển thị lại sau khi bot trả lời
    setMessages((ms) => (ms.length > 0 && ms[ms.length - 1].role === "USER" && ms[ms.length - 1].content === text ? ms.slice(0, -1) : ms));
    void send(text);
  };

  const empty = !loading && messages.length === 0 && !pending;

  return (
    <div className="cv-chat-panel" role="dialog" aria-label="Trợ lý Campvivo">
      <header className="cv-chat-head">
        <button type="button" className="cv-chat-icon-btn" onClick={() => setShowList((v) => !v)} aria-label="Danh sách cuộc trò chuyện" aria-expanded={showList}>
          ☰
        </button>
        <div className="cv-chat-title">
          <strong>Trợ lý Campvivo</strong>
          <span>Tư vấn đồ dã ngoại, combo, chính sách</span>
        </div>
        <button type="button" className="cv-chat-icon-btn" onClick={startNew} aria-label="Cuộc trò chuyện mới" title="Cuộc trò chuyện mới">
          ＋
        </button>
        <button type="button" className="cv-chat-icon-btn" onClick={onClose} aria-label="Đóng">
          ✕
        </button>
      </header>

      {showList && (
        <nav className="cv-chat-list" aria-label="Cuộc trò chuyện">
          {conversations.length === 0 ? (
            <p className="cv-chat-muted">Chưa có cuộc trò chuyện nào.</p>
          ) : (
            conversations.map((c) => (
              <div key={c.id} className={`cv-chat-list-item${c.id === activeId ? " is-active" : ""}`}>
                <button type="button" onClick={() => void openConversation(c.id)} title={c.title}>
                  {c.title}
                </button>
                <button type="button" className="cv-chat-icon-btn" onClick={() => void remove(c)} aria-label={`Xoá ${c.title}`}>
                  🗑
                </button>
              </div>
            ))
          )}
        </nav>
      )}

      <div className="cv-chat-body">
        {loading && <p className="cv-chat-muted">Đang tải…</p>}
        {empty && (
          <div className="cv-chat-welcome">
            <p>
              Xin chào! Mình có thể tìm sản phẩm theo tầm giá, tư vấn combo theo số người và ngân sách, hoặc giải đáp chính sách
              của cửa hàng.
            </p>
            <div className="cv-chat-suggest">
              {SUGGESTIONS.map((s) => (
                <button key={s} type="button" onClick={() => void send(s)} disabled={sending}>
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}
        {messages.map((m) => (
          <ChatMessageView key={m.id} role={m.role} content={m.content} cards={m.cards} sources={m.sources} />
        ))}
        {pending && <ChatMessageView role="ASSISTANT" content={pending.content} cards={pending.cards} sources={pending.sources} streaming />}
        {error && (
          <div className="cv-chat-error" role="alert">
            {error}
            {lastFailed && (
              <button type="button" onClick={retry} disabled={sending}>
                Thử lại
              </button>
            )}
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      <form
        className="cv-chat-input"
        onSubmit={(e) => {
          e.preventDefault();
          void send(input);
        }}
      >
        <textarea
          ref={inputRef}
          value={input}
          rows={1}
          maxLength={MAX_LENGTH}
          placeholder="Nhập câu hỏi… (Enter để gửi)"
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault();
              void send(input);
            }
          }}
          disabled={sending}
          aria-label="Câu hỏi cho trợ lý"
        />
        {sending ? (
          <button type="button" onClick={() => abortRef.current?.abort()} className="cv-chat-send">
            Dừng
          </button>
        ) : (
          <button type="submit" className="cv-chat-send" disabled={!input.trim()}>
            Gửi
          </button>
        )}
      </form>
      <p className="cv-chat-foot">Trợ lý AI có thể nhầm lẫn. Giá và tồn kho theo thẻ sản phẩm hiển thị.</p>
    </div>
  );
}
