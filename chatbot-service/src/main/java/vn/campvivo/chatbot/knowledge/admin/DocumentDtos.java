package vn.campvivo.chatbot.knowledge.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.ChunkView;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.DocumentStatus;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.KnowledgeDocument;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.RetrievalHit;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request/response của API admin tài liệu — api.md 4.12 (ChatbotDocument, ChatbotDocumentDetail, RetrievalHit).
 */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentResponse(long id, String title, String description, String fileName, String contentType,
                                   long sizeBytes, DocumentStatus status, String errorMessage, int chunkCount,
                                   boolean isActive, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

        public static DocumentResponse of(KnowledgeDocument d) {
            return new DocumentResponse(d.id(), d.title(), d.description(), d.fileName(), d.contentType(),
                    d.sizeBytes(), d.status(), d.errorMessage(), d.chunkCount(), d.active(), d.createdAt(),
                    d.updatedAt());
        }
    }

    public record DocumentDetailResponse(long id, String title, String description, String fileName,
                                         String contentType, long sizeBytes, DocumentStatus status,
                                         String errorMessage, int chunkCount, boolean isActive,
                                         OffsetDateTime createdAt, OffsetDateTime updatedAt, List<ChunkView> chunks) {

        public static DocumentDetailResponse of(KnowledgeDocument d, List<ChunkView> chunks) {
            return new DocumentDetailResponse(d.id(), d.title(), d.description(), d.fileName(), d.contentType(),
                    d.sizeBytes(), d.status(), d.errorMessage(), d.chunkCount(), d.active(), d.createdAt(),
                    d.updatedAt(), chunks);
        }
    }

    public record UpdateDocumentRequest(
            @NotBlank(message = "Vui lòng nhập tiêu đề.")
            @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự.")
            String title,
            @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự.")
            String description,
            @NotNull(message = "Thiếu trạng thái bật/tắt.")
            Boolean isActive
    ) {}

    public record SearchTestRequest(
            @NotBlank(message = "Vui lòng nhập câu hỏi.")
            @Size(max = 1000, message = "Câu hỏi tối đa 1000 ký tự.")
            String question
    ) {}

    public record RetrievalHitResponse(long documentId, String title, Integer page, String content, double score) {
        public static RetrievalHitResponse of(RetrievalHit h) {
            return new RetrievalHitResponse(h.documentId(), h.title(), h.page(), h.content(),
                    Math.round(h.score() * 1000) / 1000.0);
        }
    }
}
