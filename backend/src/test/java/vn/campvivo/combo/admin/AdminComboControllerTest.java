package vn.campvivo.combo.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.support.AdminMockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.UserRepository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration test quản lý combo (FR-15, D-03) và đóng gói/tháo combo (D-22). */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long"
})
class AdminComboControllerTest {

    private static final String BASE = "/api/admin/combos";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    /** Đi qua security thật (JWT filter + @PreAuthorize) với token ADMIN. */
    private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private long campingId;
    private long tentId;
    private long hikingId;
    private long tent;
    private long stove;
    private long lamp;
    /** Áo còn 10 cái. */
    private long shirt;

    @BeforeEach
    void setUp() {
        mvc = AdminMockMvc.admin(context, users, jwt);
        jdbc.update("DELETE FROM inventory_logs");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        campingId = insertCategory(null, "Cắm trại", "cam-trai");
        tentId = insertCategory(campingId, "Lều trại", "leu-trai");
        hikingId = insertCategory(null, "Leo núi", "leo-nui");
        tent = insertProduct("Lều 2 người", "leu-2-nguoi", 1_200_000);
        stove = insertProduct("Bếp gas mini", "bep-gas-mini", 300_000);
        lamp = insertProduct("Đèn lều", "den-leu", 150_000);
        setStock(tent, 5);
        setStock(stove, 20);
        setStock(lamp, 3);
        shirt = insertProduct("Áo gió", "ao-gio", 400_000);
        setStock(shirt, 10);
        jdbc.update("INSERT INTO images (product_id, image_url, is_primary, display_order) VALUES (?, 'https://img.example/leu.jpg', true, 0)", tent);
    }

    @Test
    void createReturnsItemsWithProductInfoTotalsAndZeroStock() throws Exception {
        Map<String, Object> body = combo("Combo cắm trại 2 người", 1_500_000, item(tent, 1), item(stove, 2));
        body.put("description", "<p>Đủ đồ</p><script>x()</script>");

        JsonNode data = data(post(body).andExpect(status().isCreated()));

        assertThat(data.get("slug").asText()).isEqualTo("combo-cam-trai-2-nguoi");
        assertThat(data.get("description").asText()).contains("Đủ đồ").doesNotContain("script");
        assertThat(data.get("itemsTotal").asLong()).isEqualTo(1_800_000);
        assertThat(data.get("stockQuantity").asInt()).isZero();
        assertThat(data.get("items")).hasSize(2);
        assertThat(data.get("items").get(0).get("productName").asText()).isEqualTo("Lều 2 người");
        assertThat(data.get("items").get(0).get("productImageUrl").asText()).isEqualTo("https://img.example/leu.jpg");
        assertThat(data.get("items").get(1).get("quantity").asInt()).isEqualTo(2);
        assertThat(data.get("primaryImageUrl").asText()).isEqualTo("https://img.example/leu.jpg");
    }

