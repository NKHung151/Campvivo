"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { clearToken, getToken, setToken, TOKEN_KEY } from "@/lib/authToken";
import { setUnauthorizedHandler } from "@/services/apiClient";
import { isApiError } from "@/services/apiError";
import { authService } from "@/services/authService";
import type { UserProfile } from "@/types/api/common";

/**
 * Trạng thái phiên đăng nhập:
 *  - loading: đang khôi phục phiên (có token → hỏi GET /users/me);
 *  - authenticated: có `user` từ server (role lấy từ server, không tin client);
 *  - unauthenticated: không có token hoặc token bị server từ chối (đã xoá token);
 *  - error: không hỏi được server (mất mạng / 5xx) — GIỮ token, cho thử lại, không coi là đã đăng xuất.
 */
export type AuthStatus = "loading" | "authenticated" | "unauthenticated" | "error";

interface AuthContextValue {
  status: AuthStatus;
  user: UserProfile | null;
  /** Đăng nhập; lỗi (ApiError) được ném lại cho form hiển thị. */
  login: (email: string, password: string) => Promise<UserProfile>;
  /** Đăng xuất: gọi API (đăng xuất mọi thiết bị), luôn xoá token phía client dù API lỗi. */
  logout: () => Promise<void>;
  /** Thử khôi phục phiên lại (khi status = "error"). */
  retry: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/** Đặt ở cả hai root layout ((store) và (admin)); token dùng chung qua localStorage. */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<UserProfile | null>(null);
  const alive = useRef(true);

  const restore = useCallback(async () => {
    if (!getToken()) {
      setUser(null);
      setStatus("unauthenticated");
      return;
    }
    setStatus("loading");
    try {
      const profile = await authService.getMe();
      if (!alive.current) return;
      setUser(profile);
      setStatus("authenticated");
    } catch (error) {
      if (!alive.current) return;
      // 401 UNAUTHORIZED: apiClient đã xoá token. Lỗi khác (mạng/5xx): giữ token để thử lại.
      const rejected = isApiError(error) && error.status === 401;
      setUser(null);
      setStatus(rejected ? "unauthenticated" : "error");
    }
  }, []);

  useEffect(() => {
    alive.current = true;
    void restore();

    // Token bị server từ chối giữa chừng (hết hạn, đăng xuất ở thiết bị khác, bị khoá).
    setUnauthorizedHandler(() => {
      if (!alive.current) return;
      setUser(null);
      setStatus("unauthenticated");
    });

    // Đăng nhập / đăng xuất ở tab khác: đồng bộ lại phiên.
    const onStorage = (event: StorageEvent) => {
      if (event.key === TOKEN_KEY) void restore();
    };
    window.addEventListener("storage", onStorage);

    return () => {
      alive.current = false;
      setUnauthorizedHandler(null);
      window.removeEventListener("storage", onStorage);
    };
  }, [restore]);

  const login = useCallback(async (email: string, password: string) => {
    const result = await authService.login({ email: email.trim(), password });
    setToken(result.accessToken);
    setUser(result.user);
    setStatus("authenticated");
    return result.user;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } catch {
      /* token đã hết hạn hoặc mất mạng: vẫn đăng xuất phía client */
    } finally {
      clearToken();
      setUser(null);
      setStatus("unauthenticated");
    }
  }, []);

  const retry = useCallback(() => void restore(), [restore]);

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout, retry }),
    [status, user, login, logout, retry],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth() phải nằm trong <AuthProvider>");
  return ctx;
}
