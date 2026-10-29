package vn.campvivo.chatbot.conversation;

import org.springframework.stereotype.Service;
import vn.campvivo.chatbot.common.dto.PageQuery;
import vn.campvivo.chatbot.common.dto.PageResponse;
import vn.campvivo.chatbot.common.exception.BusinessException;
import vn.campvivo.chatbot.conversation.ConversationModels.ConversationResponse;
import vn.campvivo.chatbot.conversation.ConversationModels.MessageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Hội thoại của khách (api.md 4.12). Hội thoại không phải của mình → 404 NOT_FOUND (không tiết lộ).
 */
@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;

    public ConversationService(ConversationRepository conversations, MessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    public PageResponse<ConversationResponse> list(long userId, PageQuery page) {
        return PageResponse.of(conversations.page(userId, page), page.page(), page.limit(),
                conversations.count(userId));
    }

    public ConversationResponse create(long userId) {
        return conversations.create(UUID.randomUUID(), userId);
    }

    public List<MessageResponse> messages(long userId, UUID conversationId) {
        requireOwned(userId, conversationId);
        return messages.list(conversationId);
    }

    public void delete(long userId, UUID conversationId) {
        if (conversations.deleteOwned(conversationId, userId) == 0) {
            throw BusinessException.notFound();
        }
    }

    public ConversationResponse requireOwned(long userId, UUID conversationId) {
        return conversations.findOwned(conversationId, userId).orElseThrow(BusinessException::notFound);
    }
}
