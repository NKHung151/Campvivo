package vn.campvivo.chatbot.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * Embed câu tìm kiếm của tool. Lỗi gọi OpenAI → null để nơi gọi tìm bằng chữ thay vì làm hỏng cả câu trả lời.
 */
@Component
public class QueryEmbedder {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbedder.class);

    private final EmbeddingModel embeddingModel;

    public QueryEmbedder(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /** null nếu `text` rỗng hoặc không embed được. */
    public float[] embedOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return embeddingModel.embed(text.trim());
        } catch (RuntimeException e) {
            log.warn("Không embed được câu tìm kiếm, dùng tìm bằng chữ: {}", e.getMessage());
            return null;
        }
    }
}
