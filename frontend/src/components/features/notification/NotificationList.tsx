"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { listNotifications } from "@/services/notificationService";
import { getErrorMessage } from "@/lib/errorMessages";
import { useNotifications, type NotificationEvent } from "@/components/features/notification/NotificationProvider";
import { NotificationItem, useOpenNotification } from "@/components/features/notification/NotificationItem";
import type { Notification, NotificationQuery } from "@/types/api/notification";
import type { Pagination } from "@/types/api/common";

type Filter = "all" | "unread" | "read" | "unresolved";

const FILTERS: { id: Filter; label: string; query: NotificationQuery; staffOnly?: boolean }[] = [
  { id: "all", label: "Tất cả", query: {} },
  { id: "unread", label: "Chưa đọc", query: { isRead: false } },
  { id: "read", label: "Đã đọc", query: { isRead: true } },
  { id: "unresolved", label: "Chưa xử lý", query: { resolved: false }, staffOnly: true },
];

const LIMIT = 20;

type Load =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { status: "ready"; items: Notification[]; pagination: Pagination };

/**
 * Trang thông báo (`/account/notifications`, `/admin/notifications` — pages.md): tab lọc, phân trang, "Đánh dấu tất cả đã
 * đọc". Đi cùng chuông qua Provider: đọc / xử lý ở nơi khác thì dòng ở đây đổi theo; có thông báo mới khi đang ở trang 1 thì
 * tải lại trang 1.
 */
export function NotificationList({ staff = false }: { staff?: boolean }) {
  const ctx = useNotifications();
  const open = useOpenNotification();
  const [filter, setFilter] = useState<Filter>("all");
  const [page, setPage] = useState(1);
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const request = useRef(0);
  const enabled = ctx?.enabled ?? false;
  const subscribe = ctx?.subscribe;

  const fetchPage = useCallback(
    async (silent: boolean) => {
      const id = ++request.current;
      if (!silent) setLoad({ status: "loading" });
      try {
        const query = FILTERS.find((f) => f.id === filter)!.query;
        const res = await listNotifications({ ...query, page, limit: LIMIT });
        if (id !== request.current) return;
        // Trang vượt quá (vừa đánh dấu đọc hết ở tab "Chưa đọc"...) → lùi về trang cuối còn dữ liệu.
        if (res.items.length === 0 && page > 1 && res.pagination.totalPages < page) {
          setPage(Math.max(1, res.pagination.totalPages));
          return;
        }
        setNow(Date.now());
        setLoad({ status: "ready", items: res.items, pagination: res.pagination });
      } catch (e) {
        if (id === request.current && !silent) setLoad({ status: "error", message: getErrorMessage(e) });
      }
    },
    [filter, page],
  );

  useEffect(() => {
    if (enabled) void fetchPage(false);
  }, [enabled, fetchPage]);

  // Áp thay đổi từ chuông / WS lên các dòng đang hiện.
  useEffect(() => {
    if (!subscribe) return;
    return subscribe((e: NotificationEvent) => {
      if (e.event === "CREATED" || e.event === "UPDATED" || e.event === "SYNC") {
        if (page === 1 || e.event === "SYNC") void fetchPage(true);
        return;
      }
      let apply: (n: Notification) => Notification;
      if (e.event === "READ_ALL") {
        apply = (n) => ({ ...n, isRead: true });
      } else if (e.event === "READ") {
        apply = (n) => (e.ids.includes(n.id) ? { ...n, isRead: true } : n);
      } else {
        const { ids, resolvedAt, resolvedBy } = e;
        apply = (n) => (ids.includes(n.id) ? { ...n, resolvedAt, resolvedBy } : n);
      }
      setLoad((l) => (l.status === "ready" ? { ...l, items: l.items.map(apply) } : l));
    });
  }, [subscribe, fetchPage, page]);

  if (!ctx?.enabled) {
    return (
      <div className="cv-ntf-page">
        <div className="cv-ntf-state">
          {staff ? "Đăng nhập bằng tài khoản nhân viên để xem thông báo." : "Thông báo của nhân viên nằm ở trang quản trị."}
        </div>
      </div>
    );
  }

  const markAll = async () => {
    setBusy(true);
    try {
      await ctx.markAllRead();
    } catch {
      /* giữ nguyên */
    } finally {
      setBusy(false);
    }
  };

  const filters = FILTERS.filter((f) => staff || !f.staffOnly);

  return (
    <div className="cv-ntf-page">
      <div className="cv-ntf-toolbar">
        <div className="cv-ntf-tabs" role="tablist">
          {filters.map((f) => (
            <button
              key={f.id}
              type="button"
              role="tab"
              aria-selected={filter === f.id}
              className={filter === f.id ? "active" : ""}
              onClick={() => {
                setFilter(f.id);
                setPage(1);
              }}
            >
              {f.label}
              {f.id === "unread" && ctx.unreadCount > 0 && <span className="cv-ntf-count">{ctx.unreadCount}</span>}
            </button>
          ))}
        </div>
        <button type="button" className="cv-ntf-link" onClick={markAll} disabled={busy || ctx.unreadCount === 0}>
          Đánh dấu tất cả đã đọc
        </button>
      </div>

      {load.status === "loading" ? (
        <div className="cv-ntf-state">Đang tải…</div>
      ) : load.status === "error" ? (
        <div className="cv-ntf-state">
          {load.message}{" "}
          <button type="button" className="cv-ntf-link" onClick={() => void fetchPage(false)}>
            Thử lại
          </button>
        </div>
      ) : load.items.length === 0 ? (
        <div className="cv-ntf-state">
          {filter === "unread"
            ? "Không có thông báo chưa đọc."
            : filter === "unresolved"
              ? "Không còn việc nào chờ xử lý."
              : "Chưa có thông báo nào."}
        </div>
      ) : (
        <>
          <ul className="cv-ntf-list cv-ntf-list--page">
            {load.items.map((n) => (
              <NotificationItem key={n.id} n={n} now={now} onOpen={(x) => void open(x)} />
            ))}
          </ul>
          {load.pagination.totalPages > 1 && (
            <div className="cv-ntf-pager">
              <button type="button" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
                ‹ Trước
              </button>
              <span>
                Trang {page} / {load.pagination.totalPages}
              </span>
              <button
                type="button"
                disabled={page >= load.pagination.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Sau ›
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
