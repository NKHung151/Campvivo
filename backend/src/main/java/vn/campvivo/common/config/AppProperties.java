package vn.campvivo.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Toàn bộ tham số nghiệp vụ `app.*` (docs/02-ky-thuat/tech-stack.md, mục Cấu hình).
 * Module nào cần giá trị nào thì inject AppProperties, KHÔNG hardcode (AGENTS.md mục 3).
 * Giá trị mặc định đặt ở @DefaultValue; secret không có mặc định nên thiếu là app không khởi động.
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @Valid @DefaultValue Jwt jwt,
        @Valid @DefaultValue Otp otp,
        @Valid @DefaultValue Order order,
        @Valid @DefaultValue @Name("return") Return returns,
        @Valid @DefaultValue Shipping shipping,
        @Valid @DefaultValue Inventory inventory,
        @Valid @DefaultValue Vnpay vnpay,
        @Valid @DefaultValue Cloudinary cloudinary,
        @Valid @DefaultValue Cors cors,
        @NotBlank @DefaultValue("http://localhost:3000") String frontendUrl
) {

    /** BR/authentication.md mục 3. Secret >= 32 byte cho HS256. */
    public record Jwt(
            @NotBlank @Size(min = 32, message = "app.jwt.secret phải dài ít nhất 32 ký tự") String secret,
            @NotNull @DefaultValue("24h") Duration ttl
    ) {}

    /** BR-11. */
    public record Otp(
            @NotNull @DefaultValue("10m") Duration ttl,
            @Min(1) @DefaultValue("5") int maxAttempts,
            @NotNull @DefaultValue("60s") Duration resendInterval
    ) {}

    /** BR-3. */
    public record Order(
            @NotNull @DefaultValue("15m") Duration paymentTimeout
    ) {}

    /** BR-9. */
    public record Return(
            @Min(1) @DefaultValue("7") int windowDays
    ) {}

    /** D-17. Tiền là số nguyên VND. */
    public record Shipping(
            @Min(0) @DefaultValue("30000") long fee,
            @Min(0) @DefaultValue("1000000") long freeThreshold
    ) {}

    /** FR-08. */
    public record Inventory(
            @Min(0) @DefaultValue("5") int lowStockThreshold
    ) {}

    /** Giá trị thật lấy từ biến môi trường VNPAY_*; để trống khi chưa dùng cổng thanh toán. */
    public record Vnpay(
            @DefaultValue("") String tmnCode,
            @DefaultValue("") String hashSecret,
            @DefaultValue("") String payUrl,
            @DefaultValue("") String returnUrl,
            @DefaultValue("") String ipnUrl
    ) {}

    public record Cloudinary(
            @DefaultValue("") String cloudName,
            @DefaultValue("") String apiKey,
            @DefaultValue("") String apiSecret,
            @NotBlank @DefaultValue("campvivo") String folder
    ) {
        public boolean configured() {
            return !cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
        }
    }

    public record Cors(
            @NotNull @Size(min = 1) @DefaultValue("http://localhost:3000") List<String> allowedOrigins
    ) {}
}
