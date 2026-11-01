package vn.campvivo.promotion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BR-5: mức giảm + thứ tự lỗi; BR-6: công thức giá chiến dịch (phải khớp SQL của Product.salePrice — D-30). */
class VoucherRulesTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-01T10:00:00+07:00");

    private static Voucher voucher(DiscountType type, long value, Long max, long minOrder, int quantity, int used,
                                   OffsetDateTime start, OffsetDateTime expiry, boolean active) {
        Voucher v = new Voucher("TEST", NOW);
        v.update("TEST", type, BigDecimal.valueOf(value), max == null ? null : BigDecimal.valueOf(max),
                BigDecimal.valueOf(minOrder), quantity, start, expiry, active, true, NOW);
        for (int i = 0; i < used; i++) {
            v.consume();
        }
        return v;
    }

    private static Voucher valid() {
        return voucher(DiscountType.PERCENTAGE, 10, 50_000L, 500_000, 10, 0, NOW.minusDays(1), NOW.plusDays(1), true);
    }

    private static BigDecimal vnd(long v) {
        return BigDecimal.valueOf(v);
    }

    @ParameterizedTest(name = "{0} {1} (trần {2}) trên {3} → {4}")
    @CsvSource(nullValues = "-", value = {
            "PERCENTAGE,   10, -,     800000, 80000",    // không trần
            "PERCENTAGE,   10, 50000, 800000, 50000",    // chạm trần
            "PERCENTAGE,   10, 50000, 300000, 30000",    // dưới trần
            "PERCENTAGE,   15, -,     123455, 18518",    // 18518.25 → HALF_UP
            "PERCENTAGE,   15, -,     123457, 18519",    // 18518.55 → HALF_UP
            "PERCENTAGE,   100, -,    250000, 250000",
            "FIXED_AMOUNT, 50000, -,  800000, 50000",
            "FIXED_AMOUNT, 50000, -,  30000,  30000",    // FIXED > subtotal → = subtotal
    })
    void discountAmount(DiscountType type, long value, Long max, long subtotal, long expected) {
        Voucher v = voucher(type, value, max, 0, 5, 0, NOW.minusDays(1), NOW.plusDays(1), true);

        assertThat(VoucherRules.discount(v, vnd(subtotal))).isEqualByComparingTo(vnd(expected));
    }

    @Test
    void validVoucherReturnsDiscount() {
        assertThat(VoucherRules.check(valid(), vnd(600_000), NOW, () -> false)).isEqualByComparingTo(vnd(50_000));
    }

    @Test
    void missingOrInactiveIsInvalid() {
        assertThat(VoucherRules.violation(null, vnd(1), NOW, () -> false)).isEqualTo(ErrorCode.INVALID_VOUCHER);
        Voucher off = voucher(DiscountType.PERCENTAGE, 10, null, 0, 5, 0, NOW.minusDays(1), NOW.plusDays(1), false);
        assertThat(VoucherRules.violation(off, vnd(1), NOW, () -> false)).isEqualTo(ErrorCode.INVALID_VOUCHER);
    }

    @Test
    void validityWindowIncludesStartExcludesExpiry() {
        Voucher startsNow = voucher(DiscountType.PERCENTAGE, 10, null, 0, 5, 0, NOW, NOW.plusDays(1), true);
        Voucher expiresNow = voucher(DiscountType.PERCENTAGE, 10, null, 0, 5, 0, NOW.minusDays(1), NOW, true);
        Voucher notStarted = voucher(DiscountType.PERCENTAGE, 10, null, 0, 5, 0, NOW.plusSeconds(1), NOW.plusDays(1), true);

        assertThat(VoucherRules.violation(startsNow, vnd(1), NOW, () -> false)).isNull();
        assertThat(VoucherRules.violation(expiresNow, vnd(1), NOW, () -> false)).isEqualTo(ErrorCode.VOUCHER_EXPIRED);
        assertThat(VoucherRules.violation(notStarted, vnd(1), NOW, () -> false)).isEqualTo(ErrorCode.VOUCHER_EXPIRED);
    }

    @Test
    void minOrderBoundaryAndDetails() {
        assertThat(VoucherRules.violation(valid(), vnd(500_000), NOW, () -> false)).isNull();

        assertThatThrownBy(() -> VoucherRules.check(valid(), vnd(499_999), NOW, () -> false))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo("VOUCHER_MIN_ORDER_NOT_MET");
                    assertThat(e.getDetails()).isEqualTo(Map.of("minOrderValue", vnd(500_000)));
                    assertThat(e.getMessage()).contains("500.000đ");
                });
    }

    @Test
    void errorOrderFollowsBr5WhenSeveralConditionsFail() {
        // Hết hạn + chưa đạt tối thiểu + hết lượt + đã dùng → báo hết hạn trước
        Voucher all = voucher(DiscountType.PERCENTAGE, 10, null, 500_000, 1, 1, NOW.minusDays(2), NOW.minusDays(1), true);
        assertThat(VoucherRules.violation(all, vnd(1), NOW, () -> true)).isEqualTo(ErrorCode.VOUCHER_EXPIRED);

        // Chưa đạt tối thiểu + hết lượt + đã dùng → tối thiểu
        Voucher minAndExhausted = voucher(DiscountType.PERCENTAGE, 10, null, 500_000, 1, 1, NOW.minusDays(1), NOW.plusDays(1), true);
        assertThat(VoucherRules.violation(minAndExhausted, vnd(1), NOW, () -> true))
                .isEqualTo(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET);

        // Hết lượt + đã dùng → hết lượt
        assertThat(VoucherRules.violation(minAndExhausted, vnd(600_000), NOW, () -> true))
                .isEqualTo(ErrorCode.VOUCHER_EXHAUSTED);

        // Chỉ đã dùng
        assertThat(VoucherRules.violation(valid(), vnd(600_000), NOW, () -> true)).isEqualTo(ErrorCode.VOUCHER_ALREADY_USED);
    }

    @Test
    void alreadyUsedIsNotQueriedWhenEarlierRuleFails() {
        Voucher expired = voucher(DiscountType.PERCENTAGE, 10, null, 0, 5, 0, NOW.minusDays(2), NOW.minusDays(1), true);

        VoucherRules.violation(expired, vnd(1), NOW, () -> {
            throw new AssertionError("không được hỏi DB");
        });
    }

    @ParameterizedTest(name = "{0} {1} trên giá {2} → {3}")
    @CsvSource({
            "PERCENTAGE,   20,     500000, 400000",
            "PERCENTAGE,   15,     99999,  84999",   // 84999.15 → 84999
            "PERCENTAGE,   15,     99990,  84992",   // 84991.5 → HALF_UP 84992
            "PERCENTAGE,   100,    250000, 0",
            "FIXED_AMOUNT, 100000, 500000, 400000",
            "FIXED_AMOUNT, 100000, 80000,  0",       // FIXED > giá → 0
    })
    void campaignPrice(DiscountType type, long value, long price, long expected) {
        assertThat(Discounts.campaignPrice(vnd(price), type, vnd(value))).isEqualByComparingTo(vnd(expected));
    }
}
