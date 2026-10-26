/**
 * Token đăng nhập (JWT) lưu ở localStorage — docs/02-ky-thuat/authentication.md mục 3.
 * Mọi thao tác storage bọc try/catch: chế độ riêng tư hoặc bị chặn không được làm hỏng trang.
 * Chạy phía server (SSR) luôn trả null: SSR chỉ gọi API công khai.
 */
export const TOKEN_KEY = "campvivo_token";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(TOKEN_KEY, token);
  } catch {
    /* storage bị chặn: phiên chỉ sống trong bộ nhớ trang này */
  }
}

export function clearToken(): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* ignore */
  }
}
