package vn.campvivo.favorite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.campvivo.auth.JwtService;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Bảng `favorites` (V18, database.md 3.18) và API yêu thích (api.md 4.2 / 4.6, FR-20, D-47) qua chuỗi security thật và PostgreSQL
 * thật. Mỗi test tự tạo khách + mặt hàng (slug ngẫu nhiên) và xoá ở cuối; xoá khách thì yêu thích xoá theo (CASCADE).
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class FavoriteIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;
    @Autowired FavoriteService favoriteService;

    private final List<Long> userIds = new ArrayList<>();
    private String tag;
    private long category;
    private long author;

    private record Account(long id, String token) {}

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 8);
        category = jdbc.queryForObject("INSERT INTO categories (name, slug, path) VALUES ('Lều', ?, '/') RETURNING id",
                Long.class, "fv-cat-" + tag);
        author = account(Role.ADMIN).id();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM articles WHERE slug LIKE ?", "fv-%-" + tag);
        jdbc.update("DELETE FROM product_combos WHERE slug LIKE ?", "fv-%-" + tag);
        jdbc.update("DELETE FROM products WHERE slug LIKE ?", "fv-%-" + tag);
        jdbc.update("DELETE FROM categories WHERE id = ?", category);
        userIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
        userIds.clear();
    }

    // ------------------------------------------------------------------ dữ liệu

    private Account account(Role role) {
        User u = new User("fv-" + UUID.randomUUID() + "@test.vn", "hash", "Người dùng");
        u.setRole(role);
        u.setEmailVerified(true);
        u = users.saveAndFlush(u);
        userIds.add(u.getId());
        return new Account(u.getId(), jwtService.createToken(u));
    }

    private long product(String name, boolean active) {
        return jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price, stock_quantity, is_active) "
                        + "VALUES (?, ?, ?, 500000, 3, ?) RETURNING id", Long.class, category, name,
                "fv-p" + UUID.randomUUID().toString().substring(0, 6) + "-" + tag, active);
    }

    private long combo(String name, boolean active) {
        return jdbc.queryForObject("INSERT INTO product_combos (category_id, name, slug, combo_price, is_active) "
                        + "VALUES (?, ?, ?, 900000, ?) RETURNING id", Long.class, category, name,
                "fv-c" + UUID.randomUUID().toString().substring(0, 6) + "-" + tag, active);
    }

    private long article(String title, boolean published) {
        return jdbc.queryForObject("INSERT INTO articles (user_id, title, slug, content, category, is_published, published_at) "
                        + "VALUES (?, ?, ?, 'Nội dung', 'BLOG', ?, CASE WHEN ? THEN now() END) RETURNING id", Long.class,
                author, title, "fv-a" + UUID.randomUUID().toString().substring(0, 6) + "-" + tag, published, published);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Account as, int expectedStatus) throws Exception {
        if (as != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + as.token());
        }
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        String body = response.getContentAsString();
        assertThat(response.getStatus()).as(body).isEqualTo(expectedStatus);
        return body.isEmpty() ? objectMapper.nullNode() : objectMapper.readTree(body);
    }

    private JsonNode add(Account as, String field, long id, int expectedStatus) throws Exception {
        return call(post("/api/favorites").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(field, id))), as, expectedStatus);
    }

    private static List<String> names(JsonNode list) {
        return StreamSupport.stream(list.path("data").spliterator(), false)
                .map(n -> n.path("item").has("title") ? n.path("item").path("title").asText()
                        : n.path("item").path("name").asText())
                .toList();
    }

    // ------------------------------------------------------------------ schema

    @Test
    void schemaRequiresExactlyOneItemAndOneRowPerItem() {
        long me = account(Role.CUSTOMER).id();
        long p = product("Lều A", true);
        long c = combo("Combo A", true);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO favorites (user_id) VALUES (?)", me))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_favorites_one_item");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO favorites (user_id, product_id, combo_id) VALUES (?, ?, ?)", me, p, c))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("chk_favorites_one_item");

        jdbc.update("INSERT INTO favorites (user_id, product_id) VALUES (?, ?)", me, p);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO favorites (user_id, product_id) VALUES (?, ?)", me, p))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uq_favorites_user_product");
        jdbc.update("INSERT INTO favorites (user_id, combo_id) VALUES (?, ?)", me, c);   // khác loại → được

        jdbc.update("DELETE FROM products WHERE id = ?", p);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM favorites WHERE user_id = ?", Integer.class, me))
                .as("xoá sản phẩm → yêu thích xoá theo").isEqualTo(1);
    }

    // ------------------------------------------------------------------ thêm

    @Test
    void addIsIdempotentAndOnlyForVisibleItems() throws Exception {
        Account me = account(Role.CUSTOMER);
        long p = product("Lều A", true);

        long id = add(me, "productId", p, 201).path("data").path("id").asLong();
        assertThat(add(me, "productId", p, 200).path("data").path("id").asLong()).isEqualTo(id);

        add(me, "productId", product("Lều tắt", false), 404);
        add(me, "comboId", combo("Combo tắt", false), 404);
        add(me, "articleId", article("Bài nháp", false), 404);
        add(me, "productId", 987654321L, 404);
        add(me, "articleId", article("Bài đã đăng", true), 201);

        JsonNode none = call(post("/api/favorites").contentType(MediaType.APPLICATION_JSON).content("{}"), me, 400);
        assertThat(none.path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
        call(post("/api/favorites").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("productId", p, "comboId", 1))), me, 400);
    }

    @Test
    void concurrentAddsCreateOneRowWithTheSameId() throws Exception {
        long me = account(Role.CUSTOMER).id();
        long p = product("Lều A", true);
        CyclicBarrier start = new CyclicBarrier(4);
        List<CompletableFuture<FavoriteService.AddResult>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            calls.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return favoriteService.add(me, new vn.campvivo.favorite.dto.FavoriteRequest(p, null, null));
            }));
        }
        List<FavoriteService.AddResult> results = calls.stream().map(CompletableFuture::join).toList();

        assertThat(results).extracting(FavoriteService.AddResult::id).containsOnly(results.get(0).id());
        assertThat(results).filteredOn(FavoriteService.AddResult::created).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM favorites WHERE user_id = ?", Integer.class, me)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ danh sách

    @Test
    void listIsNewestFirstFilteredByTypeAndHidesInvisibleItems() throws Exception {
        Account me = account(Role.CUSTOMER);
        Account other = account(Role.CUSTOMER);
        long p1 = product("Lều 1", true);
        long p2 = product("Lều 2", true);
        long c = combo("Combo 1", true);
        long a = article("Bài 1", true);
        add(me, "productId", p1, 201);
        add(me, "comboId", c, 201);
        add(me, "articleId", a, 201);
        add(me, "productId", p2, 201);
        add(other, "productId", p1, 201);
        // thứ tự created_at rõ ràng (các lần thêm có thể cùng mili giây)
        jdbc.update("UPDATE favorites f SET created_at = now() - (v.n || ' minutes')::interval FROM (VALUES "
                + "(?::bigint, 4), (?::bigint, 3), (?::bigint, 2), (?::bigint, 1)) v(item, n) "
                + "WHERE f.user_id = ? AND coalesce(f.product_id, f.combo_id, f.article_id) = v.item", p1, c, a, p2, me.id());

        JsonNode all = call(get("/api/users/me/favorites"), me, 200);
        assertThat(names(all)).containsExactly("Lều 2", "Bài 1", "Combo 1", "Lều 1");
        assertThat(all.path("pagination").path("totalItems").asLong()).isEqualTo(4);
        JsonNode first = all.path("data").get(0);
        assertThat(first.path("type").asText()).isEqualTo("PRODUCT");
        assertThat(first.path("item").path("type").asText()).isEqualTo("PRODUCT");
        assertThat(first.path("item").path("price").asLong()).isEqualTo(500000);
        assertThat(first.path("item").path("availableQuantity").asInt()).isEqualTo(3);
        assertThat(first.has("createdAt")).isTrue();
        JsonNode comboRow = all.path("data").get(2);
        assertThat(comboRow.path("item").path("type").asText()).isEqualTo("COMBO");
        assertThat(comboRow.path("item").has("itemsTotal")).isTrue();
        assertThat(all.path("data").get(1).path("item").path("category").asText()).isEqualTo("BLOG");

        assertThat(names(call(get("/api/users/me/favorites?type=PRODUCT"), me, 200))).containsExactly("Lều 2", "Lều 1");
        JsonNode page2 = call(get("/api/users/me/favorites?limit=3&page=2"), me, 200);
        assertThat(names(page2)).containsExactly("Lều 1");
        assertThat(page2.path("pagination").path("totalPages").asInt()).isEqualTo(2);

        // Ẩn mặt hàng → biến khỏi danh sách nhưng không mất dòng; bật lại → hiện lại
        jdbc.update("UPDATE products SET is_active = false WHERE id = ?", p2);
        jdbc.update("UPDATE articles SET is_published = false WHERE id = ?", a);
        JsonNode hidden = call(get("/api/users/me/favorites"), me, 200);
        assertThat(names(hidden)).containsExactly("Combo 1", "Lều 1");
        assertThat(hidden.path("pagination").path("totalItems").asLong()).isEqualTo(2);
        JsonNode ids = call(get("/api/users/me/favorites/ids"), me, 200).path("data");
        assertThat(ids.path("productIds")).extracting(JsonNode::asLong).containsExactly(Math.min(p1, p2), Math.max(p1, p2));
        assertThat(ids.path("comboIds")).extracting(JsonNode::asLong).containsExactly(c);
        assertThat(ids.path("articleIds")).extracting(JsonNode::asLong).containsExactly(a);
        jdbc.update("UPDATE products SET is_active = true WHERE id = ?", p2);
        assertThat(names(call(get("/api/users/me/favorites"), me, 200))).containsExactly("Lều 2", "Combo 1", "Lều 1");

        call(get("/api/users/me/favorites?type=SHOP"), me, 400);
        call(get("/api/users/me/favorites?limit=101"), me, 400);
    }

    // ------------------------------------------------------------------ bỏ

    @Test
    void removeByIdChecksOwnerAndRemoveByItemIsIdempotent() throws Exception {
        Account me = account(Role.CUSTOMER);
        Account other = account(Role.CUSTOMER);
        long p = product("Lều A", true);
        long c = combo("Combo A", true);
        long mine = add(me, "productId", p, 201).path("data").path("id").asLong();
        add(me, "comboId", c, 201);

        call(delete("/api/favorites/" + mine), other, 404);
        call(delete("/api/favorites/" + mine), me, 200);
        call(delete("/api/favorites/" + mine), me, 404);

        jdbc.update("UPDATE product_combos SET is_active = false WHERE id = ?", c);   // bỏ được cả khi đang ẩn
        call(delete("/api/favorites?comboId=" + c), me, 200);
        call(delete("/api/favorites?comboId=" + c), me, 200);
        call(delete("/api/favorites"), me, 400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM favorites WHERE user_id = ?", Integer.class, me.id())).isZero();
    }

    @Test
    void onlyCustomersCanUseFavorites() throws Exception {
        Account staff = account(Role.EMPLOYEE);
        long p = product("Lều A", true);

        add(staff, "productId", p, 403);
        call(get("/api/users/me/favorites"), staff, 403);
        call(get("/api/users/me/favorites/ids"), staff, 403);
        call(get("/api/users/me/favorites/ids"), null, 401);
        call(delete("/api/favorites?productId=" + p), null, 401);
    }

    // ------------------------------------------------------------------ người nhận thông báo

    @Test
    void customersFavoritingSkipsInactiveAccountsAndGroupsByItem() {
        long a = account(Role.CUSTOMER).id();
        long b = account(Role.CUSTOMER).id();
        long locked = account(Role.CUSTOMER).id();
        long p1 = product("Lều 1", true);
        long p2 = product("Lều 2", true);
        long p3 = product("Lều 3", true);
        for (long[] row : new long[][]{{a, p1}, {b, p1}, {locked, p1}, {b, p2}}) {
            jdbc.update("INSERT INTO favorites (user_id, product_id) VALUES (?, ?)", row[0], row[1]);
        }
        jdbc.update("UPDATE users SET is_active = false WHERE id = ?", locked);

        Map<Long, List<Long>> result = favoriteService.customersFavoriting(FavoriteType.PRODUCT, List.of(p1, p2, p3));

        assertThat(result).containsOnlyKeys(p1, p2);
        assertThat(result.get(p1)).containsExactly(a, b);
        assertThat(result.get(p2)).containsExactly(b);
        assertThat(favoriteService.customersFavoriting(FavoriteType.COMBO, List.of(p1))).isEmpty();
    }
}
