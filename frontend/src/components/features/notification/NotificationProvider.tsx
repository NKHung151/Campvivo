"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { listNotifications, markAllNotificationsRead, markNotificationRead } from "@/services/notificationService";
import { useNotificationSocket } from "@/components/features/notification/useNotificationSocket";
import type { SocketState } from "@/components/features/support/useSupportSocket";
import type { Notification, NotificationPush, NotificationType } from "@/types/api/notification";

/** Số mục trong dropdown chuông (pages.md). */
const LATEST_LIMIT = 10;
/** Quay lại tab sau từng này ms thì tải lại (tab khác có thể đã đọc / người khác đã xử lý). */
const STALE_MS = 30_000;
/** UPDATED của một dòng chưa từng thấy: coi là sự kiện mới (hiện toast) nếu xảy ra trong khoảng này. */
const FRESH_MS = 60_000;

/** Khung WS + sự kiện cục bộ để trang danh sách đi cùng chuông (đọc ở chuông → trang cũng đổi, và ngược lại). */
export type NotificationEvent =
  | NotificationPush
  | { event: "READ"; ids: number[] }
  | { event: "READ_ALL" }
  /** Vừa nối lại / tải lại: có thể đã lỡ khung → nơi nhận nên tải lại. */
  | { event: "SYNC" };

export type LoadStatus = "idle" | "loading" | "ready" | "error";

interface NotificationState {
  enabled: boolean;
  status: LoadStatus;
  unreadCount: number;
  /** 10 mục mới nhất (dropdown). */
  latest: Notification[];
  connection: SocketState;
  /** Thông báo mới nên hiện toast; `key` đổi mỗi lần. */
  incoming: { key: number; notification: Notification } | null;
  reload: () => Promise<void>;
  /** Đánh dấu đã đọc (bỏ qua nếu đã đọc). Lỗi được nuốt: không chặn việc mở link. */
  markRead: (n: Pick<Notification, "id" | "isRead">) => Promise<void>;
  markAllRead: () => Promise<void>;
  subscribe: (listener: (e: NotificationEvent) => void) => () => void;
  /** Đăng ký "đang xem" một loại: đánh dấu đọc ngay các mục chưa đọc của loại đó và mọi mục mới tới sau (không toast). */
  watchType: (type: NotificationType) => () => void;
}

const Ctx = createContext<NotificationState | null>(null);

/**
 * Thông báo in-app của người đang đăng nhập (FR-22, api.md 4.7 / 4.9). Một Provider cho mỗi root layout: storefront bật cho
 * khách, admin bật cho nhân viên. REST là nguồn đúng; WS chỉ đẩy thay đổi — mỗi lần nối lại / quay lại tab sau 30s thì tải lại.
 */
