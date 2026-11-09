package vn.campvivo.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.support.AdminMockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dashboard `/api/admin/analytics/*` (FR-24) trên PostgreSQL thật. Đơn đặt trong tháng 3/2025 để không lẫn dữ liệu test khác;
 * mốc ngày tính theo giờ Việt Nam (đơn hoàn tất 00:30 ngày 1/4 giờ VN = 17:30 ngày 31/3 UTC thuộc tháng 4).
 * Cấu hình giống các test review để dùng chung Spring context.
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
class AnalyticsIntegrationTest {

    private static final String BASE = "/api/admin/analytics";

    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @MockBean private GhnService ghn;

    private MockMvc admin;
    private long tent;
    private long stove;
    private long combo;
    private long buyer;

    @BeforeEach
    void setUp() {
        cleanUp();
        long category = jdbc.queryForObject(
                "INSERT INTO categories (name, slug, path) VALUES ('Lều', 'leu-an', '/') RETURNING id", Long.class);
        tent = jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price) "
                + "VALUES (?, 'Lều 2 người', 'leu-an-2', 100000) RETURNING id", Long.class, category);
        stove = jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price) "
                + "VALUES (?, 'Bếp gas', 'bep-an', 50000) RETURNING id", Long.class, category);
        combo = jdbc.queryForObject("INSERT INTO product_combos (category_id, name, slug, combo_price) "
                + "VALUES (?, 'Combo cắm trại', 'combo-an', 100000) RETURNING id", Long.class, category);
        buyer = customer("an-buyer", "2025-02-01T10:00:00+07:00");
        customer("an-new", "2025-03-15T10:00:00+07:00");
        admin = AdminMockMvc.admin(context, users, jwt);

        // Tháng 3: 2 đơn hoàn tất (300k + 200k), 1 đơn trả hàng, 1 đơn chờ; đơn hoàn tất 00:30 ngày 1/4 giờ VN thuộc tháng 4
        long a = order("COMPLETED", 300_000, "2025-03-02T09:00:00+07:00", "2025-03-05T10:00:00+07:00");
        item(a, tent, null, "Lều 2 người", 3, 300_000);
        long b = order("COMPLETED", 200_000, "2025-03-30T09:00:00+07:00", "2025-03-31T23:30:00+07:00");
        item(b, null, combo, "Combo cắm trại", 2, 200_000);
        long c = order("COMPLETED", 250_000, "2025-03-31T09:00:00+07:00", "2025-04-01T00:30:00+07:00");
        item(c, stove, null, "Bếp gas", 5, 250_000);
        long d = order("RETURNED", 900_000, "2025-03-10T09:00:00+07:00", "2025-03-12T10:00:00+07:00");
        item(d, stove, null, "Bếp gas", 9, 900_000);
        order("PENDING", 100_000, "2025-03-20T09:00:00+07:00", null);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM orders WHERE order_code LIKE 'AN-%'");
        jdbc.update("DELETE FROM users WHERE email LIKE 'an-%@analytics.test'");
        jdbc.update("DELETE FROM product_combos WHERE slug = 'combo-an'");
        jdbc.update("DELETE FROM products WHERE slug IN ('leu-an-2', 'bep-an')");
        jdbc.update("DELETE FROM categories WHERE slug = 'leu-an'");
    }

    @Test
    void summaryCountsCompletedRevenueByVietnamDateOrdersByStatusAndNewCustomers() throws Exception {
        JsonNode s = data("/summary?from=2025-03-01&to=2025-03-31");

        assertThat(s.get("revenue").asLong()).isEqualTo(500_000);
        assertThat(s.get("completedOrders").asLong()).isEqualTo(2);
        // Theo ngày đặt: 3 đơn hoàn tất (gồm đơn hoàn tất sang tháng 4), 1 trả hàng, 1 chờ; đủ 7 trạng thái
        assertThat(s.get("ordersByStatus").get("COMPLETED").asLong()).isEqualTo(3);
        assertThat(s.get("ordersByStatus").get("RETURNED").asLong()).isEqualTo(1);
        assertThat(s.get("ordersByStatus").get("PENDING").asLong()).isEqualTo(1);
        assertThat(s.get("ordersByStatus").get("SHIPPING").asLong()).isZero();
        assertThat(s.get("ordersByStatus").size()).isEqualTo(7);
        assertThat(s.get("newCustomers").asLong()).isEqualTo(1);
    }

    @Test
    void revenueFillsEveryPeriodAndGroupsByVietnamTime() throws Exception {
        JsonNode days = data("/revenue?from=2025-03-30&to=2025-04-01&groupBy=day");
        assertThat(days.findValuesAsText("period")).containsExactly("2025-03-30", "2025-03-31", "2025-04-01");
        assertThat(days.findValuesAsText("revenue")).containsExactly("0", "200000", "250000");
        assertThat(days.findValuesAsText("orderCount")).containsExactly("0", "1", "1");

        JsonNode months = data("/revenue?from=2025-02-15&to=2025-04-10&groupBy=month");
        assertThat(months.findValuesAsText("period")).containsExactly("2025-02", "2025-03", "2025-04");
        assertThat(months.findValuesAsText("revenue")).containsExactly("0", "500000", "250000");

        JsonNode years = data("/revenue?from=2024-06-01&to=2025-12-31&groupBy=year");
        assertThat(years.findValuesAsText("period")).containsExactly("2024", "2025");
        assertThat(years.findValuesAsText("revenue")).containsExactly("0", "750000");
    }

    @Test
    void topItemsRankByQuantityInCompletedOrdersOnly() throws Exception {
        JsonNode top = data("/top-items?from=2025-03-01&to=2025-03-31&limit=5");

        // Bếp gas: đơn trả hàng (9) không tính, đơn hoàn tất 1/4 ngoài khoảng → không có
        assertThat(top.findValuesAsText("name")).containsExactly("Lều 2 người", "Combo cắm trại");
        assertThat(top.get(0).get("type").asText()).isEqualTo("PRODUCT");
        assertThat(top.get(0).get("id").asLong()).isEqualTo(tent);
        assertThat(top.get(0).get("quantitySold").asLong()).isEqualTo(3);
        assertThat(top.get(1).get("type").asText()).isEqualTo("COMBO");
        assertThat(top.get(1).get("revenue").asLong()).isEqualTo(200_000);
    }

    @Test
    void invalidInputIsRejectedAndOnlyAdminCanRead() throws Exception {
        admin.perform(get(BASE + "/summary?from=2025-04-01&to=2025-03-01")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.from").exists());
        admin.perform(get(BASE + "/revenue?groupBy=week")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.groupBy").exists());
        admin.perform(get(BASE + "/revenue?from=2024-01-01&to=2025-03-01&groupBy=day")).andExpect(status().isBadRequest());
        admin.perform(get(BASE + "/top-items?limit=51")).andExpect(status().isBadRequest());
        admin.perform(get(BASE + "/summary")).andExpect(status().isOk());
        AdminMockMvc.as(Role.EMPLOYEE, context, users, jwt).perform(get(BASE + "/summary")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------------------------

    private JsonNode data(String path) throws Exception {
        String body = admin.perform(get(BASE + path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body).get("data");
    }

    private long customer(String name, String createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, full_name, role, created_at)
                VALUES (?, 'x', 'Khách', 'CUSTOMER', ?::timestamptz) RETURNING id
                """, Long.class, name + "@analytics.test", createdAt);
    }

    private long order(String status, long amount, String createdAt, String completedAt) {
        return jdbc.queryForObject("""
                INSERT INTO orders (order_code, user_id, subtotal, final_amount, recipient_name, recipient_phone,
                                    shipping_address, shipping_district_id, shipping_ward_code, payment_method, status,
                                    created_at, completed_at)
                VALUES (?, ?, ?, ?, 'A', '0901234567', 'HN', 1442, '20109', 'COD', ?, ?::timestamptz, ?::timestamptz)
                RETURNING id
                """, Long.class, "AN-" + UUID.randomUUID().toString().substring(0, 12), buyer, amount, amount, status,
                createdAt, completedAt);
    }

    private void item(long orderId, Long product, Long comboId, String name, int quantity, long lineTotal) {
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, combo_id, item_name, original_price, unit_price, quantity, line_total)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, orderId, product, comboId, name, lineTotal / quantity, lineTotal / quantity, quantity, lineTotal);
    }
}
