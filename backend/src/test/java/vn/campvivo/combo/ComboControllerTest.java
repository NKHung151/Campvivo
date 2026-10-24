package vn.campvivo.combo;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration test combo public (FR-07): chỉ combo đang bán, lọc theo danh mục cấp 1, chi tiết thành phần. */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long"
})
class ComboControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private long camping;
    private long hiking;
    private long tent;
    private long shirt;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        camping = insertCategory("Cắm trại", "cam-trai");
        hiking = insertCategory("Leo núi", "leo-nui");
        tent = insertProduct("Lều 2 người", "leu-2-nguoi", 1_200_000);
        shirt = insertProduct("Áo gió", "ao-gio", 400_000);
        jdbc.update("INSERT INTO images (product_id, image_url, is_primary, display_order) VALUES (?, 'https://img.example/leu.jpg', true, 0)", tent);

        long a = insertCombo(camping, "Combo cắm trại", "combo-cam-trai", 1_400_000, true, 3);
        jdbc.update("INSERT INTO combo_items (combo_id, product_id, quantity) VALUES (?, ?, 1), (?, ?, 2)", a, tent, a, shirt);
        long b = insertCombo(hiking, "Combo leo núi", "combo-leo-nui", 900_000, true, 0);
        jdbc.update("INSERT INTO combo_items (combo_id, product_id, quantity) VALUES (?, ?, 1), (?, ?, 1)", b, tent, b, shirt);
        insertCombo(camping, "Combo ẩn", "combo-an", 100_000, false, 0);
        // Combo leo núi có ảnh tự tải lên → ảnh đại diện là ảnh đó, rồi mới tới ảnh sản phẩm thành phần
        jdbc.update("INSERT INTO images (combo_id, image_url, is_primary, display_order) VALUES (?, 'https://img.example/combo-leo-nui.jpg', true, 0)", b);
    }

    @Test
    void listShowsOnlyActiveCombosWithTotalsAndFilters() throws Exception {
        mvc.perform(get("/api/combos").param("sortBy", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(2))
                .andExpect(jsonPath("$.data[0].slug").value("combo-leo-nui"))
                .andExpect(jsonPath("$.data[1].type").value("COMBO"))
                .andExpect(jsonPath("$.data[1].price").value(1_400_000))
                .andExpect(jsonPath("$.data[1].itemsTotal").value(2_000_000))
                .andExpect(jsonPath("$.data[1].itemCount").value(2))
                .andExpect(jsonPath("$.data[1].category.slug").value("cam-trai"))
                .andExpect(jsonPath("$.data[1].availableQuantity").value(3))
                // Combo không có ảnh riêng: ảnh = ảnh chính của sản phẩm thành phần đầu tiên có ảnh
                .andExpect(jsonPath("$.data[1].primaryImageUrl").value("https://img.example/leu.jpg"));

        mvc.perform(get("/api/combos").param("category", "leo-nui"))
                .andExpect(jsonPath("$.pagination.totalItems").value(1))
                .andExpect(jsonPath("$.data[0].slug").value("combo-leo-nui"));
        mvc.perform(get("/api/combos").param("q", "cam trai"))
                .andExpect(jsonPath("$.pagination.totalItems").value(1))
                .andExpect(jsonPath("$.data[0].slug").value("combo-cam-trai"));
        mvc.perform(get("/api/combos").param("sortBy", "oldest")).andExpect(status().isBadRequest());
    }

    @Test
    void detailListsItemsAndHidesInactive() throws Exception {
        mvc.perform(get("/api/combos/combo-cam-trai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Combo cắm trại"))
                .andExpect(jsonPath("$.data.category.slug").value("cam-trai"))
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andExpect(jsonPath("$.data.images[0].url").value("https://img.example/leu.jpg"))
                .andExpect(jsonPath("$.data.primaryImageUrl").value("https://img.example/leu.jpg"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].product.slug").value("leu-2-nguoi"))
                .andExpect(jsonPath("$.data.items[0].product.primaryImageUrl").value("https://img.example/leu.jpg"))
                .andExpect(jsonPath("$.data.items[1].product.slug").value("ao-gio"))
                .andExpect(jsonPath("$.data.items[1].quantity").value(2));

        mvc.perform(get("/api/combos/combo-leo-nui"))
                .andExpect(jsonPath("$.data.primaryImageUrl").value("https://img.example/combo-leo-nui.jpg"))
                .andExpect(jsonPath("$.data.images.length()").value(2))
                .andExpect(jsonPath("$.data.images[0].isPrimary").value(true))
                .andExpect(jsonPath("$.data.images[1].url").value("https://img.example/leu.jpg"))
                .andExpect(jsonPath("$.data.images[1].isPrimary").value(false));
        mvc.perform(get("/api/combos").param("category", "leo-nui"))
                .andExpect(jsonPath("$.data[0].primaryImageUrl").value("https://img.example/combo-leo-nui.jpg"));

        mvc.perform(get("/api/combos/combo-an")).andExpect(status().isNotFound());
        mvc.perform(get("/api/combos/khong-co")).andExpect(status().isNotFound());
    }

    private long insertCategory(String name, String slug) {
        Long id = jdbc.queryForObject("INSERT INTO categories (name, slug, path) VALUES (?, ?, '') RETURNING id", Long.class, name, slug);
        jdbc.update("UPDATE categories SET path = ? WHERE id = ?", "/" + id + "/", id);
        return id;
    }

    private long insertProduct(String name, String slug, long price) {
        return jdbc.queryForObject("INSERT INTO products (category_id, name, slug, price) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, camping, name, slug, price);
    }

    private long insertCombo(long categoryId, String name, String slug, long price, boolean active, int stock) {
        return jdbc.queryForObject("""
                INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity, is_active)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, categoryId, name, slug, price, stock, active);
    }
}