    @Test
    void itemsNeedTwoDistinctExistingProducts() throws Exception {
        post(combo("Combo lẻ", 100_000, item(tent, 1))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.items").exists());
        post(combo("Combo trùng", 100_000, item(tent, 1), item(tent, 2))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.items").exists());
        post(combo("Combo ma", 100_000, item(tent, 1), item(999_999L, 1))).andExpect(status().isNotFound());
        post(combo("Combo số lượng", 100_000, item(tent, 0), item(stove, 1))).andExpect(status().isBadRequest());
    }

    @Test
    void updateReplacesItemsKeepingSlugWhenBlank() throws Exception {
        long id = data(post(combo("Combo A", 1_000_000, item(tent, 1), item(stove, 1)))).get("id").asLong();

        Map<String, Object> body = combo("Combo A mới", 900_000, item(stove, 1), item(lamp, 3), item(tent, 1));
        JsonNode data = data(mvc.perform(MockMvcRequestBuilders.put(BASE + "/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().isOk()));

        assertThat(data.get("slug").asText()).isEqualTo("combo-a");
        assertThat(data.get("name").asText()).isEqualTo("Combo A mới");
        assertThat(data.get("items")).hasSize(3);
        assertThat(data.get("items").get(0).get("productSlug").asText()).isEqualTo("bep-gas-mini");
        assertThat(data.get("itemsTotal").asLong()).isEqualTo(300_000 + 450_000 + 1_200_000);
        // Ảnh combo theo sản phẩm thành phần: bếp, đèn chưa có ảnh → lấy ảnh lều (dòng thứ 3)
        assertThat(data.get("primaryImageUrl").asText()).isEqualTo("https://img.example/leu.jpg");
    }

    @Test
    void listFiltersByKeywordCategoryAndStatusAndStatusPatchHides() throws Exception {
        long a = data(post(combo("Combo cắm trại gia đình", 2_000_000, item(tent, 1), item(stove, 1)))).get("id").asLong();
        Map<String, Object> other = combo("Combo đèn", 200_000, item(lamp, 1), item(stove, 1));
        other.put("categoryId", hikingId);
        post(other).andExpect(status().isCreated());

        assertThat(slugs(list("?q=gia%20dinh"))).containsExactly("combo-cam-trai-gia-dinh");
        assertThat(slugs(list("?sort=NAME_ASC"))).containsExactly("combo-cam-trai-gia-dinh", "combo-den");
        assertThat(slugs(list("?categoryId=" + campingId))).containsExactly("combo-cam-trai-gia-dinh");
        assertThat(slugs(list("?categoryId=" + hikingId))).containsExactly("combo-den");

        mvc.perform(patch(BASE + "/" + a + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isActive").value(false));
        assertThat(slugs(list("?isActive=true"))).containsExactly("combo-den");
        JsonNode first = list("?isActive=false").get("data").get(0);
        assertThat(first.get("itemCount").asInt()).isEqualTo(2);
        assertThat(first.get("categoryPath").get(0).asText()).isEqualTo("Cắm trại");
    }

    @Test
    void duplicateSlugIsRejected() throws Exception {
        post(combo("Combo X", 100_000, item(tent, 1), item(stove, 1))).andExpect(status().isCreated());
        Map<String, Object> dup = combo("Combo Y", 100_000, item(tent, 1), item(stove, 1));
        dup.put("slug", "combo-x");
        post(dup).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("SLUG_ALREADY_EXISTS"));
    }

    @Test
    void ownImagesComeFirstAndProductImagesCannotBeReused() throws Exception {
        // Ảnh tự tải lên: ảnh chính là ảnh đại diện (thay cho ảnh sản phẩm thành phần)
        Map<String, Object> body = combo("Combo có ảnh", 1_000_000, item(tent, 1), item(stove, 1));
        body.put("images", List.of(image("https://img.example/combo-phu.jpg", false, 0), image("https://img.example/combo-chinh.jpg", true, 1)));
        JsonNode data = data(post(body).andExpect(status().isCreated()));
        assertThat(data.get("primaryImageUrl").asText()).isEqualTo("https://img.example/combo-chinh.jpg");
        assertThat(data.get("images")).hasSize(2);
        assertThat(data.get("items").get(0).get("productImageUrl").asText()).isEqualTo("https://img.example/leu.jpg");

        // Bỏ hết ảnh tự tải lên → ảnh đại diện quay về ảnh sản phẩm thành phần
        long id = data.get("id").asLong();
        body.put("images", List.of());
        JsonNode cleared = data(mvc.perform(MockMvcRequestBuilders.put(BASE + "/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().isOk()));
        assertThat(cleared.get("images")).isEmpty();
        assertThat(cleared.get("primaryImageUrl").asText()).isEqualTo("https://img.example/leu.jpg");

        // Ảnh của sản phẩm (URL unique toàn bảng) và thiếu/thừa ảnh chính → 400
        Map<String, Object> reuse = combo("Combo dùng lại ảnh", 1_000_000, item(tent, 1), item(stove, 1));
        reuse.put("images", List.of(image("https://img.example/leu.jpg", true, 0)));
        post(reuse).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.images").exists());
        reuse.put("images", List.of(image("https://img.example/a.jpg", false, 0)));
        post(reuse).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.images").exists());
    }

    @Test
    void categoryWithComboCannotBeDeleted() throws Exception {
        post(combo("Combo khoá danh mục", 100_000, item(tent, 1), item(stove, 1))).andExpect(status().isCreated());
        // "Cắm trại" không còn danh mục con, sản phẩm nào — chỉ còn combo
        jdbc.update("UPDATE products SET category_id = ?", hikingId);
        mvc.perform(MockMvcRequestBuilders.delete("/api/admin/categories/" + tentId)).andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.delete("/api/admin/categories/" + campingId)).andExpect(status().isConflict());
        mvc.perform(get("/api/admin/categories")).andExpect(jsonPath("$.data[0].comboCount").value(1));
    }

    @Test
    void comboCategoryMustBeTopLevel() throws Exception {
        Map<String, Object> body = combo("Combo lều", 500_000, item(tent, 1), item(stove, 1));
        body.put("categoryId", tentId);
        post(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.categoryId").value("Combo chỉ gắn với danh mục cấp 1."));
    }

    @Test
    void itemShowsProductStockAndMaxPackable() throws Exception {
        JsonNode data = data(post(combo("Combo áo", 500_000, item(tent, 1), item(shirt, 2))).andExpect(status().isCreated()));
        assertThat(data.get("items").get(1).get("productAvailableQuantity").asInt()).isEqualTo(10);
        // Lều còn 5 (1/bộ), áo còn 10 (2/bộ) → đóng gói tối đa 5 bộ
        assertThat(data.get("maxPackable").asInt()).isEqualTo(5);
    }

    @Test
    void packTakesStockFromEachProductAndUnpackReturnsIt() throws Exception {
        long id = data(post(combo("Combo đóng gói", 1_500_000, item(tent, 1), item(shirt, 2)))).get("id").asLong();

        JsonNode packed = data(pack(id, "pack", 3).andExpect(status().isOk()));
        assertThat(packed.get("stockQuantity").asInt()).isEqualTo(3);
        assertThat(stock(tent)).isEqualTo(2);
        assertThat(stock(shirt)).isEqualTo(4);

        pack(id, "pack", 3).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("OUT_OF_STOCK"));
        assertThat(stock(tent)).isEqualTo(2);

        JsonNode unpacked = data(pack(id, "unpack", 2).andExpect(status().isOk()));
        assertThat(unpacked.get("stockQuantity").asInt()).isEqualTo(1);
        assertThat(stock(tent)).isEqualTo(4);
        assertThat(stock(shirt)).isEqualTo(8);

        jdbc.update("UPDATE product_combos SET reserved_quantity = 1 WHERE id = ?", id);
        pack(id, "unpack", 1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_STOCK_ADJUSTMENT"));
        pack(id, "pack", 0).andExpect(status().isBadRequest());
    }

    @Test
    void itemsCannotChangeWhilePackedSetsRemain() throws Exception {
        long id = data(post(combo("Combo khoá", 1_000_000, item(tent, 1), item(stove, 1)))).get("id").asLong();
        pack(id, "pack", 1).andExpect(status().isOk());

        Map<String, Object> sameItems = combo("Combo khoá (đổi tên)", 900_000, item(stove, 1), item(tent, 1));
        mvc.perform(MockMvcRequestBuilders.put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(sameItems))).andExpect(status().isOk());

        Map<String, Object> changed = combo("Combo khoá", 900_000, item(tent, 2), item(stove, 1));
        mvc.perform(MockMvcRequestBuilders.put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(changed)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.items").exists());
    }

    // ---------------------------------------------------------------------------------------------

    private Map<String, Object> combo(String name, long price, Map<?, ?>... items) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("categoryId", campingId);
        m.put("comboPrice", price);
        m.put("isActive", true);
        m.put("items", List.of(items));
        return m;
    }

    private static Map<String, Object> image(String url, boolean primary, int order) {
        return Map.of("url", url, "isPrimary", primary, "displayOrder", order);
    }

    private static Map<String, Object> item(long productId, int quantity) {
        return Map.of("productId", productId, "quantity", quantity);
    }

    private void setStock(long productId, int stock) {
        jdbc.update("UPDATE products SET stock_quantity = ? WHERE id = ?", stock, productId);
    }

    private int stock(long productId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM products WHERE id = ?", Integer.class, productId);
    }

    private ResultActions pack(long id, String action, int sets) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(BASE + "/" + id + "/" + action).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sets\":" + sets + "}"));
    }

    private long insertCategory(Long parentId, String name, String slug) {
        Long id = jdbc.queryForObject("INSERT INTO categories (parent_id, name, slug, path) VALUES (?, ?, ?, '') RETURNING id",
                Long.class, parentId, name, slug);
        String parentPath = parentId == null ? "/" : jdbc.queryForObject("SELECT path FROM categories WHERE id = ?", String.class, parentId);
        jdbc.update("UPDATE categories SET path = ? WHERE id = ?", parentPath + id + "/", id);
        return id;
    }

    private long insertProduct(String name, String slug, long price) {
        return jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, tentId, name, slug, price);
    }

    private ResultActions post(Map<String, Object> body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private JsonNode list(String query) throws Exception {
        return mapper.readTree(mvc.perform(get(URI.create(BASE + query))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> slugs(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("data").forEach(n -> out.add(n.get("slug").asText()));
        return out;
    }

    private JsonNode data(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }
}
