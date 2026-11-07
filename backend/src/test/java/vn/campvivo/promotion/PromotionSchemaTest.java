package vn.campvivo.promotion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import vn.campvivo.catalog.Product;
import vn.campvivo.support.PostgresTestContainerConfig;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ràng buộc DB của V6__promotion.sql (database.md mục 3.14–3.16) trên PostgreSQL thật, `ddl-auto=validate`.
 * Context khởi động được nghĩa là entity Voucher/DiscountCampaign khớp migration.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class PromotionSchemaTest {

    @TestConfiguration
    @EntityScan(basePackageClasses = {Voucher.class, Product.class})
    @EnableJpaRepositories(basePackageClasses = VoucherRepository.class)
    static class OnlyPromotionModule {}

    private static final String INSERT_VOUCHER = """
            INSERT INTO vouchers (code, discount_type, discount_value, max_discount, quantity, used_count, start_date, expiry_date)
            VALUES (?, ?, ?, ?, ?, ?, now(), now() + ?::interval)
            """;

    @Autowired VoucherRepository vouchers;
    @Autowired JdbcTemplate jdbc;

    @Test
    void savesVoucherWithDefaultsAndUpperCaseCode() {
        OffsetDateTime now = OffsetDateTime.now();
        Voucher v = new Voucher("  camp10 ", now);
        v.update("camp10", DiscountType.PERCENTAGE, BigDecimal.TEN, BigDecimal.valueOf(50000), BigDecimal.ZERO, 5,
                now, now.plusDays(3), true, true, now);
        vouchers.saveAndFlush(v);

        Voucher found = vouchers.findByCode("CAMP10").orElseThrow();
        assertThat(found.getUsedCount()).isZero();
        assertThat(found.isPublicVisible()).isTrue();
        assertThat(found.statusAt(now.plusHours(1))).isEqualTo(VoucherStatus.ACTIVE);
    }

    @Test
    void validRowIsAccepted() {
        assertThat(jdbc.update(INSERT_VOUCHER, "OK1", "PERCENTAGE", 10, 50000, 5, 5, "1 day")).isEqualTo(1);
    }

    @Test
    void usedCountGreaterThanQuantityIsRejected() {
        assertThatThrownBy(() -> jdbc.update(INSERT_VOUCHER, "OVER", "PERCENTAGE", 10, null, 5, 6, "1 day"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void expiryNotAfterStartIsRejected() {
        assertThatThrownBy(() -> jdbc.update(INSERT_VOUCHER, "PAST", "PERCENTAGE", 10, null, 5, 0, "0 seconds"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void percentageOutOfRangeIsRejected() {
        assertThatThrownBy(() -> jdbc.update(INSERT_VOUCHER, "P101", "PERCENTAGE", 101, null, 5, 0, "1 day"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void fixedAmountWithMaxDiscountIsRejected() {
        assertThatThrownBy(() -> jdbc.update(INSERT_VOUCHER, "FIX", "FIXED_AMOUNT", 50000, 10000, 5, 0, "1 day"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lowerCaseCodeIsRejected() {
        assertThatThrownBy(() -> jdbc.update(INSERT_VOUCHER, "lower", "PERCENTAGE", 10, null, 5, 0, "1 day"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void campaignEndNotAfterStartIsRejected() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO discount_campaigns (name, slug, discount_type, discount_value, start_date, end_date)
                VALUES ('A', 'a', 'PERCENTAGE', 10, now(), now())
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orderVoucherForeignKeyIsEnforced() {
        Integer fk = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'fk_orders_voucher'", Integer.class);
        assertThat(fk).isEqualTo(1);
    }
}
