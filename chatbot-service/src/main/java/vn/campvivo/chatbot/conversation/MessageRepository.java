package vn.campvivo.chatbot.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.campvivo.chatbot.common.util.Times;
import vn.campvivo.chatbot.conversation.ConversationModels.ChatRole;
import vn.campvivo.chatbot.conversation.ConversationModels.MessageResponse;
import vn.campvivo.chatbot.conversation.ConversationModels.NewMessage;
import vn.campvivo.chatbot.tools.ChatCard;
import vn.campvivo.chatbot.tools.ChatSource;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Bảng `chatbot_messages` — cũng là bộ nhớ hội thoại gửi kèm cho model (không dùng bảng chat memory của Spring AI,
 * để mỗi tin lưu được cả thẻ, nguồn, số token).
 */
@Repository
public class MessageRepository {

    private static final TypeReference<List<ChatCard>> CARDS = new TypeReference<>() { };
    private static final TypeReference<List<ChatSource>> SOURCES = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<MessageResponse> mapper;

    public MessageRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.mapper = (rs, i) -> new MessageResponse(rs.getLong("id"), ChatRole.valueOf(rs.getString("role")),
                rs.getString("content"), read(rs.getString("cards"), CARDS), read(rs.getString("sources"), SOURCES),
                Times.vn(rs, "created_at"));
    }

    public MessageResponse insert(NewMessage m) {
        return jdbc.sql("""
                        INSERT INTO chatbot_messages (conversation_id, role, content, cards, sources, prompt_tokens,
                                                      completion_tokens)
                        VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)
                        RETURNING id, role, content, cards::text AS cards, sources::text AS sources, created_at
                        """)
                .params(m.conversationId(), m.role().name(), m.content(), write(m.cards()), write(m.sources()),
                        m.promptTokens(), m.completionTokens())
                .query(mapper).single();
    }

    public List<MessageResponse> list(UUID conversationId) {
        return jdbc.sql("""
                        SELECT id, role, content, cards::text AS cards, sources::text AS sources, created_at
                        FROM chatbot_messages WHERE conversation_id = ? ORDER BY created_at, id
                        """)
                .param(conversationId)
                .query(mapper).list();
    }

    /** `limit` tin gần nhất, cũ trước — lịch sử gửi kèm cho model. */
    public List<MessageResponse> recent(UUID conversationId, int limit) {
        List<MessageResponse> latest = new ArrayList<>(jdbc.sql("""
                        SELECT id, role, content, cards::text AS cards, sources::text AS sources, created_at
                        FROM chatbot_messages WHERE conversation_id = ? ORDER BY created_at DESC, id DESC LIMIT ?
                        """)
                .params(conversationId, limit)
                .query(mapper).list());
        Collections.reverse(latest);
        return latest;
    }

    /** Số tin khách (USER) gửi từ `since` trên mọi hội thoại của khách — cho giới hạn tần suất. */
    public long countUserMessagesSince(long userId, OffsetDateTime since) {
        return jdbc.sql("""
                        SELECT count(*) FROM chatbot_messages m
                        JOIN chatbot_conversations c ON c.id = m.conversation_id
                        WHERE c.user_id = ? AND m.role = 'USER' AND m.created_at >= ?
                        """)
                .params(userId, since)
                .query(Long.class).single();
    }

    /** Thời điểm tin USER thứ `n` gần nhất kể từ `since` (để tính phải chờ bao lâu). */
    public OffsetDateTime nthLatestUserMessageSince(long userId, OffsetDateTime since, int n) {
        return jdbc.sql("""
                        SELECT m.created_at FROM chatbot_messages m
                        JOIN chatbot_conversations c ON c.id = m.conversation_id
                        WHERE c.user_id = ? AND m.role = 'USER' AND m.created_at >= ?
                        ORDER BY m.created_at DESC OFFSET ? LIMIT 1
                        """)
                .params(userId, since, n - 1)
                .query(OffsetDateTime.class).optional().orElse(null);
    }

    private <T> List<T> read(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
