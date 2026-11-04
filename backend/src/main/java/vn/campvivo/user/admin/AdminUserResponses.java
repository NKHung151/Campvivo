package vn.campvivo.user.admin;

import vn.campvivo.user.Role;
import vn.campvivo.user.User;

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
}
