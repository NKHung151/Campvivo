package vn.campvivo.catalog.admin;

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
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.support.AdminMockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.UserRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test API quản lý sản phẩm (FR-06) trên PostgreSQL thật (Testcontainers), chạy cả
 * migration db/migration (không có seed dev). Mỗi test tự tạo cây danh mục nhỏ:
 * "Cắm trại" (lều trại, bàn ghế) và "Trang phục" (áo khoác).
 * <p>
 * ddl-auto=none: entity cart/order (code khung của module khác) chưa có bảng (KI-04).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long"
})
class AdminProductControllerTest {

    private static final String BASE = "/api/admin/products";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    /** Đi qua security thật (JWT filter + @PreAuthorize) với token ADMIN. */
    private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private long campingId;
    private long tentId;
    private long apparelId;

    @BeforeEach
    void setUp() {
        mvc = AdminMockMvc.admin(context, users, jwt);
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        campingId = insertCategory(null, "Cắm trại", "cam-trai");
        tentId = insertCategory(campingId, "Lều trại", "leu-trai");
        insertCategory(campingId, "Bàn ghế", "ban-ghe");
        long apparel = insertCategory(null, "Trang phục", "trang-phuc");
        apparelId = insertCategory(apparel, "Áo khoác", "ao-khoac");
    }

    // --- Tạo -----------------------------------------------------------------------------------

    @Test
    void onlyAdminCanUseCatalogAdminApi() throws Exception {
        MockMvc anonymous = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();
        anonymous.perform(get(BASE)).andExpect(status().isUnauthorized());
        AdminMockMvc.as(vn.campvivo.user.Role.EMPLOYEE, context, users, jwt).perform(get(BASE)).andExpect(status().isForbidden());
        mvc.perform(get(BASE)).andExpect(status().isOk());
    }

    @Test
    void createGeneratesSlugSanitizesDescriptionAndStartsWithZeroStock() throws Exception {
        Map<String, Object> body = product("Lều cắm trại 2 người", tentId);
        body.put("description", "<p>Chống nước</p><script>alert(1)</script>");
        body.put("brand", "  Naturehike  ");

        JsonNode data = data(post(body).andExpect(status().isCreated()));

        assertThat(data.get("slug").asText()).isEqualTo("leu-cam-trai-2-nguoi");
        assertThat(data.get("description").asText()).contains("Chống nước").doesNotContain("script");
        assertThat(data.get("brand").asText()).isEqualTo("Naturehike");
        assertThat(data.get("stockQuantity").asInt()).isZero();
        assertThat(data.get("isActive").asBoolean()).isTrue();
        assertThat(data.get("categoryName").asText()).isEqualTo("Lều trại");
        assertThat(data.get("primaryImageUrl").asText()).isEqualTo("https://img.example/leu-cam-trai-2-nguoi-1.jpg");
        assertThat(data.get("images")).hasSize(2);
    }

    @Test
    void autoSlugGetsSuffixWhenTaken() throws Exception {
        post(product("Bếp gas mini", tentId)).andExpect(status().isCreated());
        Map<String, Object> second = product("Bếp gas mini", tentId);
        second.put("images", List.of());

        JsonNode data = data(post(second).andExpect(status().isCreated()));

        assertThat(data.get("slug").asText()).isEqualTo("bep-gas-mini-2");
    }

    @Test
    void explicitDuplicateSlugIsRejected() throws Exception {
        post(product("Túi ngủ", tentId)).andExpect(status().isCreated());
        Map<String, Object> other = product("Túi ngủ mùa đông", tentId);
        other.put("slug", "tui-ngu");
        other.put("images", List.of());

        post(other).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SLUG_ALREADY_EXISTS"));
    }

