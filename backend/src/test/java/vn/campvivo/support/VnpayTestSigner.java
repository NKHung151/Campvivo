package vn.campvivo.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Ký tham số callback như VNPay (sắp theo tên, encode US-ASCII, HMAC-SHA512) — giả lập Return URL / IPN trong test. */
public final class VnpayTestSigner {

    private VnpayTestSigner() {
    }

    public static Map<String, String> sign(Map<String, String> fields, String secret) {
        StringBuilder data = new StringBuilder();
        new TreeMap<>(fields).forEach((k, v) -> {
            if (!data.isEmpty()) {
                data.append('&');
            }
            data.append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.US_ASCII));
        });
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            Map<String, String> out = new HashMap<>(fields);
            out.put("vnp_SecureHashType", "HmacSHA512");
            out.put("vnp_SecureHash", HexFormat.of().formatHex(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8))));
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Callback VNPay cho đơn `orderCode` số tiền `amountVnd`. */
    public static Map<String, String> callback(String orderCode, long amountVnd, String responseCode, String transactionNo,
                                               String secret) {
        Map<String, String> f = new HashMap<>();
        f.put("vnp_TxnRef", orderCode);
        f.put("vnp_Amount", String.valueOf(amountVnd * 100));
        f.put("vnp_ResponseCode", responseCode);
        f.put("vnp_TransactionStatus", "00".equals(responseCode) ? "00" : "02");
        f.put("vnp_TransactionNo", transactionNo);
        f.put("vnp_TmnCode", "TESTTMN1");
        f.put("vnp_OrderInfo", "Thanh toan don hang " + orderCode);
        return sign(f, secret);
    }
}
