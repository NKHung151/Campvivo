package vn.campvivo.payment.vnpay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.payment.PaymentGateway.PaymentCallback;
import vn.campvivo.payment.PaymentGateway.PaymentRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** VNPay 2.1.0: URL thanh toán đúng tham số + chữ ký HMAC-SHA512; kiểm chữ ký callback. */
class VnpayServiceTest {

    private static final String SECRET = "TESTSECRETKEY1234567890ABCDEFGHI";

    private static VnpayService service(boolean configured) {
        Map<String, String> values = new HashMap<>();
        values.put("app.jwt.secret", "test_only_secret_at_least_32_characters_long");
        if (configured) {
            values.put("app.vnpay.tmn-code", "TESTTMN1");
            values.put("app.vnpay.hash-secret", SECRET);
            values.put("app.vnpay.pay-url", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
            values.put("app.vnpay.return-url", "http://localhost:3000/payment/vnpay-return");
        }
        return new VnpayService(new Binder(new MapConfigurationPropertySource(values)).bind("app", AppProperties.class).get());
    }

    /** Ký độc lập theo mô tả tài liệu VNPay để đối chiếu. */
    private static String hmac(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> decodedQuery(String url) {
        Map<String, String> out = new LinkedHashMap<>();
        UriComponentsBuilder.fromUriString(url).build().getQueryParams().forEach((k, v) ->
                out.put(k, URLDecoder.decode(v.get(0), StandardCharsets.UTF_8)));
        return out;
    }

    /** Tham số callback có chữ ký hợp lệ (giống VNPay gửi về). */
    private static Map<String, String> signedCallback(Map<String, String> fields) throws Exception {
        StringBuilder data = new StringBuilder();
        new TreeMap<>(fields).forEach((k, v) -> {
            if (!data.isEmpty()) {
                data.append('&');
            }
            data.append(k).append('=').append(java.net.URLEncoder.encode(v, StandardCharsets.US_ASCII));
        });
        Map<String, String> params = new HashMap<>(fields);
        params.put("vnp_SecureHashType", "HmacSHA512");
        params.put("vnp_SecureHash", hmac(data.toString()).toUpperCase());
        return params;
    }

    @Test
    void paymentUrlHasVersion210AmountTimes100AndValidSignature() throws Exception {
        OffsetDateTime created = OffsetDateTime.parse("2026-10-26T13:00:00+07:00");
        String url = service(true).createPaymentUrl(new PaymentRequest("ORD-20261026-ABC123", new BigDecimal("840000"),
                "Thanh toan don hang ORD-20261026-ABC123", "10.0.0.1", created, created.plusMinutes(15)));

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        Map<String, String> q = decodedQuery(url);
        assertThat(q).containsEntry("vnp_Version", "2.1.0")
                .containsEntry("vnp_Command", "pay")
                .containsEntry("vnp_TmnCode", "TESTTMN1")
                .containsEntry("vnp_Amount", "84000000")
                .containsEntry("vnp_CurrCode", "VND")
                .containsEntry("vnp_TxnRef", "ORD-20261026-ABC123")
                .containsEntry("vnp_IpAddr", "10.0.0.1")
                .containsEntry("vnp_CreateDate", "20261026130000")
                .containsEntry("vnp_ExpireDate", "20261026131500")
                .containsEntry("vnp_ReturnUrl", "http://localhost:3000/payment/vnpay-return");

        // Chữ ký = HMAC-SHA512 của phần query (đã encode, sắp theo tên) trước vnp_SecureHash
        String signedPart = url.substring(url.indexOf('?') + 1, url.indexOf("&vnp_SecureHash="));
        assertThat(q.get("vnp_SecureHash")).isEqualTo(hmac(signedPart));
        assertThat(signedPart.indexOf("vnp_Amount")).isLessThan(signedPart.indexOf("vnp_Command"));
    }

    @Test
    void createDateUsesVietnamTimeEvenWhenGivenUtc() {
        OffsetDateTime created = OffsetDateTime.parse("2026-10-26T06:00:00Z");
        String url = service(true).createPaymentUrl(new PaymentRequest("ORD-1", BigDecimal.TEN, "x", "1.1.1.1",
                created, created.plusMinutes(15)));
        assertThat(decodedQuery(url)).containsEntry("vnp_CreateDate", "20261026130000");
    }

    @Test
    void missingConfigurationIsGatewayError() {
        OffsetDateTime now = OffsetDateTime.now();
        assertThatThrownBy(() -> service(false).createPaymentUrl(new PaymentRequest("ORD-1", BigDecimal.TEN, "x",
                "1.1.1.1", now, now.plusMinutes(15))))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("PAYMENT_GATEWAY_ERROR"));
    }

    @Test
    void parsesValidSuccessCallback() throws Exception {
        Map<String, String> params = signedCallback(Map.of(
                "vnp_TxnRef", "ORD-20261026-ABC123",
                "vnp_Amount", "84000000",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "14123456",
                "vnp_OrderInfo", "Thanh toan don hang ORD-20261026-ABC123",
                "vnp_TmnCode", "TESTTMN1"));

        PaymentCallback cb = service(true).parseCallback(params);

        assertThat(cb.signatureValid()).isTrue();
        assertThat(cb.success()).isTrue();
        assertThat(cb.amount()).isEqualByComparingTo("840000");
        assertThat(cb.txnRef()).isEqualTo("ORD-20261026-ABC123");
        assertThat(cb.transactionNo()).isEqualTo("14123456");
        assertThat(cb.payload()).doesNotContainKeys("vnp_SecureHash", "vnp_SecureHashType");
    }

    @Test
    void tamperedAmountFailsSignature() throws Exception {
        Map<String, String> params = signedCallback(Map.of(
                "vnp_TxnRef", "ORD-1", "vnp_Amount", "1000000", "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00"));
        params.put("vnp_Amount", "100");

        assertThat(service(true).parseCallback(params).signatureValid()).isFalse();
    }

    @Test
    void failedPaymentCodeIsNotSuccess() throws Exception {
        Map<String, String> params = signedCallback(Map.of(
                "vnp_TxnRef", "ORD-1", "vnp_Amount", "1000000", "vnp_ResponseCode", "24", "vnp_TransactionStatus", "02"));

        PaymentCallback cb = service(true).parseCallback(params);

        assertThat(cb.signatureValid()).isTrue();
        assertThat(cb.success()).isFalse();
        assertThat(cb.responseCode()).isEqualTo("24");
    }

    @Test
    void missingHashIsInvalid() {
        assertThat(service(true).verifySignature(Map.of("vnp_TxnRef", "ORD-1"))).isFalse();
    }
}
