package vn.campvivo.chatbot.tools;

import org.springframework.ai.chat.model.ToolContext;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Trạng thái tool của MỘT câu hỏi, truyền qua {@code toolContext} (tool chạy trên thread của reactor nên không dùng
 * ThreadLocal/request scope): đếm số lượt gọi (giới hạn `app.chatbot.max-tool-calls`), gom thẻ và nguồn tài liệu.
 * <p>Thẻ gửi về client = thẻ "đề xuất" (tư vấn chuyến đi, xem chi tiết) + thẻ tìm kiếm được nhắc tới trong câu
 * trả lời; không nhắc tới thẻ nào thì lấy vài thẻ tìm kiếm đầu.
 */
public class ToolSession {

    public static final String CONTEXT_KEY = "campvivo.toolSession";
    private static final int FALLBACK_CARDS = 4;
    private static final int MAX_CARDS = 12;
    private static final int NAME_PREFIX = 24;

    private final int maxCalls;
    private final AtomicInteger calls = new AtomicInteger();
    private final Map<String, ChatCard> recommended = new LinkedHashMap<>();
    private final Map<String, ChatCard> found = new LinkedHashMap<>();
    private final Map<String, ChatSource> sources = new LinkedHashMap<>();

    public ToolSession(int maxCalls) {
        this.maxCalls = maxCalls;
    }

    public static ToolSession from(ToolContext context) {
        Object s = context == null ? null : context.getContext().get(CONTEXT_KEY);
        if (s instanceof ToolSession session) {
            return session;
        }
        throw new IllegalStateException("Thiếu ToolSession trong toolContext");
    }

    /** @return false nếu đã vượt số lượt gọi tool cho câu hỏi này. */
    public boolean tryCall() {
        return calls.incrementAndGet() <= maxCalls;
    }

    public int callCount() {
        return calls.get();
    }

    public synchronized void recommend(ChatCard card) {
        recommended.put(card.key(), card);
        found.remove(card.key());
    }

    public synchronized void found(ChatCard card) {
        if (!recommended.containsKey(card.key())) {
            found.putIfAbsent(card.key(), card);
        }
    }

    public synchronized void source(ChatSource source) {
        sources.putIfAbsent(source.documentId() + ":" + source.page(), source);
    }

    public synchronized List<ChatSource> sources() {
        return List.copyOf(sources.values());
    }

    /** Thẻ cuối cùng gửi kèm câu trả lời `answer`. */
    public synchronized List<ChatCard> cards(String answer) {
        List<ChatCard> result = new ArrayList<>(recommended.values());
        String text = normalize(answer);
        List<ChatCard> mentioned = found.values().stream()
                .filter(c -> text.contains(normalize(prefix(c.name()))))
                .toList();
        if (!mentioned.isEmpty()) {
            result.addAll(mentioned);
        } else if (result.isEmpty()) {
            found.values().stream().limit(FALLBACK_CARDS).forEach(result::add);
        }
        return result.stream().limit(MAX_CARDS).toList();
    }

    private static String prefix(String name) {
        return name.length() <= NAME_PREFIX ? name : name.substring(0, NAME_PREFIX);
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replaceAll("\\s+", " ");
    }
}
