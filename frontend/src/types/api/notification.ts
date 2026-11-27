import type { NotificationType, Pagination } from "@/types/api/common";

/** Thông báo in-app — docs/02-ky-thuat/api.md mục 4.7, 4.9 (FR-22, D-42, D-43). */

export type { NotificationType };

export interface NotificationActor {
  id: number;
  fullName: string;
}

export interface Notification {
  id: number;
  type: NotificationType;
  title: string;
  content: string;
  link: string | null;
  /** > 1 = dòng gộp nhiều sự kiện (vd. "Nguyễn A đã nhắn 3 tin"). */
  eventCount: number;
  isRead: boolean;
  /** Thời điểm sự kiện MỚI NHẤT (dòng gộp được cập nhật lại). */
  createdAt: string;
  /** Loại cần xử lý (chỉ của nhân viên). */
  actionable: boolean;
  resolvedAt: string | null;
  /** `resolvedAt` có mà `resolvedBy` null = Hệ thống. */
  resolvedBy: NotificationActor | null;
}

export interface NotificationQuery {
  isRead?: boolean;
  /** false = loại cần xử lý mà chưa ai làm. */
  resolved?: boolean;
  page?: number;
  limit?: number;
}

export interface NotificationList {
  items: Notification[];
  /** Chưa đọc VÀ chưa xử lý của chính mình — số trên chuông. */
  unreadCount: number;
  pagination: Pagination;
}

export interface ReadAllResult {
  updated: number;
  unreadCount: number;
}

/** Khung đẩy qua `/user/queue/notifications`. `UPDATED` = dòng gộp đổi (thêm sự kiện, hoặc chỉ sửa số). */
export type NotificationPush =
  | { event: "CREATED"; notification: Notification; unreadCount: number }
  | { event: "UPDATED"; notification: Notification; unreadCount: number }
  | {
      event: "RESOLVED";
      ids: number[];
      resolvedAt: string;
      resolvedBy: NotificationActor | null;
      unreadCount: number;
    };
