import type { Notification } from "@/types/api/notification";

/** Hiển thị thông báo (FR-22) — hàm thuần, dùng chung cho chuông, toast và trang danh sách ở cả storefront lẫn admin. */

const pad = (n: number) => String(n).padStart(2, "0");

/** "14:05 27/11/2026" theo giờ máy người xem (backend trả ISO kèm offset +07:00). */
export function formatDateTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return `${pad(d.getHours())}:${pad(d.getMinutes())} ${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
}

/** "Vừa xong", "5 phút trước", "3 giờ trước", "2 ngày trước"; quá 7 ngày → ngày giờ đầy đủ. */
export function timeAgo(iso: string, now: number = Date.now()): string {
  const t = Date.parse(iso);
  if (Number.isNaN(t)) return "";
  const minutes = Math.floor((now - t) / 60_000);
  if (minutes < 1) return "Vừa xong";
  if (minutes < 60) return `${minutes} phút trước`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} giờ trước`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} ngày trước`;
  return formatDateTime(iso);
}

/** "Đã xử lý bởi Trần B lúc 14:05 27/11/2026"; `resolvedBy` null = Hệ thống (vd. job huỷ đơn quá hạn). Chưa xử lý → null. */
export function resolvedText(n: Pick<Notification, "resolvedAt" | "resolvedBy">): string | null {
  if (!n.resolvedAt) return null;
  const who = n.resolvedBy ? n.resolvedBy.fullName || "một nhân viên" : "Hệ thống";
  return `Đã xử lý bởi ${who} lúc ${formatDateTime(n.resolvedAt)}`;
}

/** Badge chuông: 0 → ẩn, > 99 → "99+". */
export function badgeText(count: number): string | null {
  if (count <= 0) return null;
  return count > 99 ? "99+" : String(count);
}

/**
 * Link của thông báo nằm ở root layout khác với trang hiện tại (storefront ↔ /admin) thì phải tải lại trang để nạp đúng CSS —
 * dùng `location.assign`; cùng khu vực thì dùng router của Next.
 */
export function crossesRootLayout(link: string, currentPath: string): boolean {
  const isAdmin = (p: string) => p === "/admin" || p.startsWith("/admin/") || p.startsWith("/admin?");
  return isAdmin(link) !== isAdmin(currentPath);
}
