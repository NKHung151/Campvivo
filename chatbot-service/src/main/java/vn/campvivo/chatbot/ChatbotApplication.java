package vn.campvivo.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

/**
 * Chatbot AI Campvivo (FR-28) — service riêng dùng chung DB với backend (docs/99-ghi-chu/decisions.md D-28).
 * Không dùng user/mật khẩu mặc định của Spring Security: chỉ xác thực bằng JWT của backend.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class ChatbotApplication {
    public static void main(String[] args) {
        // Giống backend: JVM trên Windows báo "Asia/Saigon", PostgreSQL (Debian 13) từ chối tên này
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(ChatbotApplication.class, args);
    }
}
