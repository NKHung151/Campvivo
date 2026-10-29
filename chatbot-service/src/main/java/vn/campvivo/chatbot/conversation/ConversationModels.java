package vn.campvivo.chatbot.conversation;

import vn.campvivo.chatbot.tools.ChatCard;
import vn.campvivo.chatbot.tools.ChatSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hội thoại/tin nhắn — bảng chatbot_conversations, chatbot_messages (database.md 3.24); dạng JSON khớp api.md 4.12
 * (`ChatConversation`, `ChatMessage`).
 */
public final class ConversationModels {

    private ConversationModels() {
    }

    public enum ChatRole { USER, ASSISTANT }

    public record ConversationResponse(UUID id, String title, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record MessageResponse(long id, ChatRole role, String content, List<ChatCard> cards,
                                  List<ChatSource> sources, OffsetDateTime createdAt) {}

    public record NewMessage(UUID conversationId, ChatRole role, String content, List<ChatCard> cards,
                             List<ChatSource> sources, Integer promptTokens, Integer completionTokens) {}
}
