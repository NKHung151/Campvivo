"use client";

import { useEffect, useState } from "react";
import { useNotifications } from "@/components/features/notification/NotificationProvider";
import { NotificationIcon, useOpenNotification } from "@/components/features/notification/NotificationItem";
import type { Notification } from "@/types/api/notification";

const SHOW_MS = 6000;

/** Toast storefront khi có thông báo mới (pages.md). Một toast một lúc: cái mới thay cái cũ. */
export function NotificationToaster() {
  const ctx = useNotifications();
  const open = useOpenNotification();
  const [toast, setToast] = useState<Notification | null>(null);
  const incoming = ctx?.incoming;

  useEffect(() => {
    if (!incoming) return;
    setToast(incoming.notification);
    const t = setTimeout(() => setToast(null), SHOW_MS);
    return () => clearTimeout(t);
  }, [incoming]);

  if (!toast) return null;
  return (
    <div className="cv-ntf-toast" role="status" aria-live="polite">
      <button
        type="button"
        className="cv-ntf-toast-main"
        onClick={() => {
          setToast(null);
          void open(toast);
        }}
      >
        <NotificationIcon type={toast.type} />
        <span className="cv-ntf-body">
          <span className="cv-ntf-title">{toast.title}</span>
          {toast.content && <span className="cv-ntf-content">{toast.content}</span>}
        </span>
      </button>
      <button type="button" className="cv-ntf-toast-x" aria-label="Đóng" onClick={() => setToast(null)}>
        ×
      </button>
    </div>
  );
}
