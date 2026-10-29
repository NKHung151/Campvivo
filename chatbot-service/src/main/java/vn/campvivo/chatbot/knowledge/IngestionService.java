package vn.campvivo.chatbot.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.campvivo.chatbot.common.config.ChatbotProperties;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.DocumentFile;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.NewChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Nạp tài liệu chạy nền (FR-28): đọc file → làm sạch → chia đoạn (~chunk-size token) → embed theo lô →
 * thay chunk cũ bằng chunk mới + READY trong MỘT transaction. Lỗi → FAILED kèm lý do để admin thấy.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int EMBED_BATCH = 64;
    /** Đoạn ngắn hơn (ký tự) bị bỏ — thường là số trang, tiêu đề lặp. */
    private static final int MIN_CHUNK_CHARS = 20;

    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final EmbeddingModel embeddingModel;
    private final TransactionTemplate tx;
    private final ChatbotProperties props;

    public IngestionService(DocumentRepository documents, ChunkRepository chunks, EmbeddingModel embeddingModel,
                            TransactionTemplate tx, ChatbotProperties props) {
        this.documents = documents;
        this.chunks = chunks;
        this.embeddingModel = embeddingModel;
        this.tx = tx;
        this.props = props;
    }

    /** Gọi SAU khi bản ghi tài liệu đã được lưu (đã commit). */
    @Async
    public void processAsync(long documentId) {
        process(documentId);
    }

    public void process(long documentId) {
        try {
            DocumentFile file = documents.findFile(documentId)
                    .orElseThrow(() -> new IllegalStateException("Tài liệu không còn tồn tại"));
            List<Document> pieces = split(read(file));
            if (pieces.isEmpty()) {
                throw new IngestionException("Không trích được chữ từ tài liệu (file scan/ảnh?). Cần file có chữ.");
            }
            List<NewChunk> newChunks = embed(pieces);
            tx.executeWithoutResult(status -> {
                chunks.replace(documentId, newChunks);
                documents.markReady(documentId, newChunks.size());
            });
            log.info("Đã nạp tài liệu {}: {} đoạn", documentId, newChunks.size());
        } catch (IngestionException e) {
            documents.markFailed(documentId, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Nạp tài liệu {} lỗi", documentId, e);
            documents.markFailed(documentId, "Xử lý tài liệu lỗi: " + e.getMessage());
        }
    }

    List<Document> read(DocumentFile file) {
        DocumentType type = DocumentType.fromContentType(file.contentType())
                .orElseThrow(() -> new IngestionException("Loại tài liệu không hỗ trợ"));
        Resource resource = new NamedByteArrayResource(file.content(), file.fileName());
        try {
            if (type == DocumentType.PDF) {
                // Mỗi trang một Document, metadata có số trang
                return new PagePdfDocumentReader(resource,
                        PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build()).get();
            }
            return new TikaDocumentReader(resource).get();
        } catch (RuntimeException e) {
            throw new IngestionException("Không đọc được nội dung tài liệu. File có thể bị hỏng hoặc có mật khẩu.");
        }
    }

    List<Document> split(List<Document> docs) {
        List<Document> cleaned = new ArrayList<>();
        for (Document d : docs) {
            String text = TextCleaner.clean(d.getText());
            if (!text.isBlank()) {
                cleaned.add(new Document(text, d.getMetadata()));
            }
        }
        if (cleaned.isEmpty()) {
            return List.of();
        }
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(props.chatbot().knowledge().chunkSize())
                .withMinChunkSizeChars(200)
                .withMinChunkLengthToEmbed(MIN_CHUNK_CHARS)
                .withMaxNumChunks(10_000)
                .withKeepSeparator(true)
                .build();
        return splitter.split(cleaned).stream()
                .filter(d -> d.getText() != null && d.getText().strip().length() >= MIN_CHUNK_CHARS)
                .toList();
    }

    private List<NewChunk> embed(List<Document> pieces) {
        List<NewChunk> result = new ArrayList<>(pieces.size());
        for (int from = 0; from < pieces.size(); from += EMBED_BATCH) {
            List<Document> batch = pieces.subList(from, Math.min(from + EMBED_BATCH, pieces.size()));
            List<float[]> vectors;
            try {
                vectors = embeddingModel.embed(batch.stream().map(Document::getText).toList());
            } catch (RuntimeException e) {
                log.warn("Gọi OpenAI embedding lỗi: {}", e.getMessage());
                throw new IngestionException("Không gọi được dịch vụ AI để xử lý tài liệu (kiểm tra OPENAI_API_KEY "
                        + "và hạn mức). Sửa xong bấm \"Nạp lại\".");
            }
            for (int i = 0; i < batch.size(); i++) {
                Document d = batch.get(i);
                result.add(new NewChunk(from + i, d.getText().strip(), page(d), vectors.get(i)));
            }
        }
        return result;
    }

    private static Integer page(Document d) {
        Object page = d.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
        return page instanceof Number n ? n.intValue() : null;
    }

    /** Lỗi có lý do hiển thị được cho admin. */
    static class IngestionException extends RuntimeException {
        IngestionException(String message) {
            super(message);
        }
    }

    /** Reader của Spring AI cần tên file (metadata, nhận dạng định dạng). */
    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String fileName;

        NamedByteArrayResource(byte[] content, String fileName) {
            super(content);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
