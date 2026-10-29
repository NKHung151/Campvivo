package vn.campvivo.chatbot.knowledge;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import vn.campvivo.chatbot.common.util.Times;
import vn.campvivo.chatbot.common.dto.PageQuery;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.DocumentFile;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.DocumentStatus;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.KnowledgeDocument;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.NewDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bảng `chatbot_documents`. Cột `content` (file gốc) chỉ đọc khi cần ({@link #findFile}).
 */
@Repository
public class DocumentRepository {

    private static final String COLUMNS = """
            id, title, description, file_name, content_type, size_bytes, status, error_message, chunk_count,
            is_active, created_at, updated_at
            """;
    private static final int ERROR_MAX_CHARS = 1000;

    private static final RowMapper<KnowledgeDocument> MAPPER = (rs, i) -> new KnowledgeDocument(
            rs.getLong("id"), rs.getString("title"), rs.getString("description"), rs.getString("file_name"),
            rs.getString("content_type"), rs.getLong("size_bytes"), DocumentStatus.valueOf(rs.getString("status")),
            rs.getString("error_message"), rs.getInt("chunk_count"), rs.getBoolean("is_active"),
            Times.vn(rs, "created_at"), Times.vn(rs, "updated_at"));

    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(NewDocument d) {
        return jdbc.sql("""
                        INSERT INTO chatbot_documents (title, description, file_name, content_type, size_bytes, sha256,
                                                       content, status, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'PROCESSING', ?)
                        RETURNING id
                        """)
                .params(d.title(), d.description(), d.fileName(), d.contentType(), d.sizeBytes(), d.sha256(),
                        d.content(), d.createdBy())
                .query(Long.class)
                .single();
    }

    public Optional<KnowledgeDocument> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM chatbot_documents WHERE id = ?").param(id)
                .query(MAPPER).optional();
    }

    public Optional<DocumentFile> findFile(long id) {
        return jdbc.sql("SELECT file_name, content_type, content FROM chatbot_documents WHERE id = ?").param(id)
                .query((rs, i) -> new DocumentFile(rs.getString("file_name"), rs.getString("content_type"),
                        rs.getBytes("content")))
                .optional();
    }

    /** id của tài liệu có cùng nội dung (trừ chính nó khi thay file). */
    public Optional<Long> findIdBySha256(String sha256, Long exceptId) {
        return jdbc.sql("SELECT id FROM chatbot_documents WHERE sha256 = ? AND id <> ?")
                .params(sha256, exceptId == null ? -1L : exceptId)
                .query(Long.class).optional();
    }

    public List<KnowledgeDocument> page(String q, DocumentStatus status, Boolean active, PageQuery page) {
        Filter f = filter(q, status, active);
        return jdbc.sql("SELECT " + COLUMNS + " FROM chatbot_documents" + f.where
                        + " ORDER BY updated_at DESC, id DESC LIMIT " + page.limit() + " OFFSET " + page.offset())
                .params(f.params)
                .query(MAPPER).list();
    }

    public long count(String q, DocumentStatus status, Boolean active) {
        Filter f = filter(q, status, active);
        return jdbc.sql("SELECT count(*) FROM chatbot_documents" + f.where).params(f.params)
                .query(Long.class).single();
    }

    public int updateMeta(long id, String title, String description, boolean active) {
        return jdbc.sql("""
                        UPDATE chatbot_documents SET title = ?, description = ?, is_active = ?, updated_at = now()
                        WHERE id = ?
                        """)
                .params(title, description, active, id).update();
    }

    /** Thay file gốc và đưa về PROCESSING — bot tạm không dùng tài liệu này cho tới khi nạp xong. */
    public int replaceFile(long id, String fileName, String contentType, long sizeBytes, String sha256,
                           byte[] content) {
        return jdbc.sql("""
                        UPDATE chatbot_documents
                        SET file_name = ?, content_type = ?, size_bytes = ?, sha256 = ?, content = ?,
                            status = 'PROCESSING', error_message = NULL, updated_at = now()
                        WHERE id = ?
                        """)
                .params(fileName, contentType, sizeBytes, sha256, content, id).update();
    }

    public int markProcessing(long id) {
        return jdbc.sql("""
                        UPDATE chatbot_documents SET status = 'PROCESSING', error_message = NULL, updated_at = now()
                        WHERE id = ?
                        """).param(id).update();
    }

    public void markReady(long id, int chunkCount) {
        jdbc.sql("""
                        UPDATE chatbot_documents SET status = 'READY', error_message = NULL, chunk_count = ?,
                                                     updated_at = now()
                        WHERE id = ?
                        """).params(chunkCount, id).update();
    }

    public void markFailed(long id, String message) {
        String msg = message == null ? "Lỗi không xác định" : message;
        jdbc.sql("UPDATE chatbot_documents SET status = 'FAILED', error_message = ?, updated_at = now() WHERE id = ?")
                .params(msg.length() > ERROR_MAX_CHARS ? msg.substring(0, ERROR_MAX_CHARS) : msg, id).update();
    }

    public int delete(long id) {
        return jdbc.sql("DELETE FROM chatbot_documents WHERE id = ?").param(id).update();
    }

    private record Filter(String where, List<Object> params) {}

    private static Filter filter(String q, DocumentStatus status, Boolean active) {
        List<String> conds = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            conds.add("(title ILIKE ? OR file_name ILIKE ?)");
            String like = "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            params.add(like);
            params.add(like);
        }
        if (status != null) {
            conds.add("status = ?");
            params.add(status.name());
        }
        if (active != null) {
            conds.add("is_active = ?");
            params.add(active);
        }
        return new Filter(conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds), params);
    }
}
