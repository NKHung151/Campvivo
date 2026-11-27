"use client";

import { App } from "antd";
import { useEffect } from "react";
import { useNotifications } from "@/components/features/notification/NotificationProvider";
import { NotificationIcon, useOpenNotification } from "@/components/features/notification/NotificationItem";

/** Toast thông báo mới ở khu vực admin — dùng `notification` của antd (trong `<App>` của AdminProviders). */
export function AdminNotificationToaster() {
  const ctx = useNotifications();
  const open = useOpenNotification();
  const { notification } = App.useApp();
  const incoming = ctx?.incoming;

  useEffect(() => {
    if (!incoming) return;
    const n = incoming.notification;
    const key = `ntf-${incoming.key}`;
    notification.open({
      key,
      message: n.title,
      description: n.content || undefined,
      icon: <NotificationIcon type={n.type} />,
      placement: "bottomRight",
      duration: 6,
      className: "cv-ntf-antd",
      onClick: () => {
        notification.destroy(key);
        void open(n);
      },
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- chỉ khi có thông báo mới
  }, [incoming]);

  return null;
}
