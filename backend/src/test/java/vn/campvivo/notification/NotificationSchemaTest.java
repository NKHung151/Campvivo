package vn.campvivo.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import vn.campvivo.support.PostgresTestContainerConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ràng buộc DB của V17__notifications.sql (database.md 3.20) trên PostgreSQL thật, `ddl-auto=validate`.
 * Context khởi động được nghĩa là entity Notification khớp migration.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class NotificationSchemaTest {

    @TestConfiguration
    @EntityScan(basePackageClasses = Notification.class)
    @EnableJpaRepositories(basePackageClasses = NotificationRepository.class)
    static class OnlyNotificationModule {}

    private static final String INSERT = """
            INSERT INTO notifications (user_id, type, title, content, ref_type, ref_id, group_key, event_count, resolved_at, resolved_by)
            VALUES (?, ?, 'Tiêu đề', 'Nội dung', ?, ?, ?, ?, ?, ?)
            """;

    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long staffId;

    @BeforeEach
    void seed() {
        userId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name) VALUES ('nt-schema@test.vn', 'x', 'Khách') RETURNING id
                """, Long.class);
        staffId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role) VALUES ('nt-schema-staff@test.vn', 'x', 'NV', 'EMPLOYEE')
                RETURNING id
                """, Long.class);
    }

    private int insert(String type, String refType, Long refId, String groupKey, int eventCount, boolean resolved,
                       Long resolvedBy) {
        return jdbc.update(INSERT, userId, type, refType, refId, groupKey, eventCount,
                resolved ? java.sql.Timestamp.from(java.time.Instant.now()) : null, resolvedBy);
    }

    @Test
    void savesWithDefaultsAndMapsToEntity() {
        insert("ORDER_STATUS", "ORDER", 108L, null, 1, false, null);

        Notification n = notifications.findAll().stream().filter(x -> x.getUserId() == userId).findFirst().orElseThrow();
        assertThat(n.getType()).isEqualTo(NotificationType.ORDER_STATUS);
        assertThat(n.getRefType()).isEqualTo(NotificationRefType.ORDER);
        assertThat(n.getRefId()).isEqualTo(108L);
        assertThat(n.isRead()).isFalse();
        assertThat(n.getEventCount()).isEqualTo(1);
        assertThat(n.getCreatedAt()).isNotNull();
        assertThat(n.getResolvedAt()).isNull();
        assertThat(notifications.countByUserIdAndReadFalseAndResolvedAtIsNull(userId)).isEqualTo(1);
    }

    // Mỗi test chỉ một vi phạm và đặt cuối: test chạy trong một transaction, lỗi đầu tiên làm hỏng các câu sau.

    @Test
    void rejectsUnknownType() {
        assertThatThrownBy(() -> insert("PROMO", "ORDER", 1L, null, 1, false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownRefType() {
        assertThatThrownBy(() -> insert("ORDER_STATUS", "USER", 1L, null, 1, false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsZeroEventCount() {
        assertThatThrownBy(() -> insert("SUPPORT_MESSAGE", "CONVERSATION", 1L, "SUPPORT:1", 0, false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void resolvedByRequiresResolvedAt() {
        // Hệ thống xử lý: resolved_at có, resolved_by NULL — hợp lệ
        assertThat(insert("ORDER_NEW", "ORDER", 2L, null, 1, true, null)).isEqualTo(1);
        assertThat(insert("ORDER_NEW", "ORDER", 3L, null, 1, true, staffId)).isEqualTo(1);

        assertThatThrownBy(() -> insert("ORDER_NEW", "ORDER", 1L, null, 1, false, staffId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneOpenGroupPerUserButClosedGroupsAreFree() {
        insert("SUPPORT_MESSAGE", "CONVERSATION", 1L, "SUPPORT:1", 1, false, null);
        // group_key NULL (nhóm đã đóng / thông báo không gộp) thì không bị ràng buộc; khoá khác cũng vậy
        insert("SUPPORT_MESSAGE", "CONVERSATION", 1L, null, 3, false, null);
        insert("SUPPORT_MESSAGE", "CONVERSATION", 1L, null, 1, false, null);
        insert("SUPPORT_MESSAGE", "CONVERSATION", 2L, "SUPPORT:2", 1, false, null);
        assertThat(countOf(userId)).isEqualTo(4);

        assertThatThrownBy(() -> insert("SUPPORT_MESSAGE", "CONVERSATION", 1L, "SUPPORT:1", 1, false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingRecipientDeletesNotificationsAndDeletingResolverKeepsThem() {
        insert("ORDER_NEW", "ORDER", 3L, null, 1, true, staffId);
        jdbc.update("DELETE FROM users WHERE id = ?", staffId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND resolved_by IS NULL "
                + "AND resolved_at IS NOT NULL", Integer.class, userId)).isEqualTo(1);

        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        assertThat(countOf(userId)).isZero();
    }

    /** Chỉ đếm dòng của người dùng do test tạo — DB dùng chung còn thông báo do lớp test khác sinh ra (vd. LOW_STOCK). */
    private long countOf(long user) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?", Long.class, user);
    }

    @Test
    void promotionTablesHaveAnnouncedAt() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE column_name = 'announced_at' AND table_name IN ('vouchers', 'discount_campaigns') AND is_nullable = 'YES'
                """, Integer.class)).isEqualTo(2);
    }
}
