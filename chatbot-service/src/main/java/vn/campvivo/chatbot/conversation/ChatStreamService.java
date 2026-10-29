package vn.campvivo.chatbot.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import vn.campvivo.chatbot.ai.SystemPrompt;
import vn.campvivo.chatbot.common.config.ChatbotProperties;
import vn.campvivo.chatbot.common.exception.BusinessException;
import vn.campvivo.chatbot.common.exception.ErrorCode;
import vn.campvivo.chatbot.common.util.TextUtil;
import vn.campvivo.chatbot.conversation.ConversationModels.ChatRole;
import vn.campvivo.chatbot.conversation.ConversationModels.MessageResponse;
import vn.campvivo.chatbot.conversation.ConversationModels.NewMessage;
import vn.campvivo.chatbot.tools.AdvisorTools;
import vn.campvivo.chatbot.tools.CatalogTools;
import vn.campvivo.chatbot.tools.KnowledgeTools;
import vn.campvivo.chatbot.tools.ToolSession;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gửi tin nhắn và trả lời dạng SSE (api.md 4.12): lưu tin USER → gọi model kèm lịch sử + tool → đẩy `delta` khi có
 * chữ → xong thì đẩy `cards`, `sources`, lưu tin ASSISTANT, đẩy `done`. Lỗi model → `error` AI_UNAVAILABLE, không
 * lưu câu trả lời dở. Kiểm tra quyền/giới hạn/độ dài làm TRƯỚC khi mở stream để lỗi trả về JSON thường.
 */
@Service
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(2);
    private static final int TITLE_MAX = 60;

    private final ChatClient chatClient;
    private final SystemPrompt systemPrompt;
    private final ConversationService conversations;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messages;
    private final ChatRateLimiter rateLimiter;
    private final CatalogTools catalogTools;
    private final KnowledgeTools knowledgeTools;
    private final AdvisorTools advisorTools;
    private final ChatbotProperties props;

    public ChatStreamService(ChatClient chatClient, SystemPrompt systemPrompt, ConversationService conversations,
                             ConversationRepository conversationRepository, MessageRepository messages,
                             ChatRateLimiter rateLimiter, CatalogTools catalogTools, KnowledgeTools knowledgeTools,
                             AdvisorTools advisorTools, ChatbotProperties props) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.conversations = conversations;
        this.conversationRepository = conversationRepository;
        this.messages = messages;
        this.rateLimiter = rateLimiter;
        this.catalogTools = catalogTools;
        this.knowledgeTools = knowledgeTools;
        this.advisorTools = advisorTools;
        this.props = props;
    }

    public SseEmitter send(long userId, UUID conversationId, String rawContent) {
        String content = rawContent == null ? "" : rawContent.strip();
        int max = props.chatbot().messageMaxLength();
        if (content.isEmpty() || content.length() > max) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                    Map.of("fieldErrors", Map.of("content", content.isEmpty()
                            ? "Vui lòng nhập nội dung." : "Tin nhắn tối đa " + max + " ký tự.")));
        }
        conversations.requireOwned(userId, conversationId);
        rateLimiter.check(userId);

        List<Message> history = history(conversationId);
        messages.insert(new NewMessage(conversationId, ChatRole.USER, content, List.of(), List.of(), null, null));
        conversationRepository.touch(conversationId, TextUtil.truncate(content.replaceAll("\\s+", " "), TITLE_MAX));

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        ToolSession session = new ToolSession(props.chatbot().maxToolCalls());
        StringBuilder answer = new StringBuilder();
        AtomicReference<Usage> usage = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean(false);

        Disposable subscription = chatClient.prompt()
                .system(systemPrompt.render())
                .messages(history)
                .user(content)
                .tools(catalogTools, knowledgeTools, advisorTools)
                .toolContext(Map.of(ToolSession.CONTEXT_KEY, session))
                .stream()
                .chatResponse()
                .subscribe(
                        response -> onChunk(response, emitter, answer, usage),
                        error -> {
                            if (finished.compareAndSet(false, true)) {
                                log.warn("Gọi model lỗi (hội thoại {}): {}", conversationId, error.toString());
                                sendError(emitter);
                            }
                        },
                        () -> {
                            if (finished.compareAndSet(false, true)) {
                                complete(conversationId, emitter, answer.toString(), session, usage.get());
                            }
                        });
        emitter.onTimeout(() -> {
            subscription.dispose();
            if (finished.compareAndSet(false, true)) {
                sendError(emitter);
            }
        });
        emitter.onError(e -> subscription.dispose());
        return emitter;
    }

    private List<Message> history(UUID conversationId) {
        int size = props.chatbot().historySize();
        if (size <= 0) {
            return List.of();
        }
        return messages.recent(conversationId, size).stream()
                .map(m -> m.role() == ChatRole.USER
                        ? (Message) new UserMessage(m.content())
                        : new AssistantMessage(m.content()))
                .toList();
    }

    private void onChunk(ChatResponse response, SseEmitter emitter, StringBuilder answer,
                         AtomicReference<Usage> usage) {
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null
                && response.getMetadata().getUsage().getPromptTokens() != null
                && response.getMetadata().getUsage().getPromptTokens() > 0) {
            usage.set(response.getMetadata().getUsage());
        }
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return;
        }
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        answer.append(text);
        send(emitter, "delta", Map.of("text", text));
    }

    private void complete(UUID conversationId, SseEmitter emitter, String answer, ToolSession session, Usage usage) {
        if (answer.isBlank()) {
            sendError(emitter);
            return;
        }
        var cards = session.cards(answer);
        var sources = session.sources();
        MessageResponse saved = messages.insert(new NewMessage(conversationId, ChatRole.ASSISTANT, answer, cards,
                sources, usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens()));
        conversationRepository.touch(conversationId, ConversationRepository.DEFAULT_TITLE);
        send(emitter, "cards", cards);
        send(emitter, "sources", sources);
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("messageId", saved.id());
        done.put("promptTokens", usage == null ? null : usage.getPromptTokens());
        done.put("completionTokens", usage == null ? null : usage.getCompletionTokens());
        send(emitter, "done", done);
        emitter.complete();
    }

    private void sendError(SseEmitter emitter) {
        send(emitter, "error", Map.of("errorCode", ErrorCode.AI_UNAVAILABLE.name(),
                "message", ErrorCode.AI_UNAVAILABLE.getMessage()));
        emitter.complete();
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // Client đã đóng kết nối — bỏ qua, câu trả lời (nếu xong) vẫn được lưu
            log.debug("Không gửi được sự kiện {}: {}", event, e.getMessage());
        }
    }
}
