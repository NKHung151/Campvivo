"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { getErrorMessage } from "@/lib/errorMessages";
import { mergeMessages, PAGE_SIZE } from "@/lib/support";
import type { SupportMessage, SupportMessagesQuery } from "@/types/api/support";

/**
 * Trạng thái tin của MỘT hội thoại (khách xem hội thoại của mình, nhân viên xem hội thoại đang chọn). `load` trả tin MỚI NHẤT TRƯỚC
 * theo api.md 4.8; hook đảo thành cũ → mới và gộp bỏ trùng theo id. `key` đổi (nhân viên chọn khách khác) → bỏ hết và tải lại; kết quả
 * của hội thoại cũ về muộn bị bỏ, không lẫn vào hội thoại mới.
 */
export function useConversationMessages(load: ((query: SupportMessagesQuery) => Promise<SupportMessage[]>) | null, key: unknown) {
  const [messages, setMessages] = useState<SupportMessage[]>([]);
  const [loading, setLoading] = useState(load !== null);
  const [error, setError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);

  const loadRef = useRef(load);
  loadRef.current = load;
  const generation = useRef(0); // tăng mỗi lần đổi hội thoại để loại phản hồi cũ
  const messagesRef = useRef<SupportMessage[]>([]);
  messagesRef.current = messages;

  const reload = useCallback(() => {
    const fetchPage = loadRef.current;
    const gen = ++generation.current;
    setMessages([]);
    setHasMore(false);
    setError(null);
    if (!fetchPage) {
      setLoading(false);
      return;
    }
    setLoading(true);
    fetchPage({ limit: PAGE_SIZE })
      .then((page) => {
        if (gen !== generation.current) return;
        setMessages(mergeMessages([], page));
        setHasMore(page.length >= PAGE_SIZE);
      })
      .catch((e) => {
        if (gen === generation.current) setError(getErrorMessage(e));
      })
      .finally(() => {
        if (gen === generation.current) setLoading(false);
      });
  }, []);

  // eslint-disable-next-line react-hooks/exhaustive-deps -- chỉ tải lại khi đổi hội thoại (key)
  useEffect(reload, [key]);

  /** Đồng bộ lại âm thầm (sau khi nối lại WebSocket): gộp trang mới nhất vào, không xoá gì, không bật "đang tải". */
  const refresh = useCallback(() => {
    const fetchPage = loadRef.current;
    const gen = generation.current;
    if (!fetchPage) return;
    fetchPage({ limit: PAGE_SIZE })
      .then((page) => {
        if (gen === generation.current) setMessages((cur) => mergeMessages(cur, page));
      })
      .catch(() => {
        /* lần đồng bộ sau (hoặc tin kế tiếp) sẽ bù */
      });
  }, []);

  const loadOlder = useCallback(() => {
    const fetchPage = loadRef.current;
    const oldest = messagesRef.current[0]?.id;
    if (!fetchPage || oldest === undefined) return;
    const gen = generation.current;
    setLoadingOlder(true);
    fetchPage({ before: oldest, limit: PAGE_SIZE })
      .then((page) => {
        if (gen !== generation.current) return;
        setMessages((cur) => mergeMessages(cur, page));
        setHasMore(page.length >= PAGE_SIZE);
      })
      .catch((e) => {
        if (gen === generation.current) setError(getErrorMessage(e));
      })
      .finally(() => {
        if (gen === generation.current) setLoadingOlder(false);
      });
  }, []);

  /** Thêm tin vừa gửi (REST) hoặc vừa nhận (WebSocket). Tin trùng id không bị thêm hai lần. */
  const add = useCallback((message: SupportMessage) => setMessages((cur) => mergeMessages(cur, [message])), []);

  return { messages, loading, error, hasMore, loadingOlder, reload, refresh, loadOlder, add };
}
