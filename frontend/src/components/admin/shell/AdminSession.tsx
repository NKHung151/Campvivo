"use client";

import { useAuth } from "@/components/features/auth/AuthProvider";

/**
 * Phiên đăng nhập cho khu vực /admin — dựa trên phiên đăng nhập thật (AuthProvider, GET /users/me).
 * Giữ API `{ ready, user }` để các trang admin không phải sửa; `status` cho phép phân biệt chưa đăng nhập
 * với không kết nối được máy chủ.
 */
export function useAdmin() {
  const { status, user, logout, retry } = useAuth();
  return { ready: status !== "loading", status, user, logout, retry };
}
