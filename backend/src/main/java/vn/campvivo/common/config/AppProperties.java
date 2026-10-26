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
        @Valid @DefaultValue Ghn ghn,
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

    /** BR-3: đơn cổng thanh toán quá `paymentTimeout` bị huỷ; job quét mỗi `timeoutJobDelay`. */
    public record Order(
            @NotNull @DefaultValue("15m") Duration paymentTimeout,
            @NotNull @DefaultValue("60s") Duration timeoutJobDelay
    ) {}

    /** BR-9. */
    public record Return(
            @Min(1) @DefaultValue("7") int windowDays
    ) {}

    /** D-27: phí ship tính qua GHN; đơn đạt ngưỡng (sau voucher) được miễn phí. Tiền là số nguyên VND. */
    public record Shipping(
            @Min(0) @DefaultValue("1000000") long freeThreshold
    ) {}

    /** FR-08. */
    public record Inventory(
            @Min(0) @DefaultValue("5") int lowStockThreshold
    ) {}

    /**
     * VNPay 2.1.0 (D-08, D-28). Giá trị thật lấy từ biến môi trường VNP_*; thiếu thì đặt hàng VNPay báo
     * PAYMENT_GATEWAY_ERROR. `returnUrl` = trang frontend /payment/vnpay-return; IPN URL khai báo ở cổng quản trị
     * merchant của VNPay (trỏ tới {backend}/api/vnpay/ipn), `ipnUrl` chỉ để ghi lại/tham khảo.
     */
    public record Vnpay(
            @DefaultValue("") String tmnCode,
            @DefaultValue("") String hashSecret,
            @DefaultValue("") String payUrl,
            @DefaultValue("") String returnUrl,
            @DefaultValue("") String ipnUrl,
            @NotBlank @DefaultValue("vn") String locale,
            @NotBlank @DefaultValue("other") String orderType
    ) {
        public Vnpay {
            tmnCode = unquote(tmnCode);
            hashSecret = unquote(hashSecret);
            payUrl = unquote(payUrl);
            returnUrl = unquote(returnUrl);
            ipnUrl = unquote(ipnUrl);
        }

        public boolean configured() {
            return !tmnCode.isBlank() && !hashSecret.isBlank() && !payUrl.isBlank() && !returnUrl.isBlank();
        }
    }

    /**
     * GHN (D-27). Giá trị thật lấy từ biến môi trường GHN_*; để trống thì API vận chuyển trả SHIPPING_GATEWAY_ERROR.
     * Kích thước kiện (cm) dùng chung cho mọi đơn vì sản phẩm chưa có cân nặng/kích thước riêng.
     */
    public record Ghn(
            @DefaultValue("") String token,
            @DefaultValue("") String shopId,
            @NotBlank @DefaultValue("https://dev-online-gateway.ghn.vn") String baseUrl,
            @DefaultValue("0") int fromDistrictId,
            @DefaultValue("") String fromWardCode,
            @Min(1) @DefaultValue("2") int serviceTypeId,
            @Min(1) @DefaultValue("500") int defaultWeight,
            @Min(1) @DefaultValue("20") int parcelLength,
            @Min(1) @DefaultValue("15") int parcelWidth,
            @Min(1) @DefaultValue("10") int parcelHeight,
            @NotBlank @DefaultValue("CHOXEMHANGKHONGTHU") String requiredNote,
            @NotNull @DefaultValue("12h") Duration masterDataCacheTtl,
            @NotNull @DefaultValue("10s") Duration timeout,
            @DefaultValue("true") boolean autoCreateShipment
    ) {
        public Ghn {
            token = unquote(token);
            shopId = unquote(shopId);
            baseUrl = unquote(baseUrl);
            fromWardCode = unquote(fromWardCode);
        }

        public boolean configured() {
            return !token.isBlank() && !shopId.isBlank() && fromDistrictId > 0 && !fromWardCode.isBlank();
        }
    }

    /**
     * File .env cho phép viết KEY="giá trị", nhưng khi Spring nạp .env dạng properties (spring.config.import) dấu nháy
     * bị giữ lại trong giá trị. Bỏ một cặp nháy bao ngoài và khoảng trắng thừa ở các chuỗi secret/mã đọc từ .env.
     */
    static String unquote(String value) {
        if (value == null) {
            return "";
        }
        String v = value.strip();
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
            return v.substring(1, v.length() - 1).strip();
        }
        return v;
    }

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
