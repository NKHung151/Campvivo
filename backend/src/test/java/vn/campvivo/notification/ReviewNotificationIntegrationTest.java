package vn.campvivo.notification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import vn.campvivo.review.ReviewService;
import vn.campvivo.review.admin.AdminReviewService;
import vn.campvivo.review.dto.ReviewRequests;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `REVIEW_FLAGGED` (D-43): một dòng gộp cho mỗi nhân viên, nội dung là số đánh giá đang chờ (đúng bộ lọc `status=SENSITIVE`);
 * kiểm duyệt bớt hàng chờ chỉ sửa số, về 0 thì đã xử lý. Đi qua ReviewService / AdminReviewService thật (từ điển thật: "vcl").
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long",
        "app.shipping.free-threshold=100000000",
        "app.order.timeout-job-delay=1h"
})
class ReviewNotificationIntegrationTest {

    private static final String RUDE = "Giao chậm vcl";

    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReviewService reviewService;
    @Autowired private AdminReviewService adminReviews;
    @Autowired private NotificationDispatcher dispatcher;
    @MockBean private GhnService ghn;

    private long productId;
    private long comboId;
    private long staffA;
    private long staffB;

    private record Row(String title, int eventCount, boolean read, boolean resolved, Long resolvedBy) {}

    @BeforeEach
    void setUp() {
        wipe();
        long category = jdbc.queryForObject(
                "INSERT INTO categories (name, slug, path) VALUES ('Lều', 'leu-review-nt', '/') RETURNING id", Long.class);
        productId = jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price, stock_quantity) "
                + "VALUES (?, 'Lều 2 người', 'leu-2-review-nt', 500000, 50) RETURNING id", Long.class, category);
        comboId = jdbc.queryForObject("INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity) "
                + "VALUES (?, 'Combo cắm trại', 'combo-review-nt', 900000, 10) RETURNING id", Long.class, category);
        staffA = user(Role.EMPLOYEE, "Trần A");
        staffB = user(Role.ADMIN, "Lê B");
    }

    @AfterEach
    void tearDown() {
        dispatcher.drain(Duration.ofSeconds(10));
        wipe();
        jdbc.update("DELETE FROM users WHERE email LIKE 'nt-review-%'");
    }

    private void wipe() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM reviews");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM carts");
        jdbc.update("DELETE FROM combo_items");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
    }

    private long user(Role role, String name) {
        User u = new User("nt-review-" + UUID.randomUUID() + "@test.vn", "hash", name);
        u.setRole(role);
        u.setEmailVerified(true);
        return users.saveAndFlush(u).getId();
    }

    /** Khách mới đã mua (đơn COMPLETED) rồi viết đánh giá; trả id đánh giá. */
    private long review(Long product, Long combo, String content) {
        long customer = user(Role.CUSTOMER, "Khách");
        long orderId = jdbc.queryForObject("""
                INSERT INTO orders (order_code, user_id, subtotal, final_amount, recipient_name, recipient_phone,
                                    shipping_address, shipping_district_id, shipping_ward_code, payment_method, status)
                VALUES (?, ?, 500000, 500000, 'A', '0901234567', 'HN', 1442, '20109', 'COD', 'COMPLETED') RETURNING id
                """, Long.class, "ORD-" + UUID.randomUUID().toString().substring(0, 12), customer);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, combo_id, item_name, original_price, unit_price, quantity, line_total)
                VALUES (?, ?, ?, 'Hàng', 500000, 500000, 1, 500000)
                """, orderId, product, combo);
        return reviewService.create(customer, new ReviewRequests.Create(product, combo, 2, content)).id();
    }

    private long ownerOf(long reviewId) {
        return jdbc.queryForObject("SELECT user_id FROM reviews WHERE id = ?", Long.class, reviewId);
    }

    private List<Row> flagged(long userId) {
        assertThat(dispatcher.drain(Duration.ofSeconds(10))).isTrue();
        return jdbc.query("""
                        SELECT title, event_count, is_read, resolved_at, resolved_by FROM notifications
                        WHERE user_id = ? AND type = 'REVIEW_FLAGGED' ORDER BY id""",
                (rs, i) -> new Row(rs.getString(1), rs.getInt(2), rs.getBoolean(3), rs.getObject(4) != null,
                        rs.getObject(5, Long.class)),
                userId);
    }

    // ------------------------------------------------------------------

    @Test
    void flaggedReviewsShareOneRowAndModerationCountsDownToResolved() {
        long first = review(productId, null, RUDE);
        long second = review(null, comboId, RUDE);
        review(productId, null, "Lều tốt, dựng nhanh");

        for (long staff : List.of(staffA, staffB)) {
            assertThat(flagged(staff)).singleElement().satisfies(r -> {
                assertThat(r.title()).isEqualTo("Có 2 đánh giá cần kiểm duyệt");
                assertThat(r.eventCount()).isEqualTo(2);
                assertThat(r.resolved()).isFalse();
            });
        }
        jdbc.update("UPDATE notifications SET is_read = TRUE WHERE user_id = ?", staffB);

        adminReviews.hide(first, "Ngôn từ thô tục", staffA);

        for (long staff : List.of(staffA, staffB)) {
            assertThat(flagged(staff)).singleElement().satisfies(r -> {
                assertThat(r.title()).isEqualTo("Có 1 đánh giá cần kiểm duyệt");
                assertThat(r.eventCount()).as("bớt hàng chờ không phải sự kiện mới").isEqualTo(2);
                assertThat(r.resolved()).isFalse();
            });
        }
        assertThat(flagged(staffB)).singleElement().satisfies(r -> assertThat(r.read()).as("không báo lại").isTrue());

        adminReviews.clearSensitive(second, staffB);

        for (long staff : List.of(staffA, staffB)) {
            assertThat(flagged(staff)).singleElement().satisfies(r -> {
                assertThat(r.resolved()).isTrue();
                assertThat(r.resolvedBy()).isEqualTo(staffB);
            });
        }

        review(productId, null, RUDE);
        assertThat(flagged(staffA)).as("hàng chờ có lại → dòng mới").hasSize(2).last()
                .satisfies(r -> assertThat(r.title()).isEqualTo("Có 1 đánh giá cần kiểm duyệt"));
    }

    @Test
    void customerEditingRudeWordsAwayOrDeletingResolvesAsSystem() {
        long id = review(productId, null, RUDE);
        long owner = ownerOf(id);
        assertThat(flagged(staffA)).singleElement().satisfies(r -> assertThat(r.resolved()).isFalse());

        reviewService.update(owner, id, new ReviewRequests.Update(3, "Giao hơi chậm"));

        assertThat(flagged(staffA)).singleElement().satisfies(r -> {
            assertThat(r.resolved()).isTrue();
            assertThat(r.resolvedBy()).as("do khách → Hệ thống").isNull();
        });

        reviewService.update(owner, id, new ReviewRequests.Update(1, RUDE));
        assertThat(flagged(staffA)).hasSize(2).last().satisfies(r -> assertThat(r.resolved()).isFalse());

        reviewService.delete(owner, id);
        assertThat(flagged(staffA)).hasSize(2).last().satisfies(r -> {
            assertThat(r.resolved()).isTrue();
            assertThat(r.resolvedBy()).isNull();
        });
    }

    @Test
    void unhidingSensitiveReviewPutsItBackForOtherStaff() {
        long id = review(productId, null, RUDE);
        adminReviews.hide(id, "Ngôn từ thô tục", staffA);
        assertThat(flagged(staffB)).singleElement().satisfies(r -> assertThat(r.resolvedBy()).isEqualTo(staffA));

        adminReviews.unhide(id, staffA);

        assertThat(flagged(staffB)).hasSize(2).last().satisfies(r -> {
            assertThat(r.title()).isEqualTo("Có 1 đánh giá cần kiểm duyệt");
            assertThat(r.resolved()).isFalse();
        });
        assertThat(flagged(staffA)).as("người hiện lại không tự nhận").hasSize(1);
    }

    @Test
    void safeReviewsAndModeratingSafeReviewsNotifyNobody() {
        long id = review(productId, null, "Rất tốt");
        adminReviews.hide(id, "Thử", staffA);
        adminReviews.unhide(id, staffA);
        adminReviews.clearSensitive(id, staffA);

        assertThat(flagged(staffA)).isEmpty();
        assertThat(flagged(staffB)).isEmpty();
    }
}
