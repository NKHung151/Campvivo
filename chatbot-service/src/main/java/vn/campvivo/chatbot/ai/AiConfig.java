package vn.campvivo.chatbot.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient dùng chung. Model, temperature, max-tokens, stream-usage cấu hình ở `spring.ai.openai.chat.options.*`
 * (application.yml) — đổi model không cần sửa code. Test thay ChatModel/EmbeddingModel bằng bản giả.
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
