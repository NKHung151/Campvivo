"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { resolveLanding } from "@/lib/auth/redirect";

/**
 * Đã có phiên (vừa đăng nhập/xác thực, hoặc vào trang xác thực khi đang đăng nhập) → về trang đích theo role
 * (`resolveLanding`). Chỉ một lần. `redirect` là giá trị thô của ?redirect=, được kiểm tra an toàn trước khi dùng.
 */
export function useLandingRedirect(redirect: string | null): void {
  const router = useRouter();
  const { status, user } = useAuth();
  const navigated = useRef(false);

  useEffect(() => {
    if (status !== "authenticated" || !user || navigated.current) return;
    navigated.current = true;
    const destination = resolveLanding(user.role, redirect);
    // Vào /admin là rời root layout storefront → tải lại trang để nạp đúng CSS/layout.
    if (destination === "/admin" || destination.startsWith("/admin/")) window.location.assign(destination);
    else router.replace(destination);
  }, [status, user, redirect, router]);
}
