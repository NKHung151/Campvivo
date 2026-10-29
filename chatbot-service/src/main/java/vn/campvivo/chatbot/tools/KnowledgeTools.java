package vn.campvivo.chatbot.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import vn.campvivo.chatbot.knowledge.KnowledgeModels.RetrievalHit;
import vn.campvivo.chatbot.knowledge.KnowledgeSearch;
import vn.campvivo.chatbot.tools.ToolViews.Notice;

import java.util.List;

/**
 * Tool tra tài liệu admin đã nạp (chính sách, hướng dẫn, kiến thức) — RAG dạng "model tự quyết khi nào tra".
 */
@Component
public class KnowledgeTools {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeTools.class);

    private final KnowledgeSearch search;

    public KnowledgeTools(KnowledgeSearch search) {
        this.search = search;
    }

    public record Passage(long documentId, String documentTitle, Integer page, String content) {}

    public record KnowledgeResult(List<Passage> passages, String notice) {}

    @Tool(name = "searchKnowledge", description = """
            Tra cứu tài liệu của cửa hàng Campvivo (chính sách đổi trả, bảo hành, giao hàng, thanh toán, hướng dẫn \
            chọn và dùng đồ dã ngoại, kiến thức cắm trại...). BẮT BUỘC dùng trước khi trả lời câu hỏi về chính sách \
            hoặc kiến thức. Trả về các đoạn trích kèm tên tài liệu và trang để trích nguồn.""")
    public Object searchKnowledge(@ToolParam(description = "Câu hỏi hoặc từ khoá cần tra, tiếng Việt") String query,
                                  ToolContext toolContext) {
        ToolSession session = ToolSession.from(toolContext);
        if (!session.tryCall()) {
            return ToolViews.tooManyCalls();
        }
        List<RetrievalHit> hits;
        try {
            hits = search.search(query);
        } catch (RuntimeException e) {
            log.warn("Tra tài liệu lỗi: {}", e.getMessage());
            return new Notice("Không tra được tài liệu lúc này. Hãy nói với khách là chưa tra được thông tin.");
        }
        hits.forEach(h -> session.source(new ChatSource(h.documentId(), h.title(), h.page())));
        return new KnowledgeResult(
                hits.stream().map(h -> new Passage(h.documentId(), h.title(), h.page(), h.content())).toList(),
                hits.isEmpty() ? "Không có tài liệu nào nói về nội dung này. Đừng tự bịa câu trả lời." : null);
    }
}
