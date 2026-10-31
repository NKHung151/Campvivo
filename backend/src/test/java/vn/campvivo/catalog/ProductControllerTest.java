package vn.campvivo.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration test API catalog public (FR-07): GET /products, GET /products/{slug}. */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long"
})
class ProductControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private long tentId;
    private long apparelId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM inventory_logs");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        long camping = insertCategory(null, "Cắm trại", "cam-trai");
        tentId = insertCategory(camping, "Lều trại", "leu-trai");
        apparelId = insertCategory(null, "Trang phục", "trang-phuc");
    }

    @Test
    void listShowsOnlyActiveProductsAndFiltersByCategoryTreeBrandAndKeyword() throws Exception {
        insertProduct(tentId, "Lều 2 người Naturehike", "leu-2-nguoi", "Naturehike", 1_200_000, true, 1);
        insertProduct(tentId, "Lều 4 người Coleman", "leu-4-nguoi", "Coleman", 2_500_000, true, 2);
        insertProduct(tentId, "Lều cũ", "leu-cu", "Coleman", 900_000, false, 3);
        insertProduct(apparelId, "Áo khoác gió", "ao-khoac-gio", "Naturehike", 500_000, true, 4);

        assertThat(slugs(list("?sortBy=price_asc"))).containsExactly("ao-khoac-gio", "leu-2-nguoi", "leu-4-nguoi");
        assertThat(slugs(list("?category=cam-trai&sortBy=price_desc"))).containsExactly("leu-4-nguoi", "leu-2-nguoi");
        assertThat(slugs(list("?brand=naturehike&sortBy=price_asc"))).containsExactly("ao-khoac-gio", "leu-2-nguoi");
        assertThat(slugs(list("?q=leu%202%20nguoi"))).containsExactly("leu-2-nguoi");
        assertThat(slugs(list("?category=khong-co"))).isEmpty();
        assertThat(slugs(list("?slugs=leu-4-nguoi,leu-cu,ao-khoac-gio&sortBy=name_asc"))).containsExactly("ao-khoac-gio", "leu-4-nguoi");
    }

    @Test
    void shortKeywordMatchesWholeWordsOnlyAndRanksNameMatchesFirst() throws Exception {
        insertProduct(apparelId, "Tất chạy bộ Aonijie", "tat-chay-bo-aonijie", "AONIJIE", 150_000, true, 5);
        insertProduct(tentId, "Ghế kayak tháo rời", "ghe-kayak-thao-roi", null, 900_000, true, 6);
        insertProduct(apparelId, "Quần dài cho người mặc áo khoác", "quan-dai-cho-nguoi-mac-ao-khoac", null, 300_000, true, 7);
        insertProduct(apparelId, "Áo gió nam", "ao-gio-nam", null, 500_000, true, 1);
        insertProduct(tentId, "Đèn lều", "den-leu", null, 200_000, true, 2);
        insertProduct(apparelId, "Quần màu đen", "quan-mau-den", null, 400_000, true, 8);

        // "áo": không khớp thương hiệu AONIJIE hay chữ "tháo"; tên bắt đầu bằng "Áo" đứng trước
        assertThat(slugs(list("?q=%C3%A1o"))).containsExactly("ao-gio-nam", "quan-dai-cho-nguoi-mac-ao-khoac");
        // "đèn" (có dấu) xếp trên "đen" dù cả hai khớp không dấu
        assertThat(slugs(list("?q=%C4%91%C3%A8n"))).containsExactly("den-leu", "quan-mau-den");
    }

    @Test
    void listItemHasPriceStockAndPrimaryImage() throws Exception {
        long id = insertProduct(tentId, "Lều 2 người", "leu-2-nguoi", "Naturehike", 1_200_000, true, 1);
        setStock(id, 5, 2);
        jdbc.update("INSERT INTO images (product_id, image_url, is_primary, display_order) VALUES (?, 'https://img.example/a.jpg', true, 0)", id);

        JsonNode item = list("").get("data").get(0);

        assertThat(item.get("type").asText()).isEqualTo("PRODUCT");
        assertThat(item.get("price").asLong()).isEqualTo(1_200_000);
        assertThat(item.get("originalPrice").asLong()).isEqualTo(1_200_000);
        assertThat(item.get("availableQuantity").asInt()).isEqualTo(3);
        assertThat(item.get("primaryImageUrl").asText()).isEqualTo("https://img.example/a.jpg");
        assertThat(item.get("brand").asText()).isEqualTo("Naturehike");
    }

    @Test
    void detailHasStockSpecsAndCategoryPath() throws Exception {
        long id = insertProduct(tentId, "Lều 2 người", "leu-2-nguoi", "Naturehike", 1_200_000, true, 1);
        jdbc.update("UPDATE products SET short_description = 'Nhẹ',"
                + " specs = '[{\"name\":\"Chất liệu\",\"value\":\"Polyester\"}]'::jsonb WHERE id = ?", id);
        setStock(id, 5, 0);

        JsonNode data = mapper.readTree(mvc.perform(get("/api/products/leu-2-nguoi"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");

        assertThat(data.has("variants")).isFalse();
        assertThat(data.get("availableQuantity").asInt()).isEqualTo(5);
        assertThat(data.get("specs").get(0).get("name").asText()).isEqualTo("Chất liệu");
        assertThat(data.get("shortDescription").asText()).isEqualTo("Nhẹ");
        assertThat(data.get("category").get("path").findValuesAsText("slug")).containsExactly("cam-trai", "leu-trai");
    }

    @Test
    void hiddenOrUnknownProductIsNotFound() throws Exception {
        insertProduct(tentId, "Lều cũ", "leu-cu", null, 900_000, false, 1);

        mvc.perform(get("/api/products/leu-cu")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        mvc.perform(get("/api/products/khong-co")).andExpect(status().isNotFound());
    }

    @Test
    void invalidQueryIsRejected() throws Exception {
        mvc.perform(get("/api/products?sortBy=abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products?limit=101")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------------------

    private JsonNode list(String query) throws Exception {
        return mapper.readTree(mvc.perform(get(URI.create("/api/products" + query)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> slugs(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("data").forEach(n -> out.add(n.get("slug").asText()));
        return out;
    }

    private long insertCategory(Long parentId, String name, String slug) {
        Long id = jdbc.queryForObject(
                "INSERT INTO categories (parent_id, name, slug, path) VALUES (?, ?, ?, '') RETURNING id",
                Long.class, parentId, name, slug);
        String parentPath = parentId == null ? "/"
                : jdbc.queryForObject("SELECT path FROM categories WHERE id = ?", String.class, parentId);
        jdbc.update("UPDATE categories SET path = ? WHERE id = ?", parentPath + id + "/", id);
        return id;
    }

    /** `day` = ngày tạo (1..28/10/2026) để sắp "mới nhất" ổn định. */
    private long insertProduct(long categoryId, String name, String slug, String brand, long price, boolean active, int day) {
        return jdbc.queryForObject("""
                INSERT INTO products (category_id, name, slug, brand, price, is_active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, make_timestamptz(2026, 10, ?, 0, 0, 0), now()) RETURNING id
                """, Long.class, categoryId, name, slug, brand, price, active, day);
    }

    private void setStock(long productId, int stock, int reserved) {
        jdbc.update("UPDATE products SET stock_quantity = ?, reserved_quantity = ? WHERE id = ?", stock, reserved, productId);
    }
}