export function NotificationProvider({ enabled, children }: { enabled: boolean; children: React.ReactNode }) {
  const [status, setStatus] = useState<LoadStatus>("idle");
  const [unreadCount, setUnreadCount] = useState(0);
  const [latest, setLatest] = useState<Notification[]>([]);
  const [incoming, setIncoming] = useState<NotificationState["incoming"]>(null);

  const listeners = useRef(new Set<(e: NotificationEvent) => void>());
  /** id → createdAt đã biết, để phân biệt UPDATED "có sự kiện mới" với UPDATED "chỉ sửa số". */
  const seen = useRef(new Map<number, string>());
  const watched = useRef(new Map<NotificationType, number>());
  const lastLoad = useRef(0);
  const generation = useRef(0);
  const toastKey = useRef(0);

  const emit = useCallback((e: NotificationEvent) => {
    listeners.current.forEach((l) => l(e));
  }, []);

  const reload = useCallback(async () => {
    if (!enabled) return;
    const gen = generation.current;
    lastLoad.current = Date.now();
    setStatus((s) => (s === "ready" ? s : "loading"));
    try {
      const page = await listNotifications({ page: 1, limit: LATEST_LIMIT });
      if (gen !== generation.current) return;
      page.items.forEach((n) => seen.current.set(n.id, n.createdAt));
      setLatest(page.items);
      setUnreadCount(page.unreadCount);
      setStatus("ready");
    } catch {
      if (gen === generation.current) setStatus((s) => (s === "ready" ? s : "error"));
    }
  }, [enabled]);

  const applyRead = useCallback(
    (ids: number[], count: number) => {
      const set = new Set(ids);
      setLatest((items) => items.map((n) => (set.has(n.id) ? { ...n, isRead: true } : n)));
      setUnreadCount(count);
      emit({ event: "READ", ids });
    },
    [emit],
  );

  const markRead = useCallback(
    async (n: Pick<Notification, "id" | "isRead">) => {
      if (n.isRead) return;
      try {
        applyRead([n.id], await markNotificationRead(n.id));
      } catch {
        /* mục đã bị xoá / mất mạng: lần tải lại sau sẽ đúng */
      }
    },
    [applyRead],
  );

  const markAllRead = useCallback(async () => {
    const res = await markAllNotificationsRead();
    setLatest((items) => items.map((n) => ({ ...n, isRead: true })));
    setUnreadCount(res.unreadCount);
    emit({ event: "READ_ALL" });
  }, [emit]);

  /** Đọc hết mục chưa đọc của một loại (thường chỉ một dòng gộp, vd. SUPPORT_MESSAGE). */
  const readType = useCallback(
    async (type: NotificationType) => {
      try {
        const page = await listNotifications({ isRead: false, page: 1, limit: 100 });
        const ids = page.items.filter((n) => n.type === type).map((n) => n.id);
        let count = page.unreadCount;
        for (const id of ids) count = await markNotificationRead(id);
        if (ids.length) applyRead(ids, count);
      } catch {
        /* không quan trọng: người dùng vẫn tự đọc được */
      }
    },
    [applyRead],
  );

  const watchType = useCallback(
    (type: NotificationType) => {
      watched.current.set(type, (watched.current.get(type) ?? 0) + 1);
      if (enabled) void readType(type);
      return () => {
        const left = (watched.current.get(type) ?? 1) - 1;
        if (left > 0) watched.current.set(type, left);
        else watched.current.delete(type);
      };
    },
    [enabled, readType],
  );

  const onPush = useCallback(
    (push: NotificationPush) => {
      setUnreadCount(push.unreadCount);
      if (push.event === "RESOLVED") {
        const ids = new Set(push.ids);
        setLatest((items) =>
          items.map((n) => (ids.has(n.id) ? { ...n, resolvedAt: push.resolvedAt, resolvedBy: push.resolvedBy } : n)),
        );
        emit(push);
        return;
      }

      const n = push.notification;
      const known = seen.current.get(n.id);
      const newEvent =
        push.event === "CREATED" ||
        (known !== undefined ? known !== n.createdAt : Date.now() - Date.parse(n.createdAt) < FRESH_MS);
      seen.current.set(n.id, n.createdAt);

      setLatest((items) => {
        const at = items.findIndex((x) => x.id === n.id);
        // Chỉ sửa số (không có sự kiện mới) → giữ chỗ; có sự kiện mới → lên đầu.
        if (at >= 0 && !newEvent) return items.map((x) => (x.id === n.id ? n : x));
        return [n, ...items.filter((x) => x.id !== n.id)].slice(0, LATEST_LIMIT);
      });
      emit(push);

      if (!newEvent || n.isRead || n.resolvedAt) return;
      if (watched.current.has(n.type)) {
        void markRead(n);
        return;
      }
      toastKey.current += 1;
      setIncoming({ key: toastKey.current, notification: n });
    },
    [emit, markRead],
  );

  const onConnected = useCallback(() => {
    // Lần nối đầu ngay sau lần tải đầu: khỏi tải hai lần.
    if (Date.now() - lastLoad.current > 3000) void reload();
    emit({ event: "SYNC" });
    watched.current.forEach((_, type) => void readType(type));
  }, [emit, readType, reload]);

  const connection = useNotificationSocket({ enabled, onPush, onConnected });

  // Bật → tải lần đầu; tắt (đăng xuất) → xoá sạch.
  useEffect(() => {
    generation.current += 1;
    seen.current.clear();
    setLatest([]);
    setUnreadCount(0);
    setIncoming(null);
    setStatus("idle");
    if (enabled) void reload();
  }, [enabled, reload]);

  useEffect(() => {
    if (!enabled) return;
    const onVisible = () => {
      if (document.visibilityState === "visible" && Date.now() - lastLoad.current > STALE_MS) {
        void reload();
        emit({ event: "SYNC" });
      }
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [enabled, emit, reload]);

  const subscribe = useCallback((listener: (e: NotificationEvent) => void) => {
    listeners.current.add(listener);
    return () => {
      listeners.current.delete(listener);
    };
  }, []);

  const value = useMemo<NotificationState>(
    () => ({
      enabled,
      status,
      unreadCount,
      latest,
      connection,
      incoming,
      reload,
      markRead,
      markAllRead,
      subscribe,
      watchType,
    }),
    [enabled, status, unreadCount, latest, connection, incoming, reload, markRead, markAllRead, subscribe, watchType],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

/** null khi không nằm trong Provider (component dùng được ở nơi không có thông báo). */
export function useNotifications(): NotificationState | null {
  return useContext(Ctx);
}

/** Đang mở màn hình của một loại (vd. chat hỗ trợ) → thông báo loại đó coi như đã đọc. */
export function useAutoReadNotifications(type: NotificationType, active: boolean) {
  const ctx = useNotifications();
  const watchType = ctx?.watchType;
  const enabled = ctx?.enabled ?? false;
  useEffect(() => {
    if (!active || !enabled || !watchType) return;
    return watchType(type);
  }, [type, active, enabled, watchType]);
}
