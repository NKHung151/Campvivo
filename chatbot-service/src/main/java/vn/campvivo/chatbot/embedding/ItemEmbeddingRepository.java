package vn.campvivo.chatbot.embedding;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.campvivo.chatbot.common.util.PgVector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Bảng `chatbot_item_embeddings` (database.md 3.24).
 */
@Repository
public class ItemEmbeddingRepository {

    public enum ItemType { PRODUCT, COMBO }

    private final JdbcClient jdbc;

    public ItemEmbeddingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** item_id → content_hash đã embed. */
    public Map<Long, String> hashes(ItemType type) {
        Map<Long, String> result = new HashMap<>();
        jdbc.sql("SELECT item_id, content_hash FROM chatbot_item_embeddings WHERE item_type = ?")
                .param(type.name())
                .query(rs -> {
                    result.put(rs.getLong("item_id"), rs.getString("content_hash"));
                });
        return result;
    }

    public void upsert(ItemType type, long itemId, String contentHash, float[] embedding) {
        jdbc.sql("""
                        INSERT INTO chatbot_item_embeddings (item_type, item_id, content_hash, embedding, updated_at)
                        VALUES (?, ?, ?, CAST(? AS vector), now())
                        ON CONFLICT (item_type, item_id)
                        DO UPDATE SET content_hash = EXCLUDED.content_hash, embedding = EXCLUDED.embedding,
                                      updated_at = now()
                        """)
                .params(type.name(), itemId, contentHash, PgVector.toLiteral(embedding))
                .update();
    }

    /** Xoá dòng của mặt hàng không còn đang bán. */
    public int deleteExcept(ItemType type, Collection<Long> keepIds) {
        if (keepIds.isEmpty()) {
            return jdbc.sql("DELETE FROM chatbot_item_embeddings WHERE item_type = ?").param(type.name()).update();
        }
        return jdbc.sql("DELETE FROM chatbot_item_embeddings WHERE item_type = :type AND item_id NOT IN (:ids)")
                .param("type", type.name())
                .param("ids", keepIds)
                .update();
    }
}
