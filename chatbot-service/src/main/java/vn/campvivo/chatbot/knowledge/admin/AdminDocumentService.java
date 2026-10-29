package vn.campvivo.chatbot.knowledge.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.campvivo.chatbot.common.config.ChatbotProperties;
import vn.campvivo.chatbot.common.dto.PageQuery;
import vn.campvivo.chatbot.common.dto.PageResponse;
import vn.campvivo.chatbot.common.exception.BusinessException;
import vn.campvivo.chatbot.common.exception.ErrorCode;
import vn.campvivo.chatbot.knowledge.ChunkRepository;
import vn.campvivo.chatbot.knowledge.DocumentRepository;
import vn.campvivo.chatbot.knowledge.DocumentType;
import vn.campvivo.chatbot.knowledge.IngestionService;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.DocumentFile;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.DocumentStatus;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.KnowledgeDocument;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.NewDocument;
import vn.campvivo.chatbot.knowledge.KnowledgeSearch;
import vn.campvivo.chatbot.knowledge.admin.DocumentDtos.DocumentDetailResponse;
import vn.campvivo.chatbot.knowledge.admin.DocumentDtos.DocumentResponse;
import vn.campvivo.chatbot.knowledge.admin.DocumentDtos.RetrievalHitResponse;
import vn.campvivo.chatbot.knowledge.admin.DocumentDtos.UpdateDocumentRequest;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Quản lý tài liệu cho bot (FR-28, api.md 4.12). Lưu xong mới giao cho {@link IngestionService} xử lý nền.
 */
@Service
public class AdminDocumentService {

    private static final Logger log = LoggerFactory.getLogger(AdminDocumentService.class);
    private static final int PREVIEW_CHUNKS = 20;
    private static final int TITLE_MAX = 255;

    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final IngestionService ingestion;
    private final KnowledgeSearch search;
    private final ChatbotProperties props;

    public AdminDocumentService(DocumentRepository documents, ChunkRepository chunks, IngestionService ingestion,
                                KnowledgeSearch search, ChatbotProperties props) {
        this.documents = documents;
        this.chunks = chunks;
        this.ingestion = ingestion;
        this.search = search;
        this.props = props;
    }

    public PageResponse<DocumentResponse> list(String q, DocumentStatus status, Boolean isActive, PageQuery page) {
        List<DocumentResponse> data = documents.page(q, status, isActive, page).stream()
                .map(DocumentResponse::of).toList();
        return PageResponse.of(data, page.page(), page.limit(), documents.count(q, status, isActive));
    }

    public DocumentDetailResponse get(long id) {
        return DocumentDetailResponse.of(find(id), chunks.first(id, PREVIEW_CHUNKS));
    }

    public DocumentResponse create(MultipartFile file, String title, String description, long adminId) {
        Upload upload = readUpload(file);
        documents.findIdBySha256(upload.sha256, null).ifPresent(existing -> {
            throw duplicate(existing);
        });
        String finalTitle = title == null || title.isBlank() ? baseName(upload.fileName) : title.trim();
        validateMeta(finalTitle, description);
        long id;
        try {
            id = documents.insert(new NewDocument(finalTitle, blankToNull(description), upload.fileName,
                    upload.type.contentType(), upload.content.length, upload.sha256, upload.content, adminId));
        } catch (DuplicateKeyException e) {
            throw duplicate(documents.findIdBySha256(upload.sha256, null).orElse(null));
        }
        ingestion.processAsync(id);
        return DocumentResponse.of(find(id));
    }

    public DocumentResponse update(long id, UpdateDocumentRequest req) {
        find(id);
        documents.updateMeta(id, req.title().trim(), blankToNull(req.description()), req.isActive());
        return DocumentResponse.of(find(id));
    }

    public DocumentResponse replaceFile(long id, MultipartFile file) {
        requireNotProcessing(find(id));
        Upload upload = readUpload(file);
        documents.findIdBySha256(upload.sha256, id).ifPresent(existing -> {
            throw duplicate(existing);
        });
        try {
            documents.replaceFile(id, upload.fileName, upload.type.contentType(), upload.content.length,
                    upload.sha256, upload.content);
        } catch (DuplicateKeyException e) {
            throw duplicate(documents.findIdBySha256(upload.sha256, id).orElse(null));
        }
        ingestion.processAsync(id);
        return DocumentResponse.of(find(id));
    }

    public DocumentResponse reindex(long id) {
        requireNotProcessing(find(id));
        documents.markProcessing(id);
        ingestion.processAsync(id);
        return DocumentResponse.of(find(id));
    }

    public void delete(long id) {
        if (documents.delete(id) == 0) {
            throw BusinessException.notFound();
        }
    }

    public DocumentFile file(long id) {
        return documents.findFile(id).orElseThrow(BusinessException::notFound);
    }

    public List<RetrievalHitResponse> searchTest(String question) {
        try {
            return search.search(question).stream().map(RetrievalHitResponse::of).toList();
        } catch (RuntimeException e) {
            log.warn("Hỏi thử tài liệu lỗi: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    private KnowledgeDocument find(long id) {
        return documents.findById(id).orElseThrow(BusinessException::notFound);
    }

    private static void requireNotProcessing(KnowledgeDocument d) {
        if (d.status() == DocumentStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.DOCUMENT_PROCESSING);
        }
    }

    private Upload readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                    Map.of("fieldErrors", Map.of("file", "Vui lòng chọn tài liệu.")));
        }
        long max = props.chatbot().knowledge().maxFileSize().toBytes();
        if (file.getSize() > max) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "Tài liệu vượt quá " + (max / (1024 * 1024)) + "MB.", Map.of("maxBytes", max));
        }
        String fileName = cleanFileName(file.getOriginalFilename());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        DocumentType type = DocumentType.detect(fileName, content)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE));
        return new Upload(fileName, type, content, sha256(content));
    }

    private static void validateMeta(String title, String description) {
        if (title.length() > TITLE_MAX) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                    Map.of("fieldErrors", Map.of("title", "Tiêu đề tối đa 255 ký tự.")));
        }
        if (description != null && description.length() > 1000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                    Map.of("fieldErrors", Map.of("description", "Mô tả tối đa 1000 ký tự.")));
        }
    }

    private static BusinessException duplicate(Long existingId) {
        return new BusinessException(ErrorCode.DOCUMENT_DUPLICATE,
                existingId == null ? null : Map.of("documentId", existingId));
    }

    /** Tên file chỉ giữ phần tên (bỏ đường dẫn trình duyệt cũ gửi kèm). */
    private static String cleanFileName(String original) {
        if (original == null || original.isBlank()) {
            return "tai-lieu";
        }
        String name = Paths.get(original.replace('\\', '/')).getFileName().toString().strip();
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.length() > TITLE_MAX ? base.substring(0, TITLE_MAX) : base;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Upload(String fileName, DocumentType type, byte[] content, String sha256) {}

}
