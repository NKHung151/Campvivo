"use client";

import { useCallback, useEffect, useState } from "react";
import type { Role } from "@/types/api/common";

export interface AdminSessionUser {
  id: number;
  fullName: string;
  email: string;
  role: Role;
}

const DEV_ROLE_KEY = "cv-admin-dev-role";

/**
 * Phiên đăng nhập cho khu vực admin.
 *
 * TẠM THỜI: chưa có đăng nhập thật (auth là phần của Người 3 — N3-03, F-14), nên hook trả về một
 * user giả để dựng giao diện. Role mặc định lấy từ NEXT_PUBLIC_ADMIN_DEV_ROLE (ADMIN nếu bỏ trống)
 * và đổi được bằng `setDevRole` để thử giao diện Employee.
 * TODO(F-14): thay bằng AuthProvider — đọc user từ GET /users/me bằng token; chưa đăng nhập → null.
 */
export function useAdminSession() {
  const [user, setUser] = useState<AdminSessionUser | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let role: Role = process.env.NEXT_PUBLIC_ADMIN_DEV_ROLE === "EMPLOYEE" ? "EMPLOYEE" : "ADMIN";
    try {
      const saved = window.localStorage.getItem(DEV_ROLE_KEY);
      if (saved === "ADMIN" || saved === "EMPLOYEE" || saved === "CUSTOMER") role = saved;
    } catch {
      /* storage bị chặn: dùng role mặc định */
    }
    setUser(devUser(role));
    setReady(true);
  }, []);

  const setDevRole = useCallback((role: Role) => {
    try {
      window.localStorage.setItem(DEV_ROLE_KEY, role);
    } catch {
      /* ignore */
    }
    setUser(devUser(role));
  }, []);

  return { ready, user, setDevRole, isDevSession: true as const };
}

function devUser(role: Role): AdminSessionUser {
  const names: Record<Role, string> = { ADMIN: "Admin (dev)", EMPLOYEE: "Nhân viên (dev)", CUSTOMER: "Khách hàng (dev)" };
  return { id: 0, fullName: names[role], email: `${role.toLowerCase()}@campvivo.local`, role };
}
