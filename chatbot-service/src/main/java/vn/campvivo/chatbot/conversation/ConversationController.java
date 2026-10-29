package vn.campvivo.chatbot.conversation;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.campvivo.chatbot.common.dto.ApiResponse;
import vn.campvivo.chatbot.common.dto.PageQuery;
import vn.campvivo.chatbot.common.dto.PageResponse;
import vn.campvivo.chatbot.conversation.ConversationModels.ConversationResponse;
import vn.campvivo.chatbot.conversation.ConversationModels.MessageResponse;
import vn.campvivo.chatbot.security.CurrentUserId;

import java.util.List;
import java.util.UUID;

/**
 * Hội thoại với chatbot — api.md 4.12. Quyền CUSTOMER (SecurityConfig); userId lấy từ token, không từ request.
 */
@RestController
@RequestMapping("/api/chatbot/conversations")
public class ConversationController {

    private final ConversationService conversations;
    private final ChatStreamService chat;

    public ConversationController(ConversationService conversations, ChatStreamService chat) {
        this.conversations = conversations;
        this.chat = chat;
    }

    public record SendMessageRequest(String content) {}

    @GetMapping
    public PageResponse<ConversationResponse> list(@CurrentUserId Long userId,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer limit) {
        return conversations.list(userId, PageQuery.of(page, limit));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConversationResponse> create(@CurrentUserId Long userId) {
        return ApiResponse.of(conversations.create(userId));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageResponse>> messages(@CurrentUserId Long userId, @PathVariable UUID id) {
        return ApiResponse.of(conversations.messages(userId, id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUserId Long userId, @PathVariable UUID id) {
        conversations.delete(userId, id);
    }

    /**
     * Trả lời dạng text/event-stream: delta → cards → sources → done (hoặc error). Không khai `produces` để lỗi xảy ra
     * trước khi mở stream (400/404/429) vẫn trả được JSON — client gửi `Accept: text/event-stream, application/json`.
     */
    @PostMapping("/{id}/messages")
    public SseEmitter send(@CurrentUserId Long userId, @PathVariable UUID id,
                           @RequestBody SendMessageRequest request) {
        return chat.send(userId, id, request == null ? null : request.content());
    }
}
