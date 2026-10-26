package vn.campvivo.payment.vnpay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.payment.PaymentGateway;
import vn.campvivo.payment.PaymentMethod;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * VNPay 2.1.0 (https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html):
 * - URL thanh toán: tham số `vnp_*` sắp theo tên, giá trị URL-encode (US-ASCII như code mẫu của VNPay), ký
 *   HMAC-SHA512 bằng hash secret, `vnp_Amount` = số tiền × 100, `vnp_TxnRef` = mã đơn (duy nhất).
 * - Kiểm chữ ký callback (Return URL / IPN): bỏ `vnp_SecureHash`, `vnp_SecureHashType`, ký lại đúng cách trên và so sánh.
 * Hash secret chỉ ở server (app.vnpay.hash-secret ← VNP_HASH_SECRET), không ghi log chữ ký hay secret.
 */
@Service
public class VnpayService implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(VnpayService.class);
    private static final String VERSION = "2.1.0";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String SUCCESS_CODE = "00";

    private final AppProperties.Vnpay props;

    public VnpayService(AppProperties appProperties) {
        this.props = appProperties.vnpay();
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.VNPAY;
    }

    @Override
    public String createPaymentUrl(PaymentRequest request) {
        if (!props.configured()) {
            log.error("Chưa cấu hình app.vnpay.* (VNP_TMN_CODE, VNP_HASH_SECRET, VNP_URL, VNP_RETURN_URL)");
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", props.tmnCode());
        params.put("vnp_Amount", toVnpAmount(request.amount()));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", request.txnRef());
        params.put("vnp_OrderInfo", request.orderInfo());
        params.put("vnp_OrderType", props.orderType());
        params.put("vnp_Locale", props.locale());
        params.put("vnp_ReturnUrl", props.returnUrl());
        params.put("vnp_IpAddr", request.clientIp() == null || request.clientIp().isBlank() ? "127.0.0.1" : request.clientIp());
        params.put("vnp_CreateDate", request.createdAt().atZoneSameInstant(VN).format(VNP_TIME));
        params.put("vnp_ExpireDate", request.expiresAt().atZoneSameInstant(VN).format(VNP_TIME));

        String query = canonicalQuery(params);
        return props.payUrl() + "?" + query + "&vnp_SecureHash=" + sign(query);
    }

    @Override
    public PaymentCallback parseCallback(Map<String, String> params) {
        Map<String, String> payload = new LinkedHashMap<>();
        new TreeMap<>(params).forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")) {
                payload.put(k, v);
            }
        });
        boolean valid = verifySignature(params);
        BigDecimal amount = parseAmount(params.get("vnp_Amount"));
        boolean success = SUCCESS_CODE.equals(params.get("vnp_ResponseCode"))
                && SUCCESS_CODE.equals(params.getOrDefault("vnp_TransactionStatus", SUCCESS_CODE));
        return new PaymentCallback(valid, params.get("vnp_TxnRef"), amount, success,
                params.get("vnp_TransactionNo"), params.get("vnp_ResponseCode"), payload);
    }

    /** Chữ ký `vnp_SecureHash` khớp các tham số `vnp_*` còn lại (so sánh thời gian hằng). */
    public boolean verifySignature(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isBlank() || props.hashSecret().isBlank()) {
            return false;
        }
        Map<String, String> fields = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")
                    && v != null && !v.isEmpty()) {
                fields.put(k, v);
            }
        });
        byte[] expected = sign(canonicalQuery(fields)).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = received.toLowerCase().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    /** Số tiền gửi VNPay: VND × 100, không phần lẻ. */
    static String toVnpAmount(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal parseAmount(String vnpAmount) {
        try {
            return new BigDecimal(vnpAmount).movePointLeft(2);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** `k1=v1&k2=v2…` theo thứ tự tên, bỏ giá trị rỗng, encode như code mẫu VNPay (dùng cho cả ký lẫn query). */
    private static String canonicalQuery(Map<String, String> sorted) {
        StringBuilder sb = new StringBuilder();
        sorted.forEach((k, v) -> {
            if (v == null || v.isEmpty()) {
                return;
            }
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(k, StandardCharsets.US_ASCII))
                    .append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.US_ASCII));
        });
        return sb.toString();
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(props.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            log.error("Không ký được dữ liệu VNPay", e);
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);
        }
    }
}
