package vn.campvivo.promotion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import vn.campvivo.notification.NotificationDispatcher;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * `PromotionAnnouncementJob` (D-45) + `VOUCHER_NEW` trên PostgreSQL thật: chỉ voucher công khai đang hiệu lực, còn lượt, chưa
 * công bố; đúng một lần kể cả chạy lại / chạy song song; dữ liệu đã đánh dấu (seed) không gửi. Lịch chạy tự động tắt trong test
 * (PostgresTestContainerConfig) — gọi `announceDue()` trực tiếp.
 */
@SpringBootTest
@RecordApplicationEvents
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long",
        "app.shipping.free-threshold=100000000",
        "app.order.timeout-job-delay=1h",
        "app.notification.fan-out-batch-size=2"
})
class PromotionAnnouncementJobTest {

    @Autowired private PromotionAnnouncementJob job;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private NotificationDispatcher dispatcher;
    @Autowired private ApplicationEvents events;
    @MockBean private GhnService ghn;

    private List<Long> customers;
    private long staff;

    @BeforeEach
    void setUp() {
        wipe();
        // 3 khách, lô 2 → fan-out phải ghi 2 lô
        customers = List.of(user(Role.CUSTOMER), user(Role.CUSTOMER), user(Role.CUSTOMER));
        staff = user(Role.EMPLOYEE);
    }

    @AfterEach
    void tearDown() {
        dispatcher.drain(Duration.ofSeconds(10));
        wipe();
        jdbc.update("DELETE FROM users WHERE email LIKE 'nt-promo-%'");
    }