    @Test
    void invalidFieldsReturnFieldErrors() throws Exception {
        Map<String, Object> body = product(" ", tentId);
        body.put("price", 0);
        body.put("slug", "Sai Slug");

        post(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fieldErrors.name").exists())
                .andExpect(jsonPath("$.details.fieldErrors.price").exists())
                .andExpect(jsonPath("$.details.fieldErrors.slug").exists());
    }

    @Test
    void unknownCategoryIsAFieldError() throws Exception {
        post(product("Đèn pin", 999_999L)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.categoryId").value("Danh mục không tồn tại."));
    }

    @Test
    void imagesNeedExactlyOnePrimary() throws Exception {
        Map<String, Object> body = product("Balo 40L", tentId);
        body.put("images", List.of(image("https://img.example/a.jpg", true, 0), image("https://img.example/b.jpg", true, 1)));

        post(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.images").value("Cần chọn đúng 1 ảnh chính."));
    }

    @Test
    void imageUrlOfAnotherProductIsRejected() throws Exception {
        post(product("Ghế xếp", tentId)).andExpect(status().isCreated());
        Map<String, Object> other = product("Ghế xếp mini", tentId);
        other.put("images", List.of(image("https://img.example/ghe-xep-1.jpg", true, 0)));

        post(other).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.images").exists());
    }

    // --- Sửa / ẩn ------------------------------------------------------------------------------

    @Test
    void updateKeepsSlugWhenBlankAndReplacesImagesKeepingSameUrl() throws Exception {
        long id = data(post(product("Bình nước", tentId))).get("id").asLong();
        Map<String, Object> update = product("Bình nước giữ nhiệt", apparelId);
        update.put("slug", "");
        update.put("price", 320000);
        update.put("images", List.of(
                image("https://img.example/new.jpg", true, 0),
                image("https://img.example/binh-nuoc-1.jpg", false, 1)));

        JsonNode data = data(put(id, update).andExpect(status().isOk()));

        assertThat(data.get("slug").asText()).isEqualTo("binh-nuoc");
        assertThat(data.get("name").asText()).isEqualTo("Bình nước giữ nhiệt");
        assertThat(data.get("price").asLong()).isEqualTo(320000);
        assertThat(data.get("categoryId").asLong()).isEqualTo(apparelId);
        assertThat(data.get("primaryImageUrl").asText()).isEqualTo("https://img.example/new.jpg");
        assertThat(data.get("images")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM images", Integer.class)).isEqualTo(2);
    }

    @Test
    void changeStatusHidesProduct() throws Exception {
        long id = data(post(product("Áo khoác gió", apparelId))).get("id").asLong();

        mvc.perform(patch(BASE + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isActive").value(false));

        mvc.perform(get("/api/products/ao-khoac-gio")).andExpect(status().isNotFound());
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        mvc.perform(get(BASE + "/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    // --- Danh sách -----------------------------------------------------------------------------

    @Test
    void listFiltersByCategoryTreeKeywordAndStatusWithPagination() throws Exception {
        post(product("Lều 4 người", tentId));
        post(product("Lều 2 người", tentId));
        long hidden = data(post(product("Lều cũ", tentId))).get("id").asLong();
        post(product("Áo mưa", apparelId));
        mvc.perform(patch(BASE + "/" + hidden + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"isActive\":false}"));

        // Danh mục cha "Cắm trại" gồm cả "Lều trại"
        JsonNode byParent = mapper.readTree(mvc.perform(get(BASE).param("categoryId", String.valueOf(campingId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(byParent.get("pagination").get("totalItems").asInt()).isEqualTo(3);

        mvc.perform(get(BASE).param("q", "LỀU 2")).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get(BASE).param("isActive", "false")).andExpect(jsonPath("$.data[0].name").value("Lều cũ"));
        mvc.perform(get(BASE).param("limit", "2").param("page", "2"))
                .andExpect(jsonPath("$.pagination.currentPage").value(2))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get(BASE).param("limit", "500")).andExpect(status().isBadRequest());
    }

    @Test
    void listSearchesBrandAndAccentlessFiltersStockSortsAndReturnsCategoryPath() throws Exception {
        Map<String, Object> tent = product("Lều cắm trại 2 người", tentId);
        tent.put("brand", "Naturehike");
        tent.put("price", 900000);
        long tentProduct = data(post(tent)).get("id").asLong();
        Map<String, Object> jacket = product("Áo khoác gió", apparelId);
        jacket.put("brand", "Coleman");
        jacket.put("price", 500000);
        long jacketProduct = data(post(jacket)).get("id").asLong();
        setStock(tentProduct, 30);
        setStock(jacketProduct, 3);

        mvc.perform(get(BASE).param("q", "natureHIKE")).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(tentProduct));
        mvc.perform(get(BASE).param("q", "leu 2 nguoi")).andExpect(jsonPath("$.data.length()").value(1));
        // "2" phải khớp trọn từ: không khớp mã "...-9562"
        Map<String, Object> code = product("Lều 10 người 9562", tentId);
        code.put("images", List.of());
        code.put("price", 700000);
        long codeProduct = data(post(code)).get("id").asLong();
        setStock(codeProduct, 40);
        mvc.perform(get(BASE).param("q", "leu 2 nguoi")).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get(BASE).param("brand", "coleman")).andExpect(jsonPath("$.data[0].id").value(jacketProduct));
        mvc.perform(get(BASE).param("stock", "LOW_STOCK")).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(jacketProduct));
        mvc.perform(get(BASE).param("stock", "OUT_OF_STOCK")).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get(BASE).param("sort", "PRICE_ASC")).andExpect(jsonPath("$.data[0].id").value(jacketProduct));
        mvc.perform(get(BASE).param("sort", "PRICE_DESC")).andExpect(jsonPath("$.data[0].id").value(tentProduct));
        mvc.perform(get(BASE).param("sort", "khong-hop-le")).andExpect(status().isBadRequest());

        mvc.perform(get(BASE).param("sort", "STOCK_ASC")).andExpect(jsonPath("$.data[0].id").value(jacketProduct));

        JsonNode first = data(mvc.perform(get(BASE).param("brand", "Naturehike")));
        assertThat(first.get(0).get("categoryPath").get(0).asText()).isEqualTo("Cắm trại");
        assertThat(first.get(0).get("categoryPath").get(1).asText()).isEqualTo("Lều trại");
    }

    // --- Thông số, tồn kho -----------------------------------------------------------------------

    @Test
    void createWithSpecsAndShortDescriptionStartsWithZeroStock() throws Exception {
        Map<String, Object> body = product("Áo khoác gió", apparelId);
        body.put("specs", List.of(spec(" Chất liệu ", " Polyester "), spec("Trọng lượng", "300g")));
        body.put("shortDescription", "  Áo khoác chống gió nhẹ  ");

        JsonNode data = data(post(body).andExpect(status().isCreated()));

        assertThat(data.get("stockQuantity").asInt()).isZero();
        assertThat(data.has("variants")).isFalse();
        assertThat(data.get("specs").get(0).get("name").asText()).isEqualTo("Chất liệu");
        assertThat(data.get("specs").get(0).get("value").asText()).isEqualTo("Polyester");
        assertThat(data.get("shortDescription").asText()).isEqualTo("Áo khoác chống gió nhẹ");
    }

    @Test
    void duplicateSpecNamesAreRejected() throws Exception {
        Map<String, Object> dupSpec = product("Áo mưa", apparelId);
        dupSpec.put("specs", List.of(spec("Màu sắc", "Đỏ"), spec("màu sắc", "Xanh")));
        post(dupSpec).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.specs").exists());
    }

    @Test
    void updateDoesNotTouchStock() throws Exception {
        Map<String, Object> body = product("Giày leo núi", apparelId);
        long id = data(post(body)).get("id").asLong();
        setStock(id, 7);

        body.put("price", 500000);
        JsonNode updated = data(put(id, body).andExpect(status().isOk()));
        assertThat(updated.get("stockQuantity").asInt()).isEqualTo(7);
        assertThat(updated.get("price").asLong()).isEqualTo(500000);
    }

    // --- Tra cứu public ------------------------------------------------------------------------

    @Test
    void categoriesTreeAndBrands() throws Exception {
        Map<String, Object> body = product("Lều Naturehike", tentId);
        body.put("brand", "Naturehike");
        post(body);

        JsonNode tree = data(mvc.perform(get("/api/categories")).andExpect(status().isOk()));
        assertThat(tree).hasSize(2);
        JsonNode camping = null;
        for (JsonNode root : tree) {
            if (root.get("slug").asText().equals("cam-trai")) camping = root;
        }
        assertThat(camping).isNotNull();
        assertThat(camping.get("children")).hasSize(2);
        mvc.perform(get("/api/brands"))
                .andExpect(jsonPath("$.data[0].name").value("Naturehike"))
                .andExpect(jsonPath("$.data[0].productCount").value(1));
    }

    // ---------------------------------------------------------------------------------------------

    /** Thêm danh mục và gán path theo BR-13 ('/cha/.../id/'). */
    private long insertCategory(Long parentId, String name, String slug) {
        Long id = jdbc.queryForObject(
                "INSERT INTO categories (parent_id, name, slug, path) VALUES (?, ?, ?, '') RETURNING id",
                Long.class, parentId, name, slug);
        String parentPath = parentId == null ? "/"
                : jdbc.queryForObject("SELECT path FROM categories WHERE id = ?", String.class, parentId);
        jdbc.update("UPDATE categories SET path = ? WHERE id = ?", parentPath + id + "/", id);
        return id;
    }

    private Map<String, Object> product(String name, long categoryId) {
        String key = name.trim().isEmpty() ? "x" : vn.campvivo.common.util.SlugUtil.toSlug(name);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("price", 450000);
        body.put("categoryId", categoryId);
        body.put("isActive", true);
        body.put("images", List.of(
                image("https://img.example/" + key + "-1.jpg", true, 0),
                image("https://img.example/" + key + "-2.jpg", false, 1)));
        return body;
    }

    private static Map<String, Object> spec(String name, String value) {
        return Map.of("name", name, "value", value);
    }

    private void setStock(long productId, int stock) {
        jdbc.update("UPDATE products SET stock_quantity = ? WHERE id = ?", stock, productId);
    }

    private static Map<String, Object> image(String url, boolean primary, int order) {
        return Map.of("url", url, "isPrimary", primary, "displayOrder", order);
    }

    private ResultActions post(Map<String, Object> body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(BASE)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private ResultActions put(long id, Map<String, Object> body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(BASE + "/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("data");
    }
}
