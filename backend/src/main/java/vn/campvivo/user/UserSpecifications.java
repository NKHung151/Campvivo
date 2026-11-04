package vn.campvivo.user;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Điều kiện lọc cho danh sách người dùng của Admin (api.md mục 4.10). Mỗi hàm trả null khi không có điều kiện để ghép bằng
 * {@code Specification.where(...).and(...)}. Từ khoá được thoát ký tự đại diện của LIKE: `%`, `_`, `\\` trong ô tìm kiếm là chữ
 * thường chứ không phải ký tự đại diện (khác {@code UserService#findIdsByKeyword} dùng cho tìm đơn, vốn bỏ các ký tự này).
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /** Email hoặc họ tên chứa `q`, không phân biệt hoa thường. `q` rỗng / toàn khoảng trắng → không lọc. */
    public static Specification<User> matchesKeyword(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), pattern, '\\'),
                cb.like(cb.lower(root.get("fullName")), pattern, '\\'));
    }

    public static Specification<User> hasRole(Role role) {
        return role == null ? null : (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    /** `active` null → cả tài khoản đang hoạt động lẫn bị khoá. */
    public static Specification<User> isActive(Boolean active) {
        return active == null ? null : (root, query, cb) -> cb.equal(root.get("active"), active);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
