package vn.campvivo.chatbot.knowledge;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.campvivo.chatbot.common.util.PgVector;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.ChunkView;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.NewChunk;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.RetrievalHit;

import java.util.List;
import java.util.UUID;

/**
 * Bảng `chatbot_document_chunks`. Truy hồi chỉ lấy chunk của tài liệu đang bật và READY (database.md 3.24).
 */
@Repository
public class ChunkRepository {

    private final JdbcClient jdbc;

    public ChunkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Xoá chunk cũ rồi chèn chunk mới. Gọi trong transaction để bot không thấy tài liệu nửa cũ nửa mới. */
    public void replace(long documentId, List<NewChunk> chunks) {
        jdbc.sql("DELETE FROM chatbot_document_chunks WHERE document_id = ?").param(documentId).update();
        for (NewChunk c : chunks) {
            jdbc.sql("""
                            INSERT INTO chatbot_document_chunks (id, document_id, chunk_index, content, metadata, embedding)
                            VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS vector))
                            """)
                    .params(UUID.randomUUID(), documentId, c.index(), c.content(),
                            c.page() == null ? "{}" : "{\"page\": " + c.page() + "}",
                            PgVector.toLiteral(c.embedding()))
                    .update();
        }
    }

    public List<ChunkView> first(long documentId, int limit) {
        return jdbc.sql("""
                        SELECT chunk_index, (metadata ->> 'page')::int AS page, content
                        FROM chatbot_document_chunks WHERE document_id = ? ORDER BY chunk_index LIMIT ?
                        """)
                .params(documentId, limit)
                .query((rs, i) -> new ChunkView(rs.getInt("chunk_index"), (Integer) rs.getObject("page"),
                        rs.getString("content")))
                .list();
    }

    /** Các chunk gần nhất với vector câu hỏi, chỉ của tài liệu đang dùng; score = 1 − khoảng cách cosine. */
    public List<RetrievalHit> nearest(float[] queryEmbedding, int topK) {
        String q = PgVector.toLiteral(queryEmbedding);
        return jdbc.sql("""
                        SELECT d.id AS document_id, d.title, (ch.metadata ->> 'page')::int AS page, ch.content,
                               1 - (ch.embedding <=> CAST(:q AS vector)) AS score
                        FROM chatbot_document_chunks ch
                        JOIN chatbot_documents d ON d.id = ch.document_id
                        WHERE d.is_active AND d.status = 'READY'
                        ORDER BY ch.embedding <=> CAST(:q AS vector)
                        LIMIT :k
                        """)
                .param("q", q)
                .param("k", topK)
                .query((rs, i) -> new RetrievalHit(rs.getLong("document_id"), rs.getString("title"),
                        (Integer) rs.getObject("page"), rs.getString("content"), rs.getDouble("score")))
                .list();
    }
}
