import type { Role } from "@/types/api/common";

/** Tham số `GET /admin/users` — docs/02-ky-thuat/api.md mục 4.10 (FR-14). */
export interface AdminUserQuery {
  /** Email hoặc họ tên chứa từ khoá, không phân biệt hoa thường (chưa tìm không dấu). */
  q?: string;
  role?: Role;
  /** Bỏ trống = cả tài khoản đang hoạt động lẫn đã bị khoá. */
  isActive?: boolean;
  page?: number;
  limit?: number;
}

/** Một dòng trong danh sách. Không có địa chỉ, mật khẩu, OTP, token_version. */
export interface AdminUserSummary {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  role: Role;
  isActive: boolean;
  emailVerified: boolean;
  createdAt: string;
}

/** Chi tiết một người dùng kèm thống kê đơn (đơn vị tiền: VND). */
export interface AdminUserDetail extends AdminUserSummary {
  address: string | null;
  /** Mọi đơn của người này, mọi trạng thái. */
  orderCount: number;
  completedOrderCount: number;
  /** Tổng tiền các đơn COMPLETED; đơn đã hoàn trả hoặc đã huỷ không tính. */
  totalSpent: number;
}

/**
 * Body `PATCH /admin/users/{id}`: sửa từng phần, trường vắng = giữ nguyên, `phone` là "" = xoá. Đổi `role` hoặc `isActive`
 * thật sự thì người đó bị đăng xuất ở mọi thiết bị (server tăng token_version).
 */
export interface UpdateAdminUserRequest {
  fullName?: string;
  phone?: string;
  role?: Role;
  isActive?: boolean;
}
