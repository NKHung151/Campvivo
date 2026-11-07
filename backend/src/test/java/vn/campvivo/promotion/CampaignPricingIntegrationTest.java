package vn.campvivo.promotion;

import jakarta.servlet.Filter;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.cart.CartService;
import vn.campvivo.cart.dto.CartRequests;
import vn.campvivo.catalog.ProductRepository;
import vn.campvivo.order.OrderService;
import vn.campvivo.order.dto.CreateOrderResponse;
import vn.campvivo.order.dto.OrderRequests;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.support.AdminMockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Giá chiến dịch trên PostgreSQL thật (BR-6, D-30): `Product.salePrice` lấy mức thấp nhất trên các chiến dịch hiệu lực,
 * danh sách/lọc/sắp xếp/đặt hàng dùng cùng giá, combo không đổi, trang chiến dịch, API admin (+ cảnh báo chồng).
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
class CampaignPricingIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private CartService cartService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private GhnService ghn;

    private long category;
    private long tent;   // 500.000
    private long stove;  // 80.000
    private long lamp;   // 99.990
    private long combo;  // 900.000
    private MockMvc anonymous;

    /**
     * Dọn dữ liệu đã tạo: test catalog/combo xoá `products` ở đầu mỗi test nên không được để lại dòng nào tham chiếu tới
     * sản phẩm/combo (cart_items, order_items, combo_items, campaign_products, images). Nếu không, kết quả phụ thuộc thứ tự
     * chạy các lớp test (xanh ở máy này, đỏ ở CI).
     */
    @AfterEach
    void cleanUp() {
        jdbc.execute("TRUNCATE TABLE products, product_combos CASCADE");
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM discount_campaigns");
        jdbc.update("DELETE FROM carts");
        jdbc.update("DELETE FROM combo_items");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        category = jdbc.queryForObject(
                "INSERT INTO categories (name, slug, path) VALUES ('Lều', 'leu', '/') RETURNING id", Long.class);
        tent = product("Lều 2 người", "leu-2-nguoi", 500_000);
        stove = product("Bếp cồn", "bep-con", 80_000);
        lamp = product("Đèn pin", "den-pin", 99_990);
        combo = jdbc.queryForObject("INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity) "
                + "VALUES (?, 'Combo cắm trại', 'combo-cam-trai', 900000, 5) RETURNING id", Long.class, category);
        when(ghn.calculateFee(any())).thenReturn(30_000L);
        anonymous = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    private long product(String name, String slug, long price) {
        return jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price, stock_quantity) "
                + "VALUES (?, ?, ?, ?, 20) RETURNING id", Long.class, category, name, slug, price);
    }

    /** `from`/`to`: khoảng so với now() của DB, vd. "-1 day", "+1 day". */
    private long campaign(String slug, String type, long value, String from, String to, boolean active, long... productIds) {
        long id = jdbc.queryForObject("""
                INSERT INTO discount_campaigns (name, slug, discount_type, discount_value, start_date, end_date, is_active)
                VALUES (?, ?, ?, ?, now() + ?::interval, now() + ?::interval, ?) RETURNING id
                """, Long.class, "Chiến dịch " + slug, slug, type, value, from, to, active);
        for (long p : productIds) {
            jdbc.update("INSERT INTO discount_campaign_products (campaign_id, product_id) VALUES (?, ?)", id, p);
        }
        return id;
    }

    private BigDecimal salePrice(long productId) {
        return productRepository.findById(productId).orElseThrow().getSalePrice();
    }

    // ------------------------------------------------------------------ BR-6

    @Test
    void salePriceTakesBestActiveCampaignOnly() {
        assertThat(salePrice(tent)).isEqualByComparingTo("500000");

        campaign("pct20", "PERCENTAGE", 20, "-1 day", "1 day", true, tent);
        assertThat(salePrice(tent)).isEqualByComparingTo("400000");

        campaign("fix150", "FIXED_AMOUNT", 150_000, "-1 hour", "2 day", true, tent);       // chồng → lấy thấp nhất
        campaign("expired", "PERCENTAGE", 90, "-3 day", "-1 day", true, tent);
        campaign("upcoming", "PERCENTAGE", 90, "1 day", "3 day", true, tent);
        campaign("off", "PERCENTAGE", 90, "-1 day", "1 day", false, tent);
        assertThat(salePrice(tent)).isEqualByComparingTo("350000");

        campaign("big-fixed", "FIXED_AMOUNT", 100_000, "-1 day", "1 day", true, stove);   // FIXED > giá → 0
        campaign("pct15", "PERCENTAGE", 15, "-1 day", "1 day", true, lamp);               // 84991.5 → HALF_UP
        assertThat(salePrice(stove)).isEqualByComparingTo("0");
        assertThat(salePrice(lamp)).isEqualByComparingTo(
                Discounts.campaignPrice(BigDecimal.valueOf(99_990), DiscountType.PERCENTAGE, BigDecimal.valueOf(15)));
        assertThat(salePrice(lamp)).isEqualByComparingTo("84992");
    }

    @Test
    void productListFilterSortAndDetailUseSalePrice() throws Exception {
        campaign("xa-hang", "PERCENTAGE", 20, "-1 day", "1 day", true, tent);    // 500k → 400k

        anonymous.perform(get("/api/products").param("minPrice", "300000").param("maxPrice", "450000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].slug").value("leu-2-nguoi"))
                .andExpect(jsonPath("$.data[0].originalPrice").value(500000))
                .andExpect(jsonPath("$.data[0].price").value(400000));

        // 80k, 99.99k, 400k (giá gốc 500k) — giảm dần theo giá sau chiến dịch
        anonymous.perform(get("/api/products").param("sortBy", "price_desc"))
                .andExpect(jsonPath("$.data[*].slug", contains("leu-2-nguoi", "den-pin", "bep-con")));

        anonymous.perform(get("/api/products/leu-2-nguoi"))
                .andExpect(jsonPath("$.data.price").value(400000))
                .andExpect(jsonPath("$.data.campaign.slug").value("xa-hang"))
                .andExpect(jsonPath("$.data.campaign.endDate").exists());
        anonymous.perform(get("/api/products/bep-con")).andExpect(jsonPath("$.data.campaign").doesNotExist());
    }

    @Test
    void onSaleListsDiscountedProductsOnceWithBestPrice() throws Exception {
        campaign("pct20", "PERCENTAGE", 20, "-1 day", "1 day", true, tent, lamp);   // tent 400k, lamp 79.992
        campaign("fix150", "FIXED_AMOUNT", 150_000, "-1 day", "1 day", true, tent); // tent 350k (lớn hơn)
        campaign("expired", "PERCENTAGE", 50, "-3 day", "-1 day", true, stove);     // không còn giảm

        anonymous.perform(get("/api/products").param("onSale", "true").param("sortBy", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(2))
                .andExpect(jsonPath("$.data[*].slug", contains("den-pin", "leu-2-nguoi")))
                .andExpect(jsonPath("$.data[1].price").value(350000));
        anonymous.perform(get("/api/products").param("onSale", "true").param("maxPrice", "100000"))
                .andExpect(jsonPath("$.data[*].slug", contains("den-pin")));
        anonymous.perform(get("/api/products")).andExpect(jsonPath("$.pagination.totalItems").value(3));
    }

    @Test
    void campaignListShowsOnlyRunningCampaignsWithActiveProductCount() throws Exception {
        campaign("ket-thuc-sau", "PERCENTAGE", 20, "-1 day", "5 day", true, tent, stove);
        campaign("ket-thuc-truoc", "FIXED_AMOUNT", 10_000, "-1 day", "1 day", true, lamp);
        campaign("da-het", "PERCENTAGE", 20, "-3 day", "-1 day", true, tent);
        campaign("sap-toi", "PERCENTAGE", 20, "1 day", "3 day", true, tent);
        campaign("tat", "PERCENTAGE", 20, "-1 day", "1 day", false, tent);
        jdbc.update("UPDATE products SET is_active = false WHERE id = ?", stove);

        anonymous.perform(get("/api/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].slug", contains("ket-thuc-truoc", "ket-thuc-sau")))
                .andExpect(jsonPath("$.data[1].productCount").value(1));
    }

    @Test
    void orderChargesSalePriceButComboKeepsComboPrice() {
        campaign("xa-hang", "PERCENTAGE", 20, "-1 day", "1 day", true, tent);
        User buyer = users.findByEmail("buyer@campvivo.vn").orElseGet(() -> {
            User u = new User("buyer@campvivo.vn", "hash", "Khách");
            u.setEmailVerified(true);
            return users.saveAndFlush(u);
        });
        cartService.addItem(buyer.getId(), new CartRequests.AddItem(tent, null, 1));
        cartService.addItem(buyer.getId(), new CartRequests.AddItem(null, combo, 1));
        assertThat(cartService.getCart(buyer.getId()).subtotal()).isEqualByComparingTo("1300000");

        CreateOrderResponse res = orderService.create(buyer.getId(), new OrderRequests.Create("Nguyễn Văn A", "0901234567",
                "12 Lê Lợi, Quận 1, Hồ Chí Minh", 1442, "20109", false, PaymentMethod.COD, null, null, 1_330_000L, null),
                "127.0.0.1");

        Map<String, Object> line = jdbc.queryForMap(
                "SELECT original_price, unit_price FROM order_items WHERE order_id = ? AND product_id = ?", res.orderId(), tent);
        assertThat(line.get("original_price").toString()).isEqualTo("500000");
        assertThat(line.get("unit_price").toString()).isEqualTo("400000");
        assertThat(jdbc.queryForObject("SELECT unit_price FROM order_items WHERE order_id = ? AND combo_id = ?",
                BigDecimal.class, res.orderId(), combo)).isEqualByComparingTo("900000");
    }

    @Test
    void campaignPageOnlyWhileActive() throws Exception {
        campaign("dang-chay", "PERCENTAGE", 20, "-1 day", "1 day", true, tent, stove);
        campaign("da-het", "PERCENTAGE", 20, "-3 day", "-1 day", true, tent);
        campaign("sap-toi", "PERCENTAGE", 20, "1 day", "3 day", true, tent);
        jdbc.update("UPDATE products SET is_active = false WHERE id = ?", stove);

        anonymous.perform(get("/api/campaigns/dang-chay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products.length()").value(1))
                .andExpect(jsonPath("$.data.products[0].price").value(400000));
        anonymous.perform(get("/api/campaigns/da-het")).andExpect(status().isNotFound());
        anonymous.perform(get("/api/campaigns/sap-toi")).andExpect(status().isNotFound());
        anonymous.perform(get("/api/campaigns/khong-co")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ admin

    private static final String BODY = """
            {"name":"Sale hè","slug":%s,"discountType":"PERCENTAGE","discountValue":20,"startDate":"%s","endDate":"%s",
             "isActive":true,"productIds":%s}
            """;

    @Test
    void adminCrudAndOverlapWarning() throws Exception {
        MockMvc admin = AdminMockMvc.admin(context, users, jwt);
        String start = "2026-01-01T00:00:00+07:00";
        String end = "2099-01-01T00:00:00+07:00";

        admin.perform(post("/api/admin/campaigns").contentType("application/json")
                        .content(BODY.formatted("null", end, start, "[" + tent + "]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.endDate").exists());
        admin.perform(post("/api/admin/campaigns").contentType("application/json")
                        .content(BODY.formatted("null", start, end, "[999999]")))
                .andExpect(status().isNotFound());

        String created = admin.perform(post("/api/admin/campaigns").contentType("application/json")
                        .content(BODY.formatted("null", start, end, "[" + tent + "," + stove + "]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("sale-he"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.productCount").value(2))
                .andExpect(jsonPath("$.data.products[*].campaignPrice", containsInAnyOrder(400000, 64000)))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*\"data\":\\{\"id\":(\\d+).*", "$1"));

        // Gán sản phẩm → giá hiển thị đổi ngay
        anonymous.perform(get("/api/products/leu-2-nguoi")).andExpect(jsonPath("$.data.price").value(400000));
        admin.perform(post("/api/admin/campaigns").contentType("application/json")
                        .content(BODY.formatted("\"sale-he\"", start, end, "[]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SLUG_ALREADY_EXISTS"));

        admin.perform(get("/api/admin/campaigns/overlaps").param("productIds", tent + "," + lamp)
                        .param("startDate", "2026-06-01T00:00:00+07:00").param("endDate", "2026-07-01T00:00:00+07:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(tent))
                .andExpect(jsonPath("$.data[0].campaignId").value(id));
        admin.perform(get("/api/admin/campaigns/overlaps").param("productIds", String.valueOf(tent))
                        .param("startDate", start).param("endDate", end).param("excludeId", String.valueOf(id)))
                .andExpect(jsonPath("$.data.length()").value(0));

        admin.perform(put("/api/admin/campaigns/" + id).contentType("application/json")
                        .content(BODY.formatted("null", start, end, "[" + lamp + "]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("sale-he"))
                .andExpect(jsonPath("$.data.productCount").value(1));
        anonymous.perform(get("/api/products/leu-2-nguoi")).andExpect(jsonPath("$.data.price").value(500000));

        admin.perform(get("/api/admin/campaigns").param("status", "RUNNING").param("q", "SALE"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productCount").value(1));

        admin.perform(delete("/api/admin/campaigns/" + id)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM discount_campaign_products", Integer.class)).isZero();

        AdminMockMvc.as(Role.EMPLOYEE, context, users, jwt).perform(get("/api/admin/campaigns"))
                .andExpect(status().isForbidden());
    }
}
