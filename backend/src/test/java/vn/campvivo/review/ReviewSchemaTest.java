package vn.campvivo.review;

import org.junit.jupiter.api.BeforeEach;
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
import vn.campvivo.support.PostgresTestContainerConfig;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ràng buộc DB của V8__reviews.sql (database.md mục 3.17) trên PostgreSQL thật, `ddl-auto=validate`.
 * Context khởi động được nghĩa là entity Review khớp migration.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class ReviewSchemaTest {

    @TestConfiguration
    @EntityScan(basePackageClasses = Review.class)
    @EnableJpaRepositories(basePackageClasses = ReviewRepository.class)
    static class OnlyReviewModule {}

    private static final String INSERT_REVIEW = """
            INSERT INTO reviews (user_id, product_id, combo_id, rating, moderation_status, is_hidden, hidden_reason, hidden_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Autowired ReviewRepository reviews;
    @Autowired JdbcTemplate jdbc;

    private long userId;
    private long productId;
    private long comboId;

    @BeforeEach
    void seed() {
        userId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name) VALUES ('rv@test.vn', 'x', 'Người Đánh Giá') RETURNING id
                """, Long.class);
        long categoryId = jdbc.queryForObject("""
                INSERT INTO categories (name, slug, path) VALUES ('Lều', 'leu-rv', '/leu-rv') RETURNING id
                """, Long.class);
        productId = jdbc.queryForObject("""
                INSERT INTO products (category_id, name, slug, price) VALUES (?, 'Lều 2 người', 'leu-2-nguoi-rv', 1000000) RETURNING id
                """, Long.class, categoryId);
        comboId = jdbc.queryForObject("""
                INSERT INTO product_combos (category_id, name, slug, combo_price) VALUES (?, 'Combo cắm trại', 'combo-rv', 2000000) RETURNING id
                """, Long.class, categoryId);
    }

    private int insert(Long product, Long combo, int rating) {
        return jdbc.update(INSERT_REVIEW, userId, product, combo, rating, "SAFE", false, null, null);
    }

    @Test
    void savesReviewWithDefaults() {
        OffsetDateTime now = OffsetDateTime.now();
        Review r = Review.forProduct(userId, productId, now);
        r.write(5, "Lều tốt", true, List.of("dm", "vcl"));
        reviews.saveAndFlush(r);

        Review found = reviews.findByUserIdAndProductId(userId, productId).orElseThrow();
        assertThat(found.getRating()).isEqualTo(5);
        assertThat(found.isSensitive()).isTrue();
        assertThat(found.getFlaggedTermList()).containsExactly("dm", "vcl");
        assertThat(found.isHidden()).isFalse();
        assertThat(found.getItemType()).isEqualTo("PRODUCT");
        assertThat(found.getUpdatedAt()).isEqualTo(found.getCreatedAt());
    }

    @Test
    void validRowsAreAccepted() {
        assertThat(insert(productId, null, 1)).isEqualTo(1);
        assertThat(insert(null, comboId, 5)).isEqualTo(1);
    }

    @Test
    void ratingOutOfRangeIsRejected() {
        assertThatThrownBy(() -> insert(productId, null, 0)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ratingAboveFiveIsRejected() {
        assertThatThrownBy(() -> insert(productId, null, 6)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bothItemsAreRejected() {
        assertThatThrownBy(() -> insert(productId, comboId, 4)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void noItemIsRejected() {
        assertThatThrownBy(() -> insert(null, null, 4)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondReviewForSameProductIsRejected() {
        insert(productId, null, 4);
        assertThatThrownBy(() -> insert(productId, null, 2)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void secondReviewForSameComboIsRejected() {
        insert(null, comboId, 4);
        assertThatThrownBy(() -> insert(null, comboId, 2)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void hiddenWithoutReasonIsRejected() {
        assertThatThrownBy(() -> jdbc.update(INSERT_REVIEW, userId, productId, null, 3, "SAFE", true, null, OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownModerationStatusIsRejected() {
        assertThatThrownBy(() -> jdbc.update(INSERT_REVIEW, userId, productId, null, 3, "PENDING", false, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingProductRemovesItsReviews() {
        insert(productId, null, 4);
        jdbc.update("DELETE FROM products WHERE id = ?", productId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reviews", Integer.class)).isZero();
    }
}
