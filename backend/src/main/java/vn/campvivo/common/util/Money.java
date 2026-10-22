package vn.campvivo.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tiền là số nguyên VND (api.md mục 1), lưu NUMERIC(19,0) / BigDecimal. Mọi phép chia, phần trăm
 * phải đi qua đây để làm tròn thống nhất HALF_UP (.agents/rules/backend.md).
 */
public final class Money {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {}

    /** Làm tròn HALF_UP về số nguyên VND. */
    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    /** amount * percent / 100, làm tròn HALF_UP (vd. percentOf(450000, 15) = 67500). */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return round(amount.multiply(percent).divide(HUNDRED, 10, RoundingMode.HALF_UP));
    }
}
