package vn.campvivo.chatbot.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;

/**
 * Model chat giả: trả `reply` (stream thành từng từ). `fail = true` → stream báo lỗi như OpenAI sập. Lưu prompt cuối
 * để test kiểm lịch sử hội thoại gửi kèm. Không gọi tool (việc gọi tool nằm trong OpenAiChatModel, test riêng tool).
 */
public class FakeChatModel implements ChatModel {

    public volatile String reply = "Xin chào, mình là trợ lý Campvivo.";
    public volatile boolean fail;
    public volatile Prompt lastPrompt;

    @Override
    public ChatResponse call(Prompt prompt) {
        lastPrompt = prompt;
        if (fail) {
            throw new IllegalStateException("model giả đang lỗi");
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        lastPrompt = prompt;
        if (fail) {
            return Flux.error(new IllegalStateException("model giả đang lỗi"));
        }
        List<String> words = Arrays.stream(reply.split("(?<= )")).toList();
        return Flux.fromIterable(words)
                .map(w -> new ChatResponse(List.of(new Generation(new AssistantMessage(w)))));
    }

    public void reset() {
        reply = "Xin chào, mình là trợ lý Campvivo.";
        fail = false;
        lastPrompt = null;
    }
}
