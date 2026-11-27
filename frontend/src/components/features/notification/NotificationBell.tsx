"use client";

import Link from "next/link";
import { BellOutlined } from "@ant-design/icons";
import { useEffect, useRef, useState } from "react";
import { badgeText } from "@/lib/notification";
import { useNotifications } from "@/components/features/notification/NotificationProvider";
import { NotificationItem, useOpenNotification } from "@/components/features/notification/NotificationItem";

/**
 * Chuông thông báo (pages.md): badge số chưa đọc (99+), dropdown 10 mục mới nhất, "Đánh dấu tất cả đã đọc", "Xem tất cả".
 * Dùng chung cho header storefront (khách) và thanh trên của admin shell (nhân viên); không có Provider đang bật → không hiện.
 */
export function NotificationBell({ allHref, className = "" }: { allHref: string; className?: string }) {
  const ctx = useNotifications();
  const open = useOpenNotification();
  const [shown, setShown] = useState(false);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const root = useRef<HTMLDivElement>(null);

  const reload = ctx?.reload;
  const status = ctx?.status;

  // Đóng khi bấm ra ngoài / Esc. Mở dropdown lúc trước đó tải lỗi → thử lại.
  useEffect(() => {
    if (!shown) return;
    setNow(Date.now());
    if (status === "error") void reload?.();
    const onDown = (e: MouseEvent) => {
      if (root.current && !root.current.contains(e.target as Node)) setShown(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setShown(false);
    };
    document.addEventListener("mousedown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDown);
      document.removeEventListener("keydown", onKey);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- chỉ chạy khi mở/đóng
  }, [shown]);

  if (!ctx?.enabled) return null;
  const badge = badgeText(ctx.unreadCount);

  const markAll = async () => {
    setBusy(true);
    try {
      await ctx.markAllRead();
    } catch {
      /* giữ nguyên; người dùng bấm lại được */
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={`cv-ntf ${className}`} ref={root}>
      <button
        type="button"
        className="cv-ntf-bell"
        aria-label={badge ? `Thông báo, ${ctx.unreadCount} chưa đọc` : "Thông báo"}
        aria-expanded={shown}
        aria-haspopup="dialog"
        onClick={() => setShown((s) => !s)}
      >
        <BellOutlined />
        {badge && <span className="cv-ntf-badge">{badge}</span>}
      </button>
      {shown && (
        <div className="cv-ntf-panel" role="dialog" aria-label="Thông báo">
          <div className="cv-ntf-panel-head">
            <b>Thông báo</b>
            <button type="button" className="cv-ntf-link" onClick={markAll} disabled={busy || ctx.unreadCount === 0}>
              Đánh dấu tất cả đã đọc
            </button>
          </div>
          {ctx.status === "error" && ctx.latest.length === 0 ? (
            <div className="cv-ntf-state">
              Không tải được thông báo.{" "}
              <button type="button" className="cv-ntf-link" onClick={() => void ctx.reload()}>
                Thử lại
              </button>
            </div>
          ) : ctx.status !== "ready" && ctx.latest.length === 0 ? (
            <div className="cv-ntf-state">Đang tải…</div>
          ) : ctx.latest.length === 0 ? (
            <div className="cv-ntf-state">Chưa có thông báo nào.</div>
          ) : (
            <ul className="cv-ntf-list">
              {ctx.latest.map((n) => (
                <NotificationItem
                  key={n.id}
                  n={n}
                  now={now}
                  onOpen={(x) => {
                    setShown(false);
                    void open(x);
                  }}
                />
              ))}
            </ul>
          )}
          <div className="cv-ntf-panel-foot">
            <Link href={allHref} onClick={() => setShown(false)}>
              Xem tất cả
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
