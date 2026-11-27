"use client";

import {
  CheckCircleOutlined,
  DollarOutlined,
  FlagOutlined,
  HeartOutlined,
  InboxOutlined,
  MessageOutlined,
  RollbackOutlined,
  ShoppingCartOutlined,
  ShoppingOutlined,
  TagOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { useCallback } from "react";
import { useRouter } from "next/navigation";
import { crossesRootLayout, resolvedText, timeAgo } from "@/lib/notification";
import { useNotifications } from "@/components/features/notification/NotificationProvider";
import type { Notification, NotificationType } from "@/types/api/notification";

const ICON: Record<NotificationType, React.ReactNode> = {
  ORDER_STATUS: <ShoppingOutlined />,
  RETURN_STATUS: <RollbackOutlined />,
  SUPPORT_MESSAGE: <MessageOutlined />,
  VOUCHER_NEW: <TagOutlined />,
  FAVORITE_ON_SALE: <HeartOutlined />,
  FAVORITE_BACK_IN_STOCK: <InboxOutlined />,
  ORDER_NEW: <ShoppingCartOutlined />,
  ORDER_PAID: <DollarOutlined />,
  RETURN_NEW: <RollbackOutlined />,
  SUPPORT_NEW: <MessageOutlined />,
  LOW_STOCK: <WarningOutlined />,
  REVIEW_FLAGGED: <FlagOutlined />,
};

export function NotificationIcon({ type, resolved }: { type: NotificationType; resolved?: boolean }) {
  return (
    <span className={`cv-ntf-icon cv-ntf-icon--${type.toLowerCase()}${resolved ? " is-resolved" : ""}`} aria-hidden="true">
      {resolved ? <CheckCircleOutlined /> : ICON[type]}
    </span>
  );
}

/** Bấm thông báo: đánh dấu đã đọc rồi đi tới `link` (không có link → chỉ đánh dấu đọc). */
export function useOpenNotification() {
  const ctx = useNotifications();
  const router = useRouter();
  const markRead = ctx?.markRead;
  return useCallback(
    async (n: Notification) => {
      const reading = markRead?.(n);
      if (!n.link) return reading;
      if (crossesRootLayout(n.link, window.location.pathname)) {
        await reading; // trang sắp tải lại: chờ PATCH xong
        window.location.assign(n.link);
      } else {
        router.push(n.link);
      }
    },
    [markRead, router],
  );
}

/** Một dòng thông báo (dropdown chuông và trang danh sách). Mục đã xử lý mờ đi kèm "Đã xử lý bởi … lúc …". */
export function NotificationItem({ n, onOpen, now }: { n: Notification; onOpen: (n: Notification) => void; now?: number }) {
  const resolved = resolvedText(n);
  const unread = !n.isRead && !n.resolvedAt;
  return (
    <li className={`cv-ntf-item${unread ? " is-unread" : ""}${resolved ? " is-resolved" : ""}`}>
      <button type="button" onClick={() => onOpen(n)} title={n.title}>
        <NotificationIcon type={n.type} resolved={!!resolved} />
        <span className="cv-ntf-body">
          <span className="cv-ntf-title">
            {n.title}
            {n.eventCount > 1 && <span className="cv-ntf-count">{n.eventCount}</span>}
          </span>
          {n.content && <span className="cv-ntf-content">{n.content}</span>}
          <span className="cv-ntf-meta">
            {timeAgo(n.createdAt, now)}
            {resolved && <span className="cv-ntf-resolved"> · {resolved}</span>}
          </span>
        </span>
        {unread && <span className="cv-ntf-dot" aria-label="Chưa đọc" />}
      </button>
    </li>
  );
}
