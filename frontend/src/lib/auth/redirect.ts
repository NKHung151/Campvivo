import type { Role } from "@/types/api/common";
import { findNavItem, homeFor } from "@/lib/admin/nav";

/*
 * Trang đích sau đăng nhập (docs/03-san-pham/user-flows.md mục 2, pages.md):
 *  - có ?redirect= hợp lệ VÀ role được vào đó → đi tới đó;
 *  - ngược lại → trang mặc định của role (ADMIN /admin, EMPLOYEE /admin/orders, CUSTOMER /).
 * Toàn bộ là hàm thuần để kiểm chứng độc lập. Đây chỉ là lớp tiện dụng phía client; backend luôn kiểm tra quyền.
 */

/** Trang thuộc khu vực khách hàng: chỉ role CUSTOMER dùng được (backend chặn các API tương ứng với role khác). */
const CUSTOMER_AREAS = ["/account", "/cart", "/checkout"];

/** Trang xác thực: không dùng làm đích redirect (tránh vòng lặp /login → /login). */
const AUTH_PAGES = ["/login", "/register", "/verify-email", "/forgot-password", "/reset-password"];

const MAX_REDIRECT_LENGTH = 512;

function pathnameOf(path: string): string {
  return path.split(/[?#]/)[0].replace(/\/+$/, "") || "/";
}

function isUnder(pathname: string, base: string): boolean {
  return pathname === base || pathname.startsWith(`${base}/`);
}

/**
 * Chỉ nhận đường dẫn nội bộ của chính ứng dụng; trả null với mọi thứ khác (URL ngoài, `//host`, `/\host`,
 * ký tự điều khiển, trang xác thực) để chống open redirect.
 */
export function safeRedirectPath(raw: string | null | undefined): string | null {
  if (!raw || raw.length > MAX_REDIRECT_LENGTH) return null;
  if (!raw.startsWith("/") || raw.startsWith("//")) return null;
  // eslint-disable-next-line no-control-regex -- chủ ý loại ký tự điều khiển và dấu gạch ngược
  if (/[\u0000-\u001f\u007f\\]/.test(raw)) return null;
  // Cũng chặn dạng đã mã hoá của ký tự điều khiển (%00–%1F, %7F) và dấu gạch ngược (%5C).
  if (/%(?:[01][0-9a-f]|7f|5c)/i.test(raw)) return null;

  let parsed: URL;
  try {
    parsed = new URL(raw, "http://internal.invalid");
  } catch {
    return null;
  }
  if (parsed.origin !== "http://internal.invalid") return null;

  const pathname = pathnameOf(parsed.pathname);
  if (AUTH_PAGES.some((p) => isUnder(pathname, p))) return null;
  return `${parsed.pathname}${parsed.search}${parsed.hash}`;
}

/** Trang mặc định của role sau khi đăng nhập. */
export function landingFor(role: Role): string {
  return role === "CUSTOMER" ? "/" : homeFor(role);
}

/** Role có được vào đường dẫn này không (theo menu admin và khu vực khách hàng). */
export function canAccessPath(role: Role, path: string): boolean {
  const pathname = pathnameOf(path);

  if (isUnder(pathname, "/admin")) {
    if (role === "CUSTOMER") return false;
    const item = findNavItem(pathname);
    // Đường dẫn admin không có trong menu: chỉ ADMIN (an toàn mặc định cho EMPLOYEE).
    return item ? (item.roles as string[]).includes(role) : role === "ADMIN";
  }
  if (CUSTOMER_AREAS.some((area) => isUnder(pathname, area))) return role === "CUSTOMER";
  return true;
}

/** Quyết định trang đích: `?redirect=` hợp lệ và role được vào thì dùng, không thì trang mặc định của role. */
export function resolveLanding(role: Role, redirect: string | null | undefined): string {
  const safe = safeRedirectPath(redirect);
  return safe && canAccessPath(role, safe) ? safe : landingFor(role);
}

/** Đường dẫn một trang xác thực kèm `?redirect=` (nếu hợp lệ) và các tham số khác (vd. email). */
export function authHref(page: string, params: Record<string, string | null | undefined> = {}, returnTo?: string | null): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) if (value) query.set(key, value);
  const safe = safeRedirectPath(returnTo);
  if (safe) query.set("redirect", safe);
  const qs = query.toString();
  return qs ? `${page}?${qs}` : page;
}

/** Đường dẫn trang đăng nhập kèm trang cần quay lại. */
export function loginHref(returnTo?: string): string {
  return authHref("/login", {}, returnTo);
}