    private void wipe() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM vouchers");
        jdbc.update("DELETE FROM discount_campaign_products");
        jdbc.update("DELETE FROM discount_campaigns");
    }

    private long user(Role role) {
        User u = new User("nt-promo-" + UUID.randomUUID() + "@test.vn", "hash", "Người dùng");
        u.setRole(role);
        u.setEmailVerified(true);
        return users.saveAndFlush(u).getId();
    }

    private long voucher(String code, String startOffset, String expiryOffset, boolean isPublic, boolean active,
                         int quantity, int used) {
        return jdbc.queryForObject("""
                INSERT INTO vouchers (code, discount_type, discount_value, min_order_value, quantity, used_count,
                                      start_date, expiry_date, is_public, is_active)
                VALUES (?, 'FIXED_AMOUNT', 50000, 500000, ?, ?, now() + ?::interval, now() + ?::interval, ?, ?) RETURNING id
                """, Long.class, code, quantity, used, startOffset, expiryOffset, isPublic, active);
    }

    private long dueVoucher(String code) {
        return voucher(code, "-1 minute", "10 days", true, true, 100, 0);
    }

    /** Số thông báo VOUCHER_NEW của mã `voucherId` cho từng người (sau khi hàng đợi chạy hết). */
    private int count(long userId, long voucherId) {
        assertThat(dispatcher.drain(Duration.ofSeconds(10))).isTrue();
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'VOUCHER_NEW' "
                + "AND ref_type = 'VOUCHER' AND ref_id = ?", Integer.class, userId, voucherId);
    }

    private boolean announced(String table, long id) {
        return jdbc.queryForObject("SELECT announced_at IS NOT NULL FROM " + table + " WHERE id = ?", Boolean.class, id);
    }

    // ------------------------------------------------------------------

    @Test
    void dueVoucherNotifiesEveryActiveCustomerOnceAndNoStaff() {
        long id = dueVoucher("CAMP50");

        assertThat(job.announceDue()).isEqualTo(1);

        for (long c : customers) {
            assertThat(count(c, id)).isEqualTo(1);
        }
        assertThat(count(staff, id)).isZero();
        assertThat(announced("vouchers", id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT title || ' | ' || content || ' | ' || link FROM notifications "
                + "WHERE user_id = ? AND ref_id = ?", String.class, customers.get(0), id))
                .startsWith("Mã giảm giá mới: CAMP50 | Giảm 50.000đ cho đơn từ 500.000đ. Hạn dùng đến ")
                .endsWith(" | /promotions");

        assertThat(job.announceDue()).as("chạy lại không công bố lại").isZero();
        assertThat(count(customers.get(1), id)).isEqualTo(1);
    }

    @Test
    void scheduledVoucherIsAnnouncedOnlyOnceItStarts() {
        long id = voucher("HENGIO", "1 hour", "10 days", true, true, 100, 0);

        assertThat(job.announceDue()).isZero();
        assertThat(count(customers.get(0), id)).isZero();

        jdbc.update("UPDATE vouchers SET start_date = now() - interval '1 second' WHERE id = ?", id);
        assertThat(job.announceDue()).isEqualTo(1);
        assertThat(count(customers.get(0), id)).isEqualTo(1);
    }

    @Test
    void hiddenInactiveExhaustedExpiredAndAlreadyAnnouncedVouchersAreSkipped() {
        long hidden = voucher("AN", "-1 minute", "10 days", false, true, 100, 0);
        long off = voucher("TAT", "-1 minute", "10 days", true, false, 100, 0);
        long exhausted = voucher("HET", "-1 minute", "10 days", true, true, 5, 5);
        long expired = voucher("CU", "-2 days", "-1 day", true, true, 100, 0);
        long seeded = dueVoucher("SEED");
        jdbc.update("UPDATE vouchers SET announced_at = now() - interval '1 day' WHERE id = ?", seeded);

        assertThat(job.announceDue()).isZero();

        for (long id : List.of(hidden, off, exhausted, expired, seeded)) {
            assertThat(count(customers.get(0), id)).isZero();
        }
        assertThat(announced("vouchers", hidden)).as("ẩn → chưa công bố; mở công khai sau sẽ được gửi").isFalse();
    }

    @Test
    void concurrentRunsAnnounceEachVoucherExactlyOnce() throws Exception {
        List<Long> ids = List.of(dueVoucher("A1"), dueVoucher("A2"), dueVoucher("A3"));
        CyclicBarrier start = new CyclicBarrier(2);
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> runAt(start));
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> runAt(start));

        assertThat(first.get() + second.get()).isEqualTo(3);
        for (long id : ids) {
            for (long c : customers) {
                assertThat(count(c, id)).isEqualTo(1);
            }
        }
    }

    private int runAt(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return job.announceDue();
    }

    @Test
    void runningCampaignIsAnnouncedOnceWithItsProducts() {
        long category = jdbc.queryForObject(
                "INSERT INTO categories (name, slug, path) VALUES ('Lều', 'leu-promo-nt', '/') RETURNING id", Long.class);
        long product = jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price, stock_quantity) "
                + "VALUES (?, 'Lều', 'leu-promo-nt', 500000, 5) RETURNING id", Long.class, category);
        long running = campaign("dang-chay", "-1 minute", "10 days");
        long upcoming = campaign("sap-toi", "1 day", "10 days");
        jdbc.update("INSERT INTO discount_campaign_products (campaign_id, product_id) VALUES (?, ?)", running, product);
        try {
            assertThat(job.announceDue()).isEqualTo(1);

            assertThat(events.stream(CampaignStartedEvent.class)).singleElement().satisfies(e -> {
                assertThat(e.campaignId()).isEqualTo(running);
                assertThat(e.slug()).isEqualTo("dang-chay");
                assertThat(e.productIds()).containsExactly(product);
            });
            assertThat(announced("discount_campaigns", running)).isTrue();
            assertThat(announced("discount_campaigns", upcoming)).isFalse();
            assertThat(job.announceDue()).isZero();
        } finally {
            wipe();
            jdbc.update("DELETE FROM products WHERE id = ?", product);
            jdbc.update("DELETE FROM categories WHERE id = ?", category);
        }
    }

    private long campaign(String slug, String start, String end) {
        return jdbc.queryForObject("""
                INSERT INTO discount_campaigns (name, slug, discount_type, discount_value, start_date, end_date)
                VALUES (?, ?, 'PERCENTAGE', 20, now() + ?::interval, now() + ?::interval) RETURNING id
                """, Long.class, "Chiến dịch " + slug, slug, start, end);
    }
}
