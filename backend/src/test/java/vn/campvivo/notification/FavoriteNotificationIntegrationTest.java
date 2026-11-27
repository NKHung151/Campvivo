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
import vn.campvivo.combo.admin.AdminComboService;
import vn.campvivo.inventory.InventoryReason;
import vn.campvivo.inventory.admin.AdjustmentRequest;
import vn.campvivo.inventory.admin.AdminInventoryService;
import vn.campvivo.promotion.PromotionAnnouncementJob;
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
 * `FAVORITE_ON_SALE` (chiến dịch bắt đầu — D-45) và `FAVORITE_BACK_IN_STOCK` (khả dụng ≤ 0 → &gt; 0 — D-44) chỉ tới khách đang
 * hoạt động yêu thích mặt hàng (D-47). Đổi kho qua đường nghiệp vụ thật (nhập tay, đóng gói combo); chiến dịch qua job công bố.
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
class FavoriteNotificationIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AdminInventoryService inventoryAdmin;
    @Autowired private AdminComboService comboAdmin;
    @Autowired private PromotionAnnouncementJob job;
    @Autowired private NotificationDispatcher dispatcher;
    @MockBean private GhnService ghn;

    private long category;
    private long staff;
    private long fanA;
    private long fanB;
    private long bystander;
    private long locked;

    private record Row(String type, String refType, Long refId, String title, String content, String link, int eventCount,
                       boolean read) {}

    @BeforeEach
    void setUp() {
        wipe();
        category = jdbc.queryForObject(
                "INSERT INTO categories (name, slug, path) VALUES ('Lều', 'leu-fav-nt', '/') RETURNING id", Long.class);
        staff = user(Role.EMPLOYEE);
        fanA = user(Role.CUSTOMER);
        fanB = user(Role.CUSTOMER);
        bystander = user(Role.CUSTOMER);
        locked = user(Role.CUSTOMER);
        jdbc.update("UPDATE users SET is_active = FALSE WHERE id = ?", locked);
    }

    @AfterEach
    void tearDown() {
        dispatcher.drain(Duration.ofSeconds(10));
        wipe();
        jdbc.update("DELETE FROM users WHERE email LIKE 'nt-fav-%'");
    }

    private void wipe() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM favorites");
        jdbc.update("DELETE FROM discount_campaign_products");
        jdbc.update("DELETE FROM discount_campaigns");
        jdbc.update("DELETE FROM inventory_logs");
        jdbc.update("DELETE FROM combo_items");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
    }

    private long user(Role role) {
        User u = new User("nt-fav-" + UUID.randomUUID() + "@test.vn", "hash", "Người dùng");
        u.setRole(role);
        u.setEmailVerified(true);
        return users.saveAndFlush(u).getId();
    }

    private long product(String name, String slug, int stock) {
        return jdbc.queryForObject("""
                WITH p AS (INSERT INTO products (category_id, name, slug, price, stock_quantity)
                           VALUES (?, ?, ?, 100000, ?) RETURNING id, stock_quantity),
                v AS (INSERT INTO product_variants (product_id, stock_quantity) SELECT id, stock_quantity FROM p)
                SELECT id FROM p""", Long.class, category, name, slug, stock);
    }

    private void favorite(long userId, String column, long itemId) {
        jdbc.update("INSERT INTO favorites (user_id, " + column + ") VALUES (?, ?)", userId, itemId);
    }

    private void adjust(long productId, int change, InventoryReason reason) {
        inventoryAdmin.adjust(new AdjustmentRequest(productId, null, null, change, reason, null, null), staff);
    }

    private List<Row> rows(long userId, String type) {
        assertThat(dispatcher.drain(Duration.ofSeconds(10))).isTrue();
        return jdbc.query("""
                        SELECT type, ref_type, ref_id, title, content, link, event_count, is_read
                        FROM notifications WHERE user_id = ? AND type = ? ORDER BY id""",
                (rs, i) -> new Row(rs.getString(1), rs.getString(2), rs.getObject(3, Long.class), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getInt(7), rs.getBoolean(8)),
                userId, type);
    }

    private List<Row> backInStock(long userId) {
        return rows(userId, "FAVORITE_BACK_IN_STOCK");
    }

    private List<Row> onSale(long userId) {
        return rows(userId, "FAVORITE_ON_SALE");
    }

    // ------------------------------------------------------------------ FAVORITE_BACK_IN_STOCK

    @Test
    void restockFromZeroNotifiesActiveFansOnceAndGroupsUntilRead() {
        long tent = product("Lều 2 người", "leu-2-fav-nt", 0);
        favorite(fanA, "product_id", tent);
        favorite(locked, "product_id", tent);

        adjust(tent, 3, InventoryReason.IMPORT); // 0 → 3

        assertThat(backInStock(fanA)).singleElement().satisfies(r -> {
            assertThat(r.refType()).isEqualTo("PRODUCT");
            assertThat(r.refId()).isEqualTo(tent);
            assertThat(r.title()).isEqualTo("Lều 2 người đã có hàng trở lại");
            assertThat(r.link()).isEqualTo("/products/leu-2-fav-nt");
            assertThat(r.read()).isFalse();
        });
        assertThat(backInStock(bystander)).as("không yêu thích").isEmpty();
        assertThat(backInStock(locked)).as("tài khoản bị khoá").isEmpty();
        assertThat(backInStock(staff)).isEmpty();

        adjust(tent, 2, InventoryReason.IMPORT); // 3 → 5: vốn đã có hàng
        assertThat(backInStock(fanA)).singleElement().satisfies(r -> assertThat(r.eventCount()).isEqualTo(1));

        adjust(tent, -5, InventoryReason.EXPORT); // 5 → 0
        adjust(tent, 1, InventoryReason.IMPORT);  // 0 → 1 khi khách chưa đọc → gộp
        assertThat(backInStock(fanA)).singleElement().satisfies(r -> assertThat(r.eventCount()).isEqualTo(2));

        jdbc.update("UPDATE notifications SET is_read = TRUE, group_key = NULL WHERE user_id = ?", fanA); // như PATCH read
        adjust(tent, -1, InventoryReason.EXPORT); // 1 → 0
        adjust(tent, 4, InventoryReason.IMPORT);  // 0 → 4 sau khi đã đọc → dòng mới
        assertThat(backInStock(fanA)).hasSize(2).last().satisfies(r -> assertThat(r.read()).isFalse());
    }

    @Test
    void restockOfHiddenProductDoesNotNotify() {
        long tent = product("Lều ẩn", "leu-an-fav-nt", 0);
        favorite(fanA, "product_id", tent);
        jdbc.update("UPDATE products SET is_active = FALSE WHERE id = ?", tent);

        adjust(tent, 3, InventoryReason.IMPORT);

        assertThat(backInStock(fanA)).isEmpty();
    }

    @Test
    void packingComboFromZeroNotifiesComboFans() {
        long tent = product("Lều 2 người", "leu-combo-fav-nt", 5);
        long combo = jdbc.queryForObject("""
                INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity)
                VALUES (?, 'Combo cắm trại', 'combo-fav-nt', 300000, 0) RETURNING id""", Long.class, category);
        jdbc.update("INSERT INTO combo_items (combo_id, product_id, variant_id, quantity) "
                + "SELECT ?, product_id, id, 1 FROM product_variants WHERE product_id = ?", combo, tent);
        favorite(fanB, "combo_id", combo);
        favorite(fanA, "product_id", tent); // lều 5 → 3: vẫn có hàng → không báo

        comboAdmin.pack(combo, 2, staff); // combo 0 → 2

        assertThat(backInStock(fanB)).singleElement().satisfies(r -> {
            assertThat(r.refType()).isEqualTo("COMBO");
            assertThat(r.title()).isEqualTo("Combo cắm trại đã có hàng trở lại");
            assertThat(r.link()).isEqualTo("/combos/combo-fav-nt");
        });
        assertThat(backInStock(fanA)).isEmpty();
    }

    // ------------------------------------------------------------------ FAVORITE_ON_SALE

    @Test
    void campaignStartSendsOneNotificationPerFanDetailedForOneProductGroupedForMany() {
        long tent = product("Lều 2 người", "leu-sale-fav-nt", 5);
        long stove = product("Bếp gas", "bep-sale-fav-nt", 5);
        long hidden = product("Đèn ẩn", "den-sale-fav-nt", 5);
        long outside = product("Túi ngủ", "tui-sale-fav-nt", 5);
        jdbc.update("UPDATE products SET is_active = FALSE WHERE id = ?", hidden);
        long campaign = jdbc.queryForObject("""
                INSERT INTO discount_campaigns (name, slug, discount_type, discount_value, start_date, end_date)
                VALUES ('Sale hè', 'sale-he-fav-nt', 'PERCENTAGE', 20, now() - interval '1 minute', now() + interval '5 days')
                RETURNING id""", Long.class);
        for (long p : List.of(tent, stove, hidden)) {
            jdbc.update("INSERT INTO discount_campaign_products (campaign_id, product_id) VALUES (?, ?)", campaign, p);
        }
        favorite(fanA, "product_id", tent);
        favorite(fanA, "product_id", hidden);   // ẩn → không tính: A chỉ còn 1 món
        favorite(fanB, "product_id", tent);
        favorite(fanB, "product_id", stove);
        favorite(bystander, "product_id", outside);
        favorite(locked, "product_id", tent);

        assertThat(job.announceDue()).isEqualTo(1);

        assertThat(onSale(fanA)).singleElement().satisfies(r -> {
            assertThat(r.refType()).isEqualTo("PRODUCT");
            assertThat(r.refId()).isEqualTo(tent);
            assertThat(r.title()).isEqualTo("Lều 2 người đang giảm giá");
            assertThat(r.content()).isEqualTo("Giá chỉ còn 80.000đ (giá gốc 100.000đ, giảm 20%).");
            assertThat(r.link()).isEqualTo("/products/leu-sale-fav-nt");
        });
        assertThat(onSale(fanB)).singleElement().satisfies(r -> {
            assertThat(r.refType()).isEqualTo("CAMPAIGN");
            assertThat(r.refId()).isEqualTo(campaign);
            assertThat(r.title()).isEqualTo("2 sản phẩm bạn yêu thích đang giảm giá");
            assertThat(r.link()).isEqualTo("/promotions/sale-he-fav-nt");
        });
        assertThat(onSale(bystander)).as("sản phẩm yêu thích không thuộc chiến dịch").isEmpty();
        assertThat(onSale(locked)).isEmpty();
        assertThat(onSale(staff)).isEmpty();

        assertThat(job.announceDue()).as("công bố một lần").isZero();
        assertThat(onSale(fanA)).hasSize(1);
    }
}
