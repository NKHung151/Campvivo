package vn.campvivo.chatbot.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Cấu hình `app.*` của chatbot-service — docs/02-ky-thuat/tech-stack.md (mục cấu hình chatbot-service).
 * Secret (JWT) chỉ lấy từ biến môi trường / application-dev.yml.
 */
@Validated
@ConfigurationProperties("app")
public record ChatbotProperties(
        @Valid @DefaultValue Jwt jwt,
        @Valid @DefaultValue Cors cors,
        @Valid @DefaultValue Chatbot chatbot
) {

    /** Chung secret với backend; ≥ 32 ký tự (HS256). */
    public record Jwt(@Size(min = 32, message = "app.jwt.secret phải có ít nhất 32 ký tự") String secret) {}

    public record Cors(@NotEmpty @DefaultValue("http://localhost:3000") List<String> allowedOrigins) {}

    public record Chatbot(
            @Min(1) @DefaultValue("1000") int messageMaxLength,
            @Min(0) @DefaultValue("20") int historySize,
            @Min(1) @DefaultValue("5") int maxToolCalls,
            @Valid @DefaultValue RateLimit rateLimit,
            @Valid @DefaultValue Knowledge knowledge,
            @Valid @DefaultValue Embedding embedding
    ) {}

    public record RateLimit(@Min(1) @DefaultValue("20") int perMinute, @Min(1) @DefaultValue("200") int perDay) {}

    public record Knowledge(
            @DefaultValue("10MB") DataSize maxFileSize,
            @Min(100) @DefaultValue("600") int chunkSize,
            @Min(1) @DefaultValue("5") int topK,
            @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.3") double similarityThreshold
    ) {}

    /** `enabled = false`: không chạy job embed sản phẩm/combo (test chạy tay). */
    public record Embedding(@DefaultValue("true") boolean enabled, @DefaultValue("10m") Duration refreshInterval) {}
}
