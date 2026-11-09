package vn.campvivo.review;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.support.AdminMockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quản trị đánh giá `/api/admin/reviews` trên PostgreSQL thật (FR-18, D-31, D-33): ẩn / hiện lại / đánh dấu an toàn / xoá,
 * ảnh hưởng tới phía công khai và phía khách, bộ lọc, phân quyền Employee / Admin. Cấu hình giống
 * ReviewFlowIntegrationTest để dùng chung Spring context.
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
class AdminReviewIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private GhnService ghn;

    private long productId;
    private long comboId;
    private User buyer;
    private MockMvc asBuyer;
    private MockMvc asEmployee;
    private MockMvc asAdmin;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM reviews");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM carts");
        jdbc.update("DELETE FROM combo_items");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        long category = jdbc.queryForObject(
                "INSERT INTO categories (name, slug, path) VALUES ('Lều', 'leu', '/') RETURNING id", Long.class);
        productId = jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price, stock_quantity) "
                + "VALUES (?, 'Lều 2 người', 'leu-2-nguoi', 500000, 50) RETURNING id", Long.class, category);
        comboId = jdbc.queryForObject("INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity) "
                + "VALUES (?, 'Combo cắm trại', 'combo-cam-trai', 900000, 10) RETURNING id", Long.class, category);
        buyer = user("admin-review-buyer@campvivo.vn", "Nguyễn Văn Ánh");
        asBuyer = mvc(buyer);
        asEmployee = AdminMockMvc.as(Role.EMPLOYEE, context, users, jwt);
        asAdmin = AdminMockMvc.admin(context, users, jwt);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM reviews");
        jdbc.update("DELETE FROM orders");
        jdbc.execute("TRUNCATE TABLE products, product_combos CASCADE");
    }

    // ------------------------------------------------------------------ helpers

    private User user(String email, String fullName) {
        User u = users.findByEmail(email).orElseGet(() -> {
            User created = new User(email, "hash", fullName);
            created.setRole(Role.CUSTOMER);
            created.setEmailVerified(true);
            return created;
        });
        u.setFullName(fullName);
        return users.saveAndFlush(u);
    }

    private MockMvc mvc(User user) {
        Filter security = context.getBean("springSecurityFilterChain", Filter.class);
        return MockMvcBuilders.webAppContextSetup(context).addFilters(security)
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createToken(user)))
                .build();
    }

    private MockMvc anonymous() {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    private void completed(Long userId, Long product, Long combo) {
        long orderId = jdbc.queryForObject("""
                INSERT INTO orders (order_code, user_id, subtotal, final_amount, recipient_name, recipient_phone,
                                    shipping_address, shipping_district_id, shipping_ward_code, payment_method, status)
                VALUES (?, ?, 500000, 500000, 'A', '0901234567', 'HN', 1442, '20109', 'COD', 'COMPLETED') RETURNING id
                """, Long.class, "ORD-" + UUID.randomUUID().toString().substring(0, 12), userId);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, combo_id, item_name, original_price, unit_price, quantity, line_total)
                VALUES (?, ?, ?, 'Hàng', 500000, 500000, 1, 500000)
                """, orderId, product, combo);
    }

    /** Khách mua (đơn COMPLETED) rồi viết đánh giá; trả id. */
    private long review(MockMvc mvc, User u, Long product, Long combo, int rating, String content) throws Exception {
        completed(u.getId(), product, combo);
        String item = product != null ? "\"productId\": " + product : "\"comboId\": " + combo;
        String json = mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{" + item + ", \"rating\": " + rating + ", \"content\": \"" + content + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(json, "$.data.id").longValue();
    }

    private ResultActions hide(MockMvc mvc, long id, String body) throws Exception {
        return mvc.perform(patch("/api/admin/reviews/" + id + "/hide").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // ------------------------------------------------------------------ ẩn / hiện lại

    @Test
    void hideRequiresReasonAndAffectsPublicAndOwnerViews() throws Exception {
        long id = review(asBuyer, buyer, productId, null, 1, "Hàng tệ");

        hide(asEmployee, id, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fieldErrors.reason").exists());
        hide(asEmployee, id, "{\"reason\": \"   \"}").andExpect(status().isBadRequest());
        hide(asEmployee, id, "{\"reason\": \"" + "a".repeat(501) + "\"}").andExpect(status().isBadRequest());
        hide(asEmployee, 999999, "{\"reason\": \"Spam\"}").andExpect(status().isNotFound());

        hide(asEmployee, id, "{\"reason\": \"  Nội dung quảng cáo  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isHidden").value(true))
                .andExpect(jsonPath("$.data.hiddenReason").value("Nội dung quảng cáo"))
                .andExpect(jsonPath("$.data.hiddenByName").value("Test EMPLOYEE"))
                .andExpect(jsonPath("$.data.hiddenAt").value(notNullValue()));

        MockMvc guest = anonymous();
        guest.perform(get("/api/products/" + productId + "/reviews")).andExpect(jsonPath("$.data.length()").value(0));
        guest.perform(get("/api/products/" + productId + "/reviews/summary"))
                .andExpect(jsonPath("$.data.reviewCount").value(0));
        guest.perform(get("/api/products/leu-2-nguoi"))
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.averageRating").value(nullValue()));
        asBuyer.perform(get("/api/reviews/mine?productId=" + productId))
                .andExpect(jsonPath("$.data.isHidden").value(true))
                .andExpect(jsonPath("$.data.hiddenReason").value("Nội dung quảng cáo"));
        asBuyer.perform(put("/api/reviews/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"rating\": 5}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("REVIEW_HIDDEN"));

        // Ẩn lại → ghi đè lý do + người ẩn
        hide(asAdmin, id, "{\"reason\": \"Spam\"}")
                .andExpect(jsonPath("$.data.hiddenReason").value("Spam"))
                .andExpect(jsonPath("$.data.hiddenByName").value("Test ADMIN"));

        asEmployee.perform(patch("/api/admin/reviews/" + id + "/unhide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isHidden").value(false))
                .andExpect(jsonPath("$.data.hiddenReason").value(nullValue()))
                .andExpect(jsonPath("$.data.hiddenByName").value(nullValue()))
                .andExpect(jsonPath("$.data.hiddenAt").value(nullValue()));
        guest.perform(get("/api/products/" + productId + "/reviews/summary"))
                .andExpect(jsonPath("$.data.reviewCount").value(1));

        // Thao tác của nhân viên không đổi updatedAt
        Boolean untouched = jdbc.queryForObject("SELECT updated_at = created_at FROM reviews WHERE id = ?", Boolean.class, id);
        assertThat(untouched).isTrue();
    }

    @Test
    void hiddenReviewCanBeDeletedByOwnerAndRewritten() throws Exception {
        long id = review(asBuyer, buyer, productId, null, 1, "spam");
        hide(asEmployee, id, "{\"reason\": \"Spam\"}").andExpect(status().isOk());
        asBuyer.perform(delete("/api/reviews/" + id)).andExpect(status().isOk());
        asBuyer.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": " + productId + ", \"rating\": 4, \"content\": \"viết lại\"}"))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ đánh dấu an toàn (D-33)

    @Test
    void clearSensitiveRemovesBlurOnlyForSensitiveReviews() throws Exception {
        long id = review(asBuyer, buyer, productId, null, 1, "Đ.m hàng như c*t");
        MockMvc guest = anonymous();
        guest.perform(get("/api/products/" + productId + "/reviews")).andExpect(jsonPath("$.data[0].isSensitive").value(true));

        asEmployee.perform(patch("/api/admin/reviews/" + id + "/clear-sensitive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.moderationStatus").value("CLEARED"))
                .andExpect(jsonPath("$.data.flaggedTerms.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        guest.perform(get("/api/products/" + productId + "/reviews")).andExpect(jsonPath("$.data[0].isSensitive").value(false));
        asEmployee.perform(patch("/api/admin/reviews/" + id + "/clear-sensitive"))
                .andExpect(jsonPath("$.data.moderationStatus").value("CLEARED"));

        User other = user("admin-review-other@campvivo.vn", "Trần Bình");
        long safe = review(mvc(other), other, productId, null, 5, "Lều tốt");
        asEmployee.perform(patch("/api/admin/reviews/" + safe + "/clear-sensitive"))
                .andExpect(jsonPath("$.data.moderationStatus").value("SAFE"));
    }

    // ------------------------------------------------------------------ xoá + phân quyền

    @Test
    void onlyAdminDeletesAndCustomerCannotUseAdminApi() throws Exception {
        long id = review(asBuyer, buyer, productId, null, 3, "ok");

        asEmployee.perform(delete("/api/admin/reviews/" + id))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        asAdmin.perform(delete("/api/admin/reviews/" + id)).andExpect(status().isOk());
        asAdmin.perform(delete("/api/admin/reviews/" + id)).andExpect(status().isNotFound());
        asBuyer.perform(get("/api/reviews/mine?productId=" + productId)).andExpect(jsonPath("$.data").value(nullValue()));
        asBuyer.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": " + productId + ", \"rating\": 4}"))
                .andExpect(status().isCreated());

        asBuyer.perform(get("/api/admin/reviews")).andExpect(status().isForbidden());
        asBuyer.perform(patch("/api/admin/reviews/" + id + "/unhide")).andExpect(status().isForbidden());
        anonymous().perform(get("/api/admin/reviews")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ danh sách + lọc

    @Test
    void listFiltersByKeywordTypeRatingAndStatus() throws Exception {
        User binh = user("admin-review-binh@campvivo.vn", "Trần Bình");
        User chi = user("admin-review-chi@campvivo.vn", "Lê Chi");
        review(asBuyer, buyer, productId, null, 5, "Lều tốt");                      // VISIBLE
        long sensitive = review(mvc(binh), binh, productId, null, 2, "vcl");         // VISIBLE + SENSITIVE
        long hidden = review(mvc(chi), chi, null, comboId, 4, "Combo đủ đồ");         // HIDDEN
        hide(asEmployee, hidden, "{\"reason\": \"Trùng lặp\"}").andExpect(status().isOk());

        asEmployee.perform(get("/api/admin/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(hidden))                     // mới nhất trước
                .andExpect(jsonPath("$.data[0].item.type").value("COMBO"))
                .andExpect(jsonPath("$.data[0].item.slug").value("combo-cam-trai"))
                .andExpect(jsonPath("$.data[0].user.email").value("admin-review-chi@campvivo.vn"))
                .andExpect(jsonPath("$.data[2].user.fullName").value("Nguyễn Văn Ánh"))
                .andExpect(jsonPath("$.data[2].item.name").value("Lều 2 người"))
                .andExpect(jsonPath("$.pagination.limit").value(20))
                .andExpect(jsonPath("$.pagination.totalItems").value(3));

        asEmployee.perform(get("/api/admin/reviews?status=VISIBLE")).andExpect(jsonPath("$.data.length()").value(2));
        asEmployee.perform(get("/api/admin/reviews?status=HIDDEN"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].hiddenReason").value("Trùng lặp"));
        asEmployee.perform(get("/api/admin/reviews?status=SENSITIVE"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(sensitive))
                .andExpect(jsonPath("$.data[0].flaggedTerms").value(hasItem("vcl")));
        asEmployee.perform(get("/api/admin/reviews?rating=2")).andExpect(jsonPath("$.data.length()").value(1));
        asEmployee.perform(get("/api/admin/reviews?itemType=COMBO")).andExpect(jsonPath("$.data.length()").value(1));
        asEmployee.perform(get("/api/admin/reviews?itemType=PRODUCT&rating=5"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("Lều tốt"));

        // q: họ tên (gõ không dấu, hoa thường), email, tên mặt hàng (không dấu)
        assertCount("nguyen van", 1);
        asEmployee.perform(get("/api/admin/reviews").param("q", "ANH"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].user.fullName").value(containsString("Ánh")));
        assertCount("Bình", 1);
        assertCount("review-chi@", 1);
        assertCount("leu 2", 2);
        assertCount("cắm trại", 1);
        assertCount("%", 0);
        assertCount("   ", 3);
        asEmployee.perform(get("/api/admin/reviews").param("q", "nguyen").param("status", "HIDDEN"))
                .andExpect(jsonPath("$.data.length()").value(0));

        asEmployee.perform(get("/api/admin/reviews?page=2&limit=2"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));
    }

    private void assertCount(String q, int expected) throws Exception {
        asEmployee.perform(get("/api/admin/reviews").param("q", q))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(expected));
    }

    @Test
    void invalidFiltersAreRejected() throws Exception {
        for (String query : new String[]{"status=DELETED", "itemType=ARTICLE", "rating=0", "rating=6", "page=0",
                "limit=101", "q=" + "a".repeat(101)}) {
            asEmployee.perform(get("/api/admin/reviews?" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        }
    }
}
