package vn.campvivo.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    void roundsHalfUpToWholeDong() {
        assertThat(Money.round(new BigDecimal("0.5"))).isEqualByComparingTo("1");
        assertThat(Money.round(new BigDecimal("1.5"))).isEqualByComparingTo("2");
        assertThat(Money.round(new BigDecimal("2.4"))).isEqualByComparingTo("2");
        assertThat(Money.round(new BigDecimal("-0.5"))).isEqualByComparingTo("-1");
    }

    @Test
    void resultAlwaysHasScaleZero() {
        assertThat(Money.round(new BigDecimal("1000.00")).scale()).isZero();
        assertThat(Money.percentOf(new BigDecimal("333"), new BigDecimal("15")).scale()).isZero();
    }

    @Test
    void percentOfExactAmount() {
        assertThat(Money.percentOf(new BigDecimal("450000"), new BigDecimal("15"))).isEqualByComparingTo("67500");
    }

    @Test
    void percentOfRoundsHalfUp() {
        // 333 * 15% = 49.95 -> 50 ; 10 * 5% = 0.5 -> 1
        assertThat(Money.percentOf(new BigDecimal("333"), new BigDecimal("15"))).isEqualByComparingTo("50");
        assertThat(Money.percentOf(new BigDecimal("10"), new BigDecimal("5"))).isEqualByComparingTo("1");
    }
}
