package vn.campvivo.chatbot.knowledge;

import java.time.OffsetDateTime;

/**
 * Dữ liệu tài liệu cho bot — bảng chatbot_documents / chatbot_document_chunks (database.md 3.24).
 */
public final class KnowledgeModels {

    private KnowledgeModels() {
    }

    public enum DocumentStatus { PROCESSING, READY, FAILED }

    /** Một tài liệu (không gồm nội dung file). */
    public record KnowledgeDocument(long id, String title, String description, String fileName, String contentType,
                                    long sizeBytes, DocumentStatus status, String errorMessage, int chunkCount,
                                    boolean active, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record DocumentFile(String fileName, String contentType, byte[] content) {}

    public record NewDocument(String title, String description, String fileName, String contentType, long sizeBytes,
                              String sha256, byte[] content, long createdBy) {}

    /** Đoạn đã tách, chờ lưu. `page` null nếu không phải PDF. */
    public record NewChunk(int index, String content, Integer page, float[] embedding) {}

    public record ChunkView(int index, Integer page, String content) {}

    /** Một kết quả truy hồi; score 0..1, cao = giống hơn. */
    public record RetrievalHit(long documentId, String title, Integer page, String content, double score) {}
}
