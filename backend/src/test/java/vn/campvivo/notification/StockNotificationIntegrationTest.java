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
import vn.campvivo.cart.CartService;
import vn.campvivo.cart.dto.CartRequests;
import vn.campvivo.combo.admin.AdminComboService;
import vn.campvivo.inventory.InventoryReason;
import vn.campvivo.inventory.admin.AdjustmentRequest;
import vn.campvivo.inventory.admin.AdminInventoryService;
import vn.campvivo.order.OrderService;
import vn.campvivo.order.dto.OrderRequests;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.shipping.GhnCreateOrderResult;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * `LOW_STOCK` (D-44, ngưỡng mặc định 5) qua các đường đổi kho thật: đặt đơn (giữ chỗ), khách huỷ (trả giữ chỗ), nhập / xuất tay,
 * đóng gói combo, xuất combo hỏng. PostgreSQL thật; GHN giả lập. Chỉ kiểm tra thông báo của nhân viên do test tạo.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long",
        "app.vnpay.tmn-code=TESTTMN1",
        "app.vnpay.hash-secret=TESTSECRETKEY1234567890ABCDEFGHI",
        "app.vnpay.pay-url=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
        "app.vnpay.return-url=http://localhost:3000/payment/vnpay-return",
        "app.shipping.free-threshold=100000000",
        "app.order.timeout-job-delay=1h",
        "app.inventory.low-stock-threshold=5"
})
class StockNotificationIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CartService cartService;
    @Autowired private OrderService orderService;
    @Autowired private AdminInventoryService inventoryAdmin;
    @Autowired private AdminComboService comboAdmin;
    @Autowired private NotificationDispatcher dispatcher;
    @MockBean private GhnService ghn;

    private long customer;
    private long staffA;
    private long staffB;
    private long category;
    private long tent;
    private long tentVariant;

    private record Row(String type, String refType, Long refId, String title, String content, String link, int eventCount,
                       boolean read, boolean resolved, Long resolvedBy) {}

    @BeforeEach
    void setUp() {
        wipe();
        category = jdbc.queryForObject(
                "INSERT INTO categories (name, slug, path) VALUES ('Lều', 'leu-stock-nt', '/') RETURNING id", Long.class);
        tent = product("Lều 2 người", "leu-2-stock-nt", 7);
        tentVariant = jdbc.queryForObject("SELECT id FROM product_variants WHERE product_id = ?", Long.class, tent);
        customer = user(Role.CUSTOMER, "Nguyễn Khách");
        staffA = user(Role.EMPLOYEE, "Trần A");
        staffB = user(Role.ADMIN, "Lê B");
        when(ghn.calculateFee(any())).thenReturn(30_000L);
        when(ghn.createOrder(any())).thenReturn(new GhnCreateOrderResult("GHNST1", 30_000, null));
    }

    @AfterEach
    void tearDown() {
        dispatcher.drain(Duration.ofSeconds(10));
        wipe();
        jdbc.update("DELETE FROM users WHERE email LIKE 'nt-stock-%'");
    }

    private void wipe() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM carts");
        jdbc.update("DELETE FROM inventory_logs");
        jdbc.update("DELETE FROM combo_items");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
    }

    private long product(String name, String slug, int stock) {
        return jdbc.queryForObject("""
                WITH p AS (INSERT INTO products (category_id, name, slug, price, stock_quantity)
                           VALUES (?, ?, ?, 100000, ?) RETURNING id, stock_quantity),
                v AS (INSERT INTO product_variants (product_id, stock_quantity) SELECT id, stock_quantity FROM p)
                SELECT id FROM p""", Long.class, category, name, slug, stock);
    }

    private long user(Role role, String name) {
        User u = new User("nt-stock-" + UUID.randomUUID() + "@test.vn", "hash", name);
        u.setRole(role);
        u.setEmailVerified(true);
        return users.saveAndFlush(u).getId();
    }

    /** Khách đặt COD `qty` cái lều (giữ chỗ). */
    private long order(int qty) {
        cartService.addItem(customer, new CartRequests.AddItem(tent, tentVariant, null, qty));
        return orderService.create(customer, new OrderRequests.Create("Nguyễn Khách", "0901234567", "12 Lê Lợi", 1442,
                "20109", false, PaymentMethod.COD, null, null, 100_000L * qty + 30_000, null), "127.0.0.1").orderId();
    }

    private void adjustTent(int change, InventoryReason reason, long staff) {
        inventoryAdmin.adjust(new AdjustmentRequest(tent, null, null, change, reason, null, null), staff);
    }

    private List<Row> lowStock(long userId) {
        assertThat(dispatcher.drain(Duration.ofSeconds(10))).isTrue();
        return jdbc.query("""
                        SELECT type, ref_type, ref_id, title, content, link, event_count, is_read, resolved_at, resolved_by
                        FROM notifications WHERE user_id = ? AND type = 'LOW_STOCK' ORDER BY id""",
                (rs, i) -> new Row(rs.getString(1), rs.getString(2), rs.getObject(3, Long.class), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getInt(7), rs.getBoolean(8), rs.getObject(9) != null,
                        rs.getObject(10, Long.class)),
                userId);
    }

    // ------------------------------------------------------------------

    @Test
    void ordersDroppingToThresholdNotifyOnceGroupedThenImportResolvesForEveryone() {
        assertThat(lowStock(staffA)).as("7 > ngưỡng 5").isEmpty();

        order(2); // 7 → 5

        for (long staff : List.of(staffA, staffB)) {
            assertThat(lowStock(staff)).singleElement().satisfies(r -> {
                assertThat(r.refType()).isEqualTo("PRODUCT");
                assertThat(r.refId()).isEqualTo(tent);
                assertThat(r.title()).isEqualTo("Lều 2 người sắp hết hàng");
                assertThat(r.content()).startsWith("Còn 5 khả dụng (ngưỡng cảnh báo 5)");
                assertThat(r.link()).isEqualTo("/admin/inventory?tab=products&q=L%E1%BB%81u+2+ng%C6%B0%E1%BB%9Di");
                assertThat(r.resolved()).isFalse();
            });
        }
        assertThat(lowStock(customer)).isEmpty();

        jdbc.update("UPDATE notifications SET is_read = TRUE WHERE user_id = ?", staffB);
        order(3); // 5 → 2

        for (long staff : List.of(staffA, staffB)) {
            assertThat(lowStock(staff)).as("vẫn một dòng gộp").singleElement().satisfies(r -> {
                assertThat(r.content()).startsWith("Còn 2 khả dụng");
                assertThat(r.eventCount()).isEqualTo(2);
                assertThat(r.read()).as("giảm thêm → báo lại").isFalse();
            });
        }

        adjustTent(10, InventoryReason.IMPORT, staffA); // 2 → 12

        for (long staff : List.of(staffA, staffB)) {
            assertThat(lowStock(staff)).singleElement().satisfies(r -> {
                assertThat(r.resolved()).isTrue();
                assertThat(r.resolvedBy()).isEqualTo(staffA);
            });
        }

        order(7); // 12 → 5: dòng cũ đã đóng nhóm → dòng MỚI
        assertThat(lowStock(staffB)).hasSize(2).last().satisfies(r -> {
            assertThat(r.resolved()).isFalse();
            assertThat(r.eventCount()).isEqualTo(1);
        });
    }

    @Test
    void cancelRaisingStockWithinThresholdOnlyRefreshesAndAboveThresholdResolvesAsSystem() {
        long first = order(2);  // 7 → 5
        long second = order(2); // 5 → 3
        assertThat(lowStock(staffA)).singleElement().satisfies(r -> assertThat(r.content()).startsWith("Còn 3"));
        jdbc.update("UPDATE notifications SET is_read = TRUE WHERE user_id = ?", staffA);

        orderService.cancelByCustomer(customer, second, "Đặt nhầm"); // 3 → 5

        assertThat(lowStock(staffA)).singleElement().satisfies(r -> {
            assertThat(r.content()).as("số mới").startsWith("Còn 5 khả dụng");
            assertThat(r.read()).as("tăng → không báo lại").isTrue();
            assertThat(r.eventCount()).isEqualTo(2);
            assertThat(r.resolved()).isFalse();
        });

        orderService.cancelByCustomer(customer, first, "Đổi ý"); // 5 → 7

        assertThat(lowStock(staffA)).singleElement().satisfies(r -> {
            assertThat(r.resolved()).isTrue();
            assertThat(r.resolvedBy()).as("huỷ đơn → Hệ thống").isNull();
        });
    }

    @Test
    void staffExportBelowThresholdNotifiesOthersButNotTheActor() {
        adjustTent(-3, InventoryReason.EXPORT, staffA); // 7 → 4

        assertThat(lowStock(staffA)).as("người gây ra không nhận").isEmpty();
        assertThat(lowStock(staffB)).singleElement()
                .satisfies(r -> assertThat(r.content()).startsWith("Còn 4 khả dụng"));

        adjustTent(-4, InventoryReason.EXPORT, staffB); // 4 → 0

        assertThat(lowStock(staffB)).singleElement().as("B tự xuất → dòng của B không đổi")
                .satisfies(r -> assertThat(r.content()).startsWith("Còn 4"));
        assertThat(lowStock(staffA)).singleElement().satisfies(r -> {
            assertThat(r.title()).isEqualTo("Lều 2 người đã hết hàng");
            assertThat(r.content()).startsWith("Không còn hàng khả dụng");
        });
    }

    @Test
    void packingComboLowersComponentAndWritingOffComboReachesZero() {
        long stove = product("Bếp gas mini", "bep-gas-stock-nt", 6);
        long combo = jdbc.queryForObject("""
                INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity)
                VALUES (?, 'Combo bếp', 'combo-bep-stock-nt', 300000, 0) RETURNING id""", Long.class, category);
        jdbc.update("""
                INSERT INTO combo_items (combo_id, product_id, variant_id, quantity)
                SELECT ?, product_id, id, 1 FROM product_variants WHERE product_id IN (?, ?)""", combo, tent, stove);

        comboAdmin.pack(combo, 2, staffA); // lều 7 → 5, bếp 6 → 4, combo 0 → 2 (tăng)

        assertThat(lowStock(staffB)).extracting(Row::refType, Row::refId)
                .as("thành phần giảm tới ngưỡng; combo tăng thì không báo")
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("PRODUCT", tent),
                        org.assertj.core.groups.Tuple.tuple("PRODUCT", stove));
        assertThat(lowStock(staffA)).isEmpty();

        inventoryAdmin.adjust(new AdjustmentRequest(null, null, combo, -2, InventoryReason.EXPORT, "Hỏng", null),
                staffA); // combo 2 → 0

        assertThat(lowStock(staffB)).filteredOn(r -> "COMBO".equals(r.refType())).singleElement().satisfies(r -> {
            assertThat(r.refId()).isEqualTo(combo);
            assertThat(r.title()).isEqualTo("Combo bếp đã hết hàng");
            assertThat(r.link()).startsWith("/admin/inventory?tab=combos&q=");
        });
    }
}
