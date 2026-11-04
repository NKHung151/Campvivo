import type { Role } from "@/types/api/common";

/** Nhãn và màu hiển thị của vai trò (users.role, authentication.md mục 1). */
export const ROLE_LABEL: Record<Role, { label: string; color: string }> = {
  CUSTOMER: { label: "Khách hàng", color: "default" },
  EMPLOYEE: { label: "Nhân viên", color: "blue" },
  ADMIN: { label: "Quản trị viên", color: "volcano" },
};

export const ROLES = Object.keys(ROLE_LABEL) as Role[];
