"use client";

import { useAuth } from "@/components/features/auth/AuthProvider";
import { NotificationProvider } from "@/components/features/notification/NotificationProvider";
import { NotificationToaster } from "@/components/features/notification/NotificationToaster";

/**
 * Thông báo ở storefront: chỉ bật cho khách hàng (D-42). Nhân viên xem cửa hàng không có chuông ở đây — thông báo của họ
 * dẫn về /admin và đã có chuông ở admin shell.
 */
export function StoreNotifications({ children }: { children: React.ReactNode }) {
  const { status, user } = useAuth();
  const enabled = status === "authenticated" && user?.role === "CUSTOMER";
  return (
    <NotificationProvider enabled={enabled}>
      {children}
      <NotificationToaster />
    </NotificationProvider>
  );
}
