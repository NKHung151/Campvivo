package vn.campvivo.chatbot.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import vn.campvivo.chatbot.common.config.ChatbotProperties;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.RetrievalHit;

import java.util.List;

/**
 * Truy hồi đoạn tài liệu liên quan tới câu hỏi (top-k, lọc theo ngưỡng). Chỉ tài liệu đang bật và READY.
 */
@Service
public class KnowledgeSearch {

    private final ChunkRepository chunks;
    private final EmbeddingModel embeddingModel;
    private final ChatbotProperties props;

    public KnowledgeSearch(ChunkRepository chunks, EmbeddingModel embeddingModel, ChatbotProperties props) {
        this.chunks = chunks;
        this.embeddingModel = embeddingModel;
        this.props = props;
    }

    /** @throws RuntimeException nếu không embed được câu hỏi (nơi gọi quyết định báo lỗi thế nào). */
    public List<RetrievalHit> search(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        var cfg = props.chatbot().knowledge();
        float[] q = embeddingModel.embed(question.trim());
        return chunks.nearest(q, cfg.topK()).stream()
                .filter(h -> h.score() >= cfg.similarityThreshold())
                .toList();
    }
}
