package vn.campvivo.chatbot.security;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Đọc (chỉ đọc) thông tin cần cho xác thực từ bảng `users` của backend (D-28).
 */
@Repository
public class UserAccountReader {

    private final JdbcClient jdbc;

    public UserAccountReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findById(long id) {
        return jdbc.sql("SELECT id, role, is_active, token_version FROM users WHERE id = ?")
                .param(id)
                .query((rs, i) -> new UserAccount(rs.getLong("id"), rs.getString("role"),
                        rs.getBoolean("is_active"), rs.getInt("token_version")))
                .optional();
    }

    public record UserAccount(long id, String role, boolean active, int tokenVersion) {}
}
