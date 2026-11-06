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
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đánh giá trên PostgreSQL thật (FR-18, BR-10, D-31…D-33): điều kiện "đã mua", 1 đánh giá / mặt hàng, quyền sở hữu,
 * kiểm duyệt từ điển, danh sách/lọc sao/tổng quan, đánh giá bị ẩn, phân quyền. Cấu hình giống
 * CampaignPricingIntegrationTest để dùng chung Spring context.
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
class ReviewFlowIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private GhnService ghn;

    private long productId;
    private long otherProductId;
    private long comboId;
    private User buyer;
    private MockMvc asBuyer;

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
        otherProductId = jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price, stock_quantity) "
                + "VALUES (?, 'Túi ngủ', 'tui-ngu', 300000, 50) RETURNING id", Long.class, category);
        comboId = jdbc.queryForObject("INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity) "
                + "VALUES (?, 'Combo cắm trại', 'combo-cam-trai', 900000, 10) RETURNING id", Long.class, category);
        buyer = user("review-buyer@campvivo.vn", Role.CUSTOMER);
        asBuyer = mvc(buyer);
    }

    /** Không để lại dòng tham chiếu sản phẩm/combo cho lớp test khác (xem VoucherFlowIntegrationTest). */
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM reviews");
        jdbc.update("DELETE FROM orders");
        jdbc.execute("TRUNCATE TABLE products, product_combos CASCADE");
    }

    // ------------------------------------------------------------------ helpers

    private User user(String email, Role role) {
        return users.findByEmail(email).orElseGet(() -> {
            User u = new User(email, "hash", "Khách " + email.substring(0, email.indexOf('@')));
            u.setRole(role);
            u.setEmailVerified(true);
            return users.saveAndFlush(u);
        });
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

    /** Đơn của `userId` ở `status`, có 1 dòng sản phẩm hoặc combo. */
    private long order(Long userId, String status, Long product, Long combo) {
        long orderId = jdbc.queryForObject("""
                INSERT INTO orders (order_code, user_id, subtotal, final_amount, recipient_name, recipient_phone,
                                    shipping_address, shipping_district_id, shipping_ward_code, payment_method, status)
                VALUES (?, ?, 500000, 500000, 'A', '0901234567', 'HN', 1442, '20109', 'COD', ?) RETURNING id
                """, Long.class, "ORD-" + UUID.randomUUID().toString().substring(0, 12), userId, status);
        line(orderId, product, combo);
        return orderId;
    }

    private void line(long orderId, Long product, Long combo) {
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, combo_id, item_name, original_price, unit_price, quantity, line_total)
                VALUES (?, ?, ?, 'Hàng', 500000, 500000, 1, 500000)
                """, orderId, product, combo);
    }

    private void completed(Long userId, long product) {
        order(userId, "COMPLETED", product, null);
    }

    private ResultActions postReview(MockMvc mvc, String body) throws Exception {
        return mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions reviewProduct(MockMvc mvc, long product, int rating, String content) throws Exception {
        String text = content == null ? "null" : "\"" + content + "\"";
        return postReview(mvc, "{\"productId\": " + product + ", \"rating\": " + rating + ", \"content\": " + text + "}");
    }

    private long createdId(ResultActions result) throws Exception {
        String json = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(json, "$.data.id").longValue();
    }

    // ------------------------------------------------------------------ viết

    @Test
    void writesReviewForCompletedPurchase() throws Exception {
        completed(buyer.getId(), productId);
        reviewProduct(asBuyer, productId, 5, "  Lều tốt, dựng nhanh  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.content").value("Lều tốt, dựng nhanh"))
                .andExpect(jsonPath("$.data.userFullName").value("Khách review-buyer"))
                .andExpect(jsonPath("$.data.itemType").value("PRODUCT"))
                .andExpect(jsonPath("$.data.itemId").value(productId))
                .andExpect(jsonPath("$.data.isSensitive").value(false))
                .andExpect(jsonPath("$.data.isHidden").value(false));
    }

    @Test
    void blankContentIsStoredAsNull() throws Exception {
        completed(buyer.getId(), productId);
        reviewProduct(asBuyer, productId, 4, "   ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value(nullValue()));
    }

    @Test
    void notPurchasedOrOrderNotCompletedIsRejected() throws Exception {
        reviewProduct(asBuyer, productId, 5, "ok")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("NOT_PURCHASED"));
        for (String status : new String[]{"PENDING", "CONFIRMED", "SHIPPING", "CANCELLED", "RETURNED"}) {
            order(buyer.getId(), status, productId, null);
        }
        reviewProduct(asBuyer, productId, 5, "ok")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("NOT_PURCHASED"));
    }

    @Test
    void comboPurchaseDoesNotCountForProductAndViceVersa() throws Exception {
        order(buyer.getId(), "COMPLETED", null, comboId);
        reviewProduct(asBuyer, productId, 5, "ok").andExpect(status().isForbidden());
        postReview(asBuyer, "{\"comboId\": " + comboId + ", \"rating\": 4}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.itemType").value("COMBO"));
    }

    @Test
    void oneReviewPerItemAndRewriteAfterDelete() throws Exception {
        completed(buyer.getId(), productId);
        completed(buyer.getId(), productId);                       // mua 2 lần vẫn chỉ 1 đánh giá
        long id = createdId(reviewProduct(asBuyer, productId, 5, "ok"));
        reviewProduct(asBuyer, productId, 3, "lần 2")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("REVIEW_ALREADY_EXISTS"));
        asBuyer.perform(delete("/api/reviews/" + id)).andExpect(status().isOk());
        reviewProduct(asBuyer, productId, 3, "viết lại").andExpect(status().isCreated());
    }

    @Test
    void invalidBodiesAreRejected() throws Exception {
        completed(buyer.getId(), productId);
        reviewProduct(asBuyer, productId, 0, "ok").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        reviewProduct(asBuyer, productId, 6, "ok").andExpect(status().isBadRequest());
        reviewProduct(asBuyer, productId, 5, "a".repeat(2001)).andExpect(status().isBadRequest());
        postReview(asBuyer, "{\"rating\": 5}").andExpect(status().isBadRequest());
        postReview(asBuyer, "{\"productId\": " + productId + ", \"comboId\": " + comboId + ", \"rating\": 5}")
                .andExpect(status().isBadRequest());
        reviewProduct(asBuyer, productId, 5, "a".repeat(2000)).andExpect(status().isCreated());
    }

    @Test
    void inactiveOrUnknownItemIsNotFound() throws Exception {
        completed(buyer.getId(), productId);
        jdbc.update("UPDATE products SET is_active = false WHERE id = ?", productId);
        reviewProduct(asBuyer, productId, 5, "ok").andExpect(status().isNotFound());
        anonymous().perform(get("/api/products/" + productId + "/reviews")).andExpect(status().isNotFound());
        anonymous().perform(get("/api/products/999999/reviews/summary")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ sửa / xoá

    @Test
    void ownerEditsAndUpdatedAtChanges() throws Exception {
        completed(buyer.getId(), productId);
        long id = createdId(reviewProduct(asBuyer, productId, 2, "tạm"));
        asBuyer.perform(put("/api/reviews/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 4, \"content\": \"dùng lâu thấy ổn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.content").value("dùng lâu thấy ổn"));
        Boolean edited = jdbc.queryForObject("SELECT updated_at > created_at FROM reviews WHERE id = ?", Boolean.class, id);
        assertThat(edited).isTrue();
    }

    @Test
    void otherCustomersReviewIsNotFound() throws Exception {
        completed(buyer.getId(), productId);
        long id = createdId(reviewProduct(asBuyer, productId, 5, "ok"));
        MockMvc stranger = mvc(user("review-stranger@campvivo.vn", Role.CUSTOMER));
        stranger.perform(put("/api/reviews/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"rating\": 1}"))
                .andExpect(status().isNotFound());
        stranger.perform(delete("/api/reviews/" + id)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ kiểm duyệt (D-33)

    @Test
    void offensiveContentIsFlaggedAndRecheckedOnEdit() throws Exception {
        completed(buyer.getId(), productId);
        long id = createdId(reviewProduct(asBuyer, productId, 1, "Đ.m hàng như c*t"));
        assertThat(
                jdbc.queryForObject("SELECT moderation_status FROM reviews WHERE id = ?", String.class, id))
                .isEqualTo("SENSITIVE");
        anonymous().perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(jsonPath("$.data[0].isSensitive").value(true));

        asBuyer.perform(put("/api/reviews/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 2, \"content\": \"Hàng tệ, giao chậm\"}"))
                .andExpect(jsonPath("$.data.isSensitive").value(false));

        // Nhân viên đã đánh dấu an toàn, khách sửa lại có từ cấm → bị làm mờ lại
        jdbc.update("UPDATE reviews SET moderation_status = 'CLEARED' WHERE id = ?", id);
        asBuyer.perform(put("/api/reviews/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 1, \"content\": \"vcl\"}"))
                .andExpect(jsonPath("$.data.isSensitive").value(true));
    }

    // ------------------------------------------------------------------ danh sách / tổng quan (D-32)

    @Test
    void listIsNewestFirstFilterableAndPaged() throws Exception {
        int[] ratings = {5, 4, 5};
        for (int i = 0; i < ratings.length; i++) {
            User u = user("review-list-" + i + "@campvivo.vn", Role.CUSTOMER);
            completed(u.getId(), productId);
            reviewProduct(mvc(u), productId, ratings[i], "đánh giá " + i).andExpect(status().isCreated());
        }
        MockMvc guest = anonymous();
        guest.perform(get("/api/products/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].content").value("đánh giá 2"))
                .andExpect(jsonPath("$.data[2].content").value("đánh giá 0"))
                .andExpect(jsonPath("$.pagination.limit").value(10))
                .andExpect(jsonPath("$.pagination.totalItems").value(3));
        guest.perform(get("/api/products/" + productId + "/reviews?rating=5"))
                .andExpect(jsonPath("$.data.length()").value(2));
        guest.perform(get("/api/products/" + productId + "/reviews?page=2&limit=2"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pagination.totalPages").value(2));
        guest.perform(get("/api/products/" + productId + "/reviews?rating=6")).andExpect(status().isBadRequest());
        guest.perform(get("/api/products/" + productId + "/reviews/summary"))
                .andExpect(jsonPath("$.data.averageRating").value(4.7))
                .andExpect(jsonPath("$.data.reviewCount").value(3))
                .andExpect(jsonPath("$.data.ratingCounts.5").value(2))
                .andExpect(jsonPath("$.data.ratingCounts.4").value(1))
                .andExpect(jsonPath("$.data.ratingCounts.1").value(0));
        guest.perform(get("/api/products/" + otherProductId + "/reviews/summary"))
                .andExpect(jsonPath("$.data.averageRating").value(nullValue()))
                .andExpect(jsonPath("$.data.reviewCount").value(0));
    }

    // ------------------------------------------------------------------ đánh giá bị ẩn (D-31)

    @Test
    void hiddenReviewIsExcludedPubliclyButOwnerSeesItAndCanOnlyDelete() throws Exception {
        completed(buyer.getId(), productId);
        long id = createdId(reviewProduct(asBuyer, productId, 1, "spam"));
        jdbc.update("UPDATE reviews SET is_hidden = true, hidden_reason = 'Spam', hidden_at = now() WHERE id = ?", id);

        MockMvc guest = anonymous();
        guest.perform(get("/api/products/" + productId + "/reviews")).andExpect(jsonPath("$.data.length()").value(0));
        guest.perform(get("/api/products/" + productId + "/reviews/summary"))
                .andExpect(jsonPath("$.data.reviewCount").value(0));
        asBuyer.perform(get("/api/reviews/mine?productId=" + productId))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.isHidden").value(true))
                .andExpect(jsonPath("$.data.hiddenReason").value("Spam"));
        asBuyer.perform(put("/api/reviews/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"rating\": 5}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("REVIEW_HIDDEN"));
        asBuyer.perform(delete("/api/reviews/" + id)).andExpect(status().isOk());
        reviewProduct(asBuyer, productId, 5, "viết lại").andExpect(status().isCreated());
    }

    @Test
    void mineIsNullWhenNotWrittenAndNeedsExactlyOneItem() throws Exception {
        asBuyer.perform(get("/api/reviews/mine?productId=" + productId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(nullValue()));
        asBuyer.perform(get("/api/reviews/mine")).andExpect(status().isBadRequest());
        asBuyer.perform(get("/api/reviews/mine?productId=" + productId + "&comboId=" + comboId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eligibilityFollowsPurchaseAndExistingReview() throws Exception {
        String url = "/api/reviews/eligibility?productId=" + productId;
        asBuyer.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.data.canReview").value(false));
        order(buyer.getId(), "SHIPPING", productId, null);
        asBuyer.perform(get(url)).andExpect(jsonPath("$.data.canReview").value(false));
        completed(buyer.getId(), productId);
        asBuyer.perform(get(url)).andExpect(jsonPath("$.data.canReview").value(true));
        long id = createdId(reviewProduct(asBuyer, productId, 5, "ok"));
        asBuyer.perform(get(url)).andExpect(jsonPath("$.data.canReview").value(false));
        asBuyer.perform(delete("/api/reviews/" + id)).andExpect(status().isOk());
        asBuyer.perform(get(url)).andExpect(jsonPath("$.data.canReview").value(true));
        asBuyer.perform(get("/api/reviews/eligibility?comboId=" + comboId)).andExpect(jsonPath("$.data.canReview").value(false));
        asBuyer.perform(get("/api/reviews/eligibility")).andExpect(status().isBadRequest());
        anonymous().perform(get(url)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ tích hợp catalog / combo / giỏ / đơn (D-35)

    @Test
    void ratingsAppearEverywhereAndExcludeHiddenReviews() throws Exception {
        User second = user("review-second@campvivo.vn", Role.CUSTOMER);
        User third = user("review-third@campvivo.vn", Role.CUSTOMER);
        completed(buyer.getId(), productId);
        completed(second.getId(), productId);
        completed(third.getId(), productId);
        order(buyer.getId(), "COMPLETED", null, comboId);
        reviewProduct(asBuyer, productId, 5, "tốt").andExpect(status().isCreated());
        reviewProduct(mvc(second), productId, 4, "ổn").andExpect(status().isCreated());
        long hiddenId = createdId(reviewProduct(mvc(third), productId, 1, "spam"));
        jdbc.update("UPDATE reviews SET is_hidden = true, hidden_reason = 'Spam', hidden_at = now() WHERE id = ?", hiddenId);
        postReview(asBuyer, "{\"comboId\": " + comboId + ", \"rating\": 3}").andExpect(status().isCreated());

        MockMvc guest = anonymous();
        guest.perform(get("/api/products/leu-2-nguoi"))
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.reviewCount").value(2));
        guest.perform(get("/api/products?slugs=leu-2-nguoi,tui-ngu&sortBy=name_asc"))
                .andExpect(jsonPath("$.data[?(@.slug == 'leu-2-nguoi')].averageRating").value(4.5))
                .andExpect(jsonPath("$.data[?(@.slug == 'tui-ngu')].reviewCount").value(0))
                .andExpect(jsonPath("$.data[?(@.slug == 'tui-ngu')].averageRating").value(hasItem(nullValue())));
        guest.perform(get("/api/combos/combo-cam-trai"))
                .andExpect(jsonPath("$.data.averageRating").value(3.0))
                .andExpect(jsonPath("$.data.reviewCount").value(1));
        guest.perform(get("/api/combos"))
                .andExpect(jsonPath("$.data[0].reviewCount").value(1));

        asBuyer.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": " + productId + ", \"quantity\": 1}"))
                .andExpect(status().isOk());
        asBuyer.perform(get("/api/cart"))
                .andExpect(jsonPath("$.data.items[0].item.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.items[0].item.reviewCount").value(2));
    }

    @Test
    void orderDetailShowsMyReviewPerLine() throws Exception {
        long orderId = order(buyer.getId(), "COMPLETED", productId, null);
        line(orderId, otherProductId, null);
        line(orderId, null, comboId);
        long reviewId = createdId(reviewProduct(asBuyer, productId, 5, "tốt"));

        asBuyer.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].itemId").value(productId))
                .andExpect(jsonPath("$.data.items[0].myReviewId").value(reviewId))
                .andExpect(jsonPath("$.data.items[0].canReview").value(false))
                .andExpect(jsonPath("$.data.items[1].myReviewId").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].canReview").value(true))
                .andExpect(jsonPath("$.data.items[2].type").value("COMBO"))
                .andExpect(jsonPath("$.data.items[2].canReview").value(true));

        // Đơn chưa hoàn tất: không viết được, nhưng vẫn biết đã đánh giá (từ đơn khác)
        long pending = order(buyer.getId(), "SHIPPING", productId, null);
        line(pending, otherProductId, null);
        asBuyer.perform(get("/api/orders/" + pending))
                .andExpect(jsonPath("$.data.items[0].myReviewId").value(reviewId))
                .andExpect(jsonPath("$.data.items[0].canReview").value(false))
                .andExpect(jsonPath("$.data.items[1].canReview").value(false));
    }

    // ------------------------------------------------------------------ phân quyền

    @Test
    void publicReadsNeedNoTokenButWritesNeedCustomer() throws Exception {
        MockMvc guest = anonymous();
        guest.perform(get("/api/products/" + productId + "/reviews")).andExpect(status().isOk());
        guest.perform(get("/api/combos/" + comboId + "/reviews/summary")).andExpect(status().isOk());
        reviewProduct(guest, productId, 5, "ok").andExpect(status().isUnauthorized());
        reviewProduct(mvc(user("review-employee@campvivo.vn", Role.EMPLOYEE)), productId, 5, "ok")
                .andExpect(status().isForbidden());
    }
}
