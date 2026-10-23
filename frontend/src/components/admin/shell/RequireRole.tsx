"use client";

import { Button, Result, Spin } from "antd";
import { homeFor, type StaffRole } from "@/lib/admin/nav";
import { useAdmin } from "@/components/admin/shell/AdminSession";

/**
 * Chặn giao diện theo role. Chỉ là lớp tiện dụng phía client — backend luôn kiểm tra lại bằng
 * RBAC (.agents/rules/frontend.md).
 */
export function RequireRole({ roles, children }: { roles: StaffRole[]; children: React.ReactNode }) {
  const { ready, user } = useAdmin();

  if (!ready) {
    return (
      <div style={{ padding: 48, textAlign: "center" }}>
        <Spin />
      </div>
    );
  }
  if (!user || !(roles as string[]).includes(user.role)) {
    const isStaff = user?.role === "ADMIN" || user?.role === "EMPLOYEE";
    return (
      <Result
        status="403"
        title="Không có quyền truy cập"
        subTitle="Tài khoản của bạn không được phép xem trang này."
        extra={
          isStaff ? (
            <Button type="primary" href={homeFor(user.role as StaffRole)}>
              Về trang quản trị
            </Button>
          ) : (
            <Button type="primary" href="/">
              Về trang chủ
            </Button>
          )
        }
      />
    );
  }
  return <>{children}</>;
}
