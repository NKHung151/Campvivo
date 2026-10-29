package vn.campvivo.chatbot.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Thay model OpenAI bằng bản giả (application-test.yml tắt auto-config chat/embedding của OpenAI).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestAiConfig {

    @Bean
    FakeChatModel fakeChatModel() {
        return new FakeChatModel();
    }

    @Bean
    FakeEmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }
}
