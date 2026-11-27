package vn.campvivo.notification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ghi `notifications` bằng SQL (database.md 3.20, D-43): mỗi thao tác là MỘT câu cho mọi người nhận (`unnest` mảng id) và
 * trả lại `(id, user_id)` các dòng bị ảnh hưởng để đẩy WebSocket. Dòng gộp dùng `ON CONFLICT` trên unique một phần
 * `uq_notifications_user_group` nên hai sự kiện tới cùng lúc vẫn chỉ có một dòng. Gọi trong transaction của
 * {@link NotificationService}.
 */
@Repository
public class NotificationWriter {

    /** Một dòng vừa ghi; `inserted = false` = dòng gộp đã có, vừa được cập nhật. */
    public record Written(long id, long userId, boolean inserted) {}

    private static final String INSERT = """
            INSERT INTO notifications (user_id, type, title, content, link, ref_type, ref_id, created_at)
            SELECT u, ?, ?, ?, ?, ?, ?, ? FROM unnest(?) AS u
            RETURNING id, user_id, TRUE AS inserted
            """;

    // xmax = 0 chỉ đúng với dòng vừa INSERT (dòng bị UPDATE mang xmax của transaction hiện tại).
    private static final String UPSERT_GROUP = """
            INSERT INTO notifications (user_id, type, title, content, link, ref_type, ref_id, group_key, created_at)
            SELECT u, ?, ?, ?, ?, ?, ?, ?, ? FROM unnest(?) AS u
            ON CONFLICT (user_id, group_key) WHERE group_key IS NOT NULL DO UPDATE SET
                event_count = notifications.event_count + 1,
                title = EXCLUDED.title,
                content = EXCLUDED.content,
                link = EXCLUDED.link,
                created_at = EXCLUDED.created_at,
                is_read = FALSE
            RETURNING id, user_id, (xmax = 0) AS inserted
            """;

    // Chỉ dòng gộp đang mở (group_key khác NULL) và nội dung thật sự đổi; không đụng is_read / event_count / created_at.
    private static final String REFRESH_GROUP = """
            UPDATE notifications SET title = ?, content = ?
            WHERE type = ? AND group_key = ? AND (title IS DISTINCT FROM ? OR content IS DISTINCT FROM ?)
            RETURNING id, user_id, FALSE AS inserted
            """;

    private static final String RESOLVE_OPEN = """
            UPDATE notifications SET resolved_at = ?, resolved_by = ?, group_key = NULL
            WHERE type = ? AND ref_type = ? AND ref_id IS NOT DISTINCT FROM CAST(? AS BIGINT) AND resolved_at IS NULL
            RETURNING id, user_id, FALSE AS inserted
            """;

    private static final String UNREAD_COUNTS = """
            SELECT user_id, count(*) FROM notifications
            WHERE user_id = ANY(?) AND NOT is_read AND resolved_at IS NULL
            GROUP BY user_id
            """;

    /** Loại không cần xử lý: đọc là đóng dòng gộp (D-43). Hằng từ enum, không phải dữ liệu người dùng. */
    private static final String CLOSE_ON_READ_TYPES = Arrays.stream(NotificationType.values())
            .filter(t -> !t.actionable())
            .map(t -> "'" + t.name() + "'")
            .collect(Collectors.joining(", "));

    private static final String MARK_ALL_READ = """
            UPDATE notifications
            SET is_read = TRUE, group_key = CASE WHEN type IN (%s) THEN NULL ELSE group_key END
            WHERE user_id = ? AND NOT is_read
            """.formatted(CLOSE_ON_READ_TYPES);

    private static final RowMapper<Written> WRITTEN = (rs, i) ->
            new Written(rs.getLong("id"), rs.getLong("user_id"), rs.getBoolean("inserted"));

    private final JdbcTemplate jdbc;

    public NotificationWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Một thông báo (không gộp) cho mỗi người trong `userIds` (id không được trùng). */
    public List<Written> insert(Collection<Long> userIds, NotificationMessage m, OffsetDateTime now) {
        return jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT);
            int i = bindContent(ps, m);
            ps.setObject(i++, now);
            ps.setArray(i, bigintArray(con, userIds));
            return ps;
        }, WRITTEN);
    }

    /** Gộp vào dòng đang mở `m.groupKey()` của từng người, chưa có thì tạo (id không được trùng — ON CONFLICT không cho). */
    public List<Written> upsertGroup(Collection<Long> userIds, NotificationMessage m, OffsetDateTime now) {
        if (m.groupKey() == null) {
            throw new IllegalArgumentException("upsertGroup cần groupKey");
        }
        return jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(UPSERT_GROUP);
            int i = bindContent(ps, m);
            ps.setString(i++, m.groupKey());
            ps.setObject(i++, now);
            ps.setArray(i, bigintArray(con, userIds));
            return ps;
        }, WRITTEN);
    }

    /**
     * Sửa tiêu đề / nội dung của mọi dòng gộp đang mở `m.groupKey()` (mọi người nhận) mà KHÔNG báo lại: giữ `is_read`,
     * `event_count`, `created_at`. Dùng khi tình trạng đổi theo hướng bớt khẩn (vd. hàng chờ kiểm duyệt giảm). Không tạo dòng.
     */
    public List<Written> refreshGroup(NotificationMessage m) {
        if (m.groupKey() == null) {
            throw new IllegalArgumentException("refreshGroup cần groupKey");
        }
        return jdbc.query(REFRESH_GROUP, WRITTEN, m.title(), m.content(), m.type().name(), m.groupKey(), m.title(),
                m.content());
    }

    /**
     * Đánh dấu đã xử lý MỌI dòng còn mở của một đối tượng (mọi người nhận) và đóng nhóm gộp của chúng (D-43).
     * `resolvedBy` null = Hệ thống; `refId` null chỉ dùng cho `REVIEW_QUEUE`.
     */
    public List<Written> resolveOpen(NotificationType type, NotificationRefType refType, Long refId, Long resolvedBy,
                                     OffsetDateTime now) {
        return jdbc.query(RESOLVE_OPEN, WRITTEN, now, resolvedBy, type.name(), refType.name(), refId);
    }

    /** `unreadCount` của từng người; người không có thông báo chưa đọc không có trong map. */
    public Map<Long, Long> unreadCounts(Collection<Long> userIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (userIds.isEmpty()) {
            return counts;
        }
        jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(UNREAD_COUNTS);
            ps.setArray(1, bigintArray(con, userIds));
            return ps;
        }, rs -> {
            counts.put(rs.getLong(1), rs.getLong(2));
        });
        return counts;
    }

    /** Đọc tất cả của một người; trả số dòng vừa đổi. */
    public int markAllRead(Long userId) {
        return jdbc.update(MARK_ALL_READ, userId);
    }

    /** type, title, content, link, ref_type, ref_id → trả vị trí tham số kế tiếp. */
    private static int bindContent(PreparedStatement ps, NotificationMessage m) throws java.sql.SQLException {
        ps.setString(1, m.type().name());
        ps.setString(2, m.title());
        ps.setString(3, m.content());
        ps.setString(4, m.link());
        ps.setString(5, m.refType() == null ? null : m.refType().name());
        ps.setObject(6, m.refId(), java.sql.Types.BIGINT);
        return 7;
    }

    private static Array bigintArray(java.sql.Connection con, Collection<Long> ids) throws java.sql.SQLException {
        return con.createArrayOf("bigint", ids.toArray(Long[]::new));
    }
}
