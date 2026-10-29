package vn.campvivo.chatbot.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Xử lý tài liệu chạy nền (@Async trên applicationTaskExecutor) và job embed lại sản phẩm/combo (@Scheduled).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
