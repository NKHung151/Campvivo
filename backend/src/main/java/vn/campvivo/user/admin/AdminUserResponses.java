package vn.campvivo.user.admin;

import vn.campvivo.order.UserOrderStats;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Dữ liệu trả về của API quản lý người dùng (api.md mục 4.10). Không bao giờ chứa mật khẩu, trường OTP hay token_version. */
public final class AdminUserResponses {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private AdminUserResponses() {
    }

    /** Một dòng trong danh sách. Không có `address` (chỉ xem ở trang chi tiết). */
    public record Summary(
            Long id,
            String email,
            String fullName,
            String phone,
            Role role,
            boolean isActive,
            boolean emailVerified,
            OffsetDateTime createdAt
    ) {
        public static Summary from(User user) {
            return new Summary(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getRole(),
                    user.isActive(), user.isEmailVerified(), OffsetDateTime.ofInstant(user.getCreatedAt(), VN));
        }
    }

    /**
     * Trang chi tiết: các trường của danh sách cộng `address` và thống kê đơn. `orderCount` là mọi đơn, `completedOrderCount` và
     * `totalSpent` chỉ tính đơn COMPLETED (đơn đã hoàn trả không tính). Vẫn không có mật khẩu, OTP, token_version.
     */
    public record Detail(
            Long id,
            String email,
            String fullName,
            String phone,
            String address,
            Role role,
            boolean isActive,
            boolean emailVerified,
            OffsetDateTime createdAt,
            long orderCount,
            long completedOrderCount,
            BigDecimal totalSpent
    ) {
        public static Detail from(User user, UserOrderStats stats) {
            return new Detail(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getAddress(),
                    user.getRole(), user.isActive(), user.isEmailVerified(),
                    OffsetDateTime.ofInstant(user.getCreatedAt(), VN),
                    stats.orderCount(), stats.completedOrderCount(), stats.totalSpent());
        }
    }
}
