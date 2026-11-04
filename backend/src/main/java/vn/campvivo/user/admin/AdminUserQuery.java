package vn.campvivo.user.admin;

import vn.campvivo.user.Role;

/**
 * Tham số GET /api/admin/users — docs/02-ky-thuat/api.md mục 4.10. `role` viết hoa trên query (?role=EMPLOYEE); sai giá trị →
 * 400 VALIDATION_ERROR. `isActive` null = cả hai nhóm.
 */
public record AdminUserQuery(String q, Role role, Boolean isActive, int page, int limit) {
}
