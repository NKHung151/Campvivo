package vn.campvivo.chatbot.conversation;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.campvivo.chatbot.common.util.Times;
import vn.campvivo.chatbot.common.dto.PageQuery;
import vn.campvivo.chatbot.conversation.ConversationModels.ConversationResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bảng `chatbot_conversations`. Mọi truy vấn lọc theo `user_id` = chủ hội thoại (không lộ hội thoại người khác).
 */
@Repository
public class ConversationRepository {

    public static final String DEFAULT_TITLE = "Cuộc trò chuyện mới";

    private static final RowMapper<ConversationResponse> MAPPER = (rs, i) -> new ConversationResponse(
            rs.getObject("id", UUID.class), rs.getString("title"),
            Times.vn(rs, "created_at"), Times.vn(rs, "updated_at"));

    private final JdbcClient jdbc;

    public ConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public ConversationResponse create(UUID id, long userId) {
        return jdbc.sql("""
                        INSERT INTO chatbot_conversations (id, user_id, title) VALUES (?, ?, ?)
                        RETURNING id, title, created_at, updated_at
                        """)
                .params(id, userId, DEFAULT_TITLE)
                .query(MAPPER).single();
    }

    public Optional<ConversationResponse> findOwned(UUID id, long userId) {
        return jdbc.sql("SELECT id, title, created_at, updated_at FROM chatbot_conversations WHERE id = ? AND user_id = ?")
                .params(id, userId)
                .query(MAPPER).optional();
    }

    public List<ConversationResponse> page(long userId, PageQuery page) {
        return jdbc.sql("""
                        SELECT id, title, created_at, updated_at FROM chatbot_conversations WHERE user_id = ?
                        ORDER BY updated_at DESC, created_at DESC LIMIT ? OFFSET ?
                        """)
                .params(userId, page.limit(), page.offset())
                .query(MAPPER).list();
    }

    public long count(long userId) {
        return jdbc.sql("SELECT count(*) FROM chatbot_conversations WHERE user_id = ?").param(userId)
                .query(Long.class).single();
    }

    /** Cập nhật thời điểm có tin mới; đặt tiêu đề nếu vẫn là tiêu đề mặc định. */
    public void touch(UUID id, String titleIfDefault) {
        jdbc.sql("""
                        UPDATE chatbot_conversations
                        SET updated_at = now(), title = CASE WHEN title = ? THEN ? ELSE title END
                        WHERE id = ?
                        """)
                .params(DEFAULT_TITLE, titleIfDefault, id).update();
    }

    public int deleteOwned(UUID id, long userId) {
        return jdbc.sql("DELETE FROM chatbot_conversations WHERE id = ? AND user_id = ?").params(id, userId).update();
    }
}
