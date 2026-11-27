import { apiClient } from "@/services/apiClient";
import type { Notification, NotificationList, NotificationQuery, ReadAllResult } from "@/types/api/notification";

/** Thông báo của chính người đang đăng nhập (mọi role) — docs/02-ky-thuat/api.md mục 4.7. Mới nhất trước. */

export async function listNotifications(query: NotificationQuery = {}): Promise<NotificationList> {
  const { data, pagination } = await apiClient.getWithPagination<{ items: Notification[]; unreadCount: number }>(
    "/notifications",
    { params: query },
  );
  return { items: data.items, unreadCount: data.unreadCount, pagination };
}

/** Idempotent; thông báo của người khác → 404. Trả `unreadCount` mới. */
export async function markNotificationRead(id: number): Promise<number> {
  return (await apiClient.patch<{ unreadCount: number }>(`/notifications/${id}/read`)).unreadCount;
}

export function markAllNotificationsRead(): Promise<ReadAllResult> {
  return apiClient.patch<ReadAllResult>("/notifications/read-all");
}
