package vn.campvivo.inventory.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.support.AdminMockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration test trang Tồn kho (FR-08, BR-8) và nhật ký đóng gói/tháo combo (D-36). */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long",
        "app.inventory.low-stock-threshold=5"
})
class AdminInventoryControllerTest {

    private static final String BASE = "/api/admin/inventory";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    /** Token ADMIN. */
    private MockMvc mvc;

    private long tent;
    private long stove;
    private long lamp;
    private long combo;

    @BeforeEach
    void setUp() {
        mvc = AdminMockMvc.admin(context, users, jwt);
        jdbc.update("DELETE FROM inventory_logs");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        Long category = jdbc.queryForObject("INSERT INTO categories (name, slug, path) VALUES ('Cắm trại', 'cam-trai', '') RETURNING id", Long.class);
        jdbc.update("UPDATE categories SET path = ? WHERE id = ?", "/" + category + "/", category);
        tent = insertProduct(category, "Lều 2 người", "leu-2-nguoi", 50);
        stove = insertProduct(category, "Bếp gas mini", "bep-gas-mini", 3);
        lamp = insertProduct(category, "Đèn lều", "den-leu", 0);
        combo = jdbc.queryForObject("""
                INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity)
                VALUES (?, 'Combo bếp', 'combo-bep', 1000000, 2) RETURNING id""", Long.class, category);
        jdbc.update("""
                INSERT INTO combo_items (combo_id, product_id, variant_id, quantity)
                SELECT ?, product_id, id, 1 FROM product_variants WHERE product_id IN (?, ?)""", combo, tent, stove);
    }

    // --- Danh sách -----------------------------------------------------------------------------

    @Test
    void listsProductsLowestAvailableFirstWithLowStockFilterAndSearch() throws Exception {
        JsonNode all = data(mvc.perform(get(BASE)).andExpect(status().isOk()));
        assertThat(names(all)).containsExactly("Đèn lều", "Bếp gas mini", "Lều 2 người");
        assertThat(all.get(0).get("isLowStock").asBoolean()).isTrue();
        assertThat(all.get(2).get("isLowStock").asBoolean()).isFalse();
        assertThat(all.get(2).get("availableQuantity").asInt()).isEqualTo(50);

        assertThat(names(data(mvc.perform(get(BASE).param("lowStock", "true"))))).containsExactly("Đèn lều", "Bếp gas mini");
        assertThat(names(data(mvc.perform(get(BASE).param("q", "leu 2"))))).containsExactly("Lều 2 người");
    }

    @Test
    void listCanBeSortedByAvailableDescNameOrLastUpdate() throws Exception {
        jdbc.update("UPDATE products SET updated_at = now() - interval '1 day'");
        jdbc.update("UPDATE products SET updated_at = now() WHERE id = ?", stove);

        assertThat(names(data(mvc.perform(get(BASE).param("sort", "AVAILABLE_DESC")))))
                .containsExactly("Lều 2 người", "Bếp gas mini", "Đèn lều");
        // Vị trí của "Đ" tuỳ collation của DB → chỉ chắc chắn "B" đứng đầu
        assertThat(names(data(mvc.perform(get(BASE).param("sort", "NAME_ASC")))).get(0)).isEqualTo("Bếp gas mini");
        assertThat(names(data(mvc.perform(get(BASE).param("sort", "UPDATED_DESC")))).get(0)).isEqualTo("Bếp gas mini");
        mvc.perform(get(BASE).param("sort", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void comboListShowsStockAndHowManySetsCanBePacked() throws Exception {
        JsonNode combos = data(mvc.perform(get(BASE).param("type", "COMBO")).andExpect(status().isOk()));
        assertThat(combos).hasSize(1);
        assertThat(combos.get(0).get("type").asText()).isEqualTo("COMBO");
        assertThat(combos.get(0).get("stockQuantity").asInt()).isEqualTo(2);
        // Lều còn 50, bếp còn 3, mỗi bộ 1 lều + 1 bếp → đóng gói được tối đa 3 bộ
        assertThat(combos.get(0).get("maxPackable").asInt()).isEqualTo(3);
    }

    // --- Điều chỉnh tay --------------------------------------------------------------------------

    @Test
    void importExportAdjustChangeStockAndWriteOneLogEach() throws Exception {
        JsonNode imported = data(adjust(Map.of("productId", tent, "changeQuantity", 10, "reason", "IMPORT", "note", " Lô hàng mới "))
                .andExpect(status().isCreated()));
        assertThat(imported.get("stockAfter").asInt()).isEqualTo(60);
        assertThat(imported.get("note").asText()).isEqualTo("Lô hàng mới");
        assertThat(imported.get("user").get("fullName").asText()).isEqualTo("Test ADMIN");
        adjust(Map.of("productId", tent, "changeQuantity", -5, "reason", "EXPORT")).andExpect(status().isCreated());
        adjust(Map.of("productId", tent, "changeQuantity", -3, "reason", "ADJUST")).andExpect(status().isCreated());

        assertThat(stock("products", tent)).isEqualTo(52);
        JsonNode logs = data(mvc.perform(get(BASE + "/logs").param("productId", String.valueOf(tent))));
        assertThat(logs).hasSize(3);
        assertThat(logs.get(0).get("reason").asText()).isEqualTo("ADJUST");
        assertThat(logs.get(0).get("changeQuantity").asInt()).isEqualTo(-3);
        assertThat(logs.get(0).get("stockAfter").asInt()).isEqualTo(52);
        assertThat(logs.get(2).get("reason").asText()).isEqualTo("IMPORT");
        assertThat(data(mvc.perform(get(BASE + "/logs").param("reason", "EXPORT")))).hasSize(1);
    }

    @Test
    void invalidAdjustmentsAreRejected() throws Exception {
        adjust(Map.of("productId", tent, "comboId", combo, "changeQuantity", 1, "reason", "IMPORT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.variantId").exists());
        adjust(Map.of("productId", tent, "changeQuantity", -1, "reason", "IMPORT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.changeQuantity").exists());
        adjust(Map.of("productId", tent, "changeQuantity", 1, "reason", "EXPORT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.changeQuantity").exists());
        adjust(Map.of("productId", tent, "changeQuantity", 1, "reason", "RETURN"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.reason").exists());
        adjust(Map.of("productId", 999_999L, "changeQuantity", 1, "reason", "IMPORT")).andExpect(status().isNotFound());

        // Bếp còn 3, đang giữ chỗ 2 cho đơn → xuất 2 thì còn 1 < 2 → từ chối, không đổi kho, không ghi log
        jdbc.update("UPDATE products SET reserved_quantity = 2 WHERE id = ?", stove);
        adjust(Map.of("productId", stove, "changeQuantity", -2, "reason", "EXPORT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_STOCK_ADJUSTMENT"));
        assertThat(stock("products", stove)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_logs", Long.class)).isZero();
    }

    @Test
    void comboCanOnlyBeDecreasedByHand() throws Exception {
        adjust(Map.of("comboId", combo, "changeQuantity", 1, "reason", "IMPORT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_STOCK_ADJUSTMENT"));
        adjust(Map.of("comboId", combo, "changeQuantity", 1, "reason", "ADJUST"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_STOCK_ADJUSTMENT"));

        JsonNode log = data(adjust(Map.of("comboId", combo, "changeQuantity", -1, "reason", "EXPORT", "note", "Bộ bị móp"))
                .andExpect(status().isCreated()));
        assertThat(log.get("type").asText()).isEqualTo("COMBO");
        assertThat(log.get("stockAfter").asInt()).isEqualTo(1);
        // Hàng lẻ không đổi: bộ combo hỏng là mất thật
        assertThat(stock("product_combos", combo)).isEqualTo(1);
        assertThat(stock("products", tent)).isEqualTo(50);
    }

    // --- Đóng gói / tháo combo ghi nhật ký (D-36) --------------------------------------------------

    @Test
    void packAndUnpackLogComboAndEachProductAndEmployeeCanDoIt() throws Exception {
        MockMvc employee = AdminMockMvc.as(Role.EMPLOYEE, context, users, jwt);
        employee.perform(post("/api/admin/combos/" + combo + "/pack").contentType(MediaType.APPLICATION_JSON).content("{\"sets\":2}"))
                .andExpect(status().isOk());
        assertThat(stock("product_combos", combo)).isEqualTo(4);
        assertThat(stock("products", stove)).isEqualTo(1);

        JsonNode logs = data(employee.perform(get(BASE + "/logs")).andExpect(status().isOk()));
        assertThat(logs).hasSize(3);
        Map<String, Integer> changes = new HashMap<>();
        logs.forEach(l -> changes.put(l.get("itemName").asText(), l.get("changeQuantity").asInt()));
        assertThat(changes).containsEntry("Combo bếp", 2).containsEntry("Lều 2 người", -2).containsEntry("Bếp gas mini", -2);
        assertThat(logs.get(0).get("note").asText()).contains("Đóng gói 2 bộ");
        assertThat(logs.get(0).get("user").get("fullName").asText()).isEqualTo("Test EMPLOYEE");

        mvc.perform(post("/api/admin/combos/" + combo + "/unpack").contentType(MediaType.APPLICATION_JSON).content("{\"sets\":1}"))
                .andExpect(status().isOk());
        JsonNode comboLogs = data(mvc.perform(get(BASE + "/logs").param("comboId", String.valueOf(combo))));
        assertThat(comboLogs).hasSize(2);
        assertThat(comboLogs.get(0).get("reason").asText()).isEqualTo("EXPORT");
        assertThat(comboLogs.get(0).get("stockAfter").asInt()).isEqualTo(3);
        assertThat(data(mvc.perform(get(BASE + "/logs").param("productId", String.valueOf(stove)))).get(0).get("changeQuantity").asInt())
                .isEqualTo(1);
    }

    @Test
    void staffOnly() throws Exception {
        AdminMockMvc.as(Role.EMPLOYEE, context, users, jwt).perform(get(BASE)).andExpect(status().isOk());
        AdminMockMvc.as(Role.CUSTOMER, context, users, jwt).perform(get(BASE)).andExpect(status().isForbidden());
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        anonymous.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void productWithVariantsIsAdjustedPerVariant() throws Exception {
        long large = addSizeOption(lamp, 1);

        JsonNode rows = data(mvc.perform(get(BASE).param("q", "den leu")).andExpect(status().isOk()));
        assertThat(rows.get(0).get("variants").findValuesAsText("title")).containsExactly("M", "L");
        assertThat(rows.get(0).get("isLowStock").asBoolean()).isTrue();

        adjust(Map.of("productId", lamp, "changeQuantity", 3, "reason", "IMPORT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.variantId").exists());
        adjust(Map.of("productId", tent, "variantId", large, "changeQuantity", 3, "reason", "IMPORT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.variantId").exists());

        JsonNode log = data(adjust(Map.of("variantId", large, "changeQuantity", 3, "reason", "IMPORT"))
                .andExpect(status().isCreated()));
        assertThat(log.get("itemId").asLong()).isEqualTo(lamp);
        assertThat(log.get("variantTitle").asText()).isEqualTo("L");
        assertThat(log.get("stockAfter").asInt()).isEqualTo(4);
        assertThat(variantStock(large)).isEqualTo(4);
        assertThat(stock("products", lamp)).isEqualTo(4);

        JsonNode logs = data(mvc.perform(get(BASE + "/logs").param("variantId", String.valueOf(large))));
        assertThat(logs).hasSize(1);
        assertThat(data(mvc.perform(get(BASE + "/logs").param("productId", String.valueOf(lamp))))).hasSize(1);
    }

    @Test
    void soldUnitsAreAllocatedToVariantsWithoutTouchingProductTotal() throws Exception {
        long large = addSizeOption(lamp, 5);
        // Đơn hàng xác nhận chỉ trừ tổng sản phẩm (giỏ / đơn chưa lưu biến thể)
        jdbc.update("UPDATE products SET stock_quantity = stock_quantity - 2 WHERE id = ?", lamp);

        JsonNode row = data(mvc.perform(get(BASE).param("q", "den leu")).andExpect(status().isOk())).get(0);
        assertThat(row.get("stockQuantity").asInt()).isEqualTo(3);
        assertThat(row.get("unallocatedSold").asInt()).isEqualTo(2);

        Map<String, Object> tooMany = new java.util.HashMap<>(Map.of("variantId", large, "changeQuantity", -3, "reason", "EXPORT"));
        tooMany.put("allocateSold", true);
        adjust(tooMany).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_STOCK_ADJUSTMENT"));

        Map<String, Object> allocate = new java.util.HashMap<>(Map.of("variantId", large, "changeQuantity", -2, "reason", "EXPORT"));
        allocate.put("allocateSold", true);
        JsonNode log = data(adjust(allocate).andExpect(status().isCreated()));
        assertThat(log.get("variantTitle").asText()).isEqualTo("L");
        assertThat(log.get("note").asText()).isEqualTo("Phân bổ hàng đã bán theo đơn hàng");
        assertThat(variantStock(large)).isEqualTo(3);
        assertThat(stock("products", lamp)).isEqualTo(3);

        row = data(mvc.perform(get(BASE).param("q", "den leu"))).get(0);
        assertThat(row.get("unallocatedSold").asInt()).isZero();
        // Xuất thường thì trừ cả biến thể lẫn tổng
        adjust(Map.of("variantId", large, "changeQuantity", -1, "reason", "EXPORT")).andExpect(status().isCreated());
        assertThat(variantStock(large)).isEqualTo(2);
        assertThat(stock("products", lamp)).isEqualTo(2);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Gắn tuỳ chọn "Size" (M, L) cho sản phẩm bằng SQL: biến thể mặc định thành "M" (giữ tồn kho), thêm biến thể "L"
     * với `stockL`. Trả về id biến thể "L".
     */
    private long addSizeOption(long productId, int stockL) {
        Long option = jdbc.queryForObject(
                "INSERT INTO product_options (product_id, name, position) VALUES (?, 'Size', 1) RETURNING id", Long.class, productId);
        Long m = jdbc.queryForObject(
                "INSERT INTO product_option_values (option_id, value, position) VALUES (?, 'M', 0) RETURNING id", Long.class, option);
        Long l = jdbc.queryForObject(
                "INSERT INTO product_option_values (option_id, value, position) VALUES (?, 'L', 1) RETURNING id", Long.class, option);
        jdbc.update("UPDATE product_variants SET option1_value_id = ? WHERE product_id = ?", m, productId);
        long large = jdbc.queryForObject("""
                INSERT INTO product_variants (product_id, option1_value_id, stock_quantity, position)
                VALUES (?, ?, ?, 1) RETURNING id""", Long.class, productId, l, stockL);
        // Tồn sản phẩm = tổng các biến thể (D-37)
        jdbc.update("UPDATE products SET stock_quantity = stock_quantity + ? WHERE id = ?", stockL, productId);
        return large;
    }

    private int variantStock(long variantId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id = ?", Integer.class, variantId);
    }

    private long insertProduct(long categoryId, String name, String slug, int stock) {
        return jdbc.queryForObject("""
                WITH p AS (INSERT INTO products (category_id, name, slug, price, stock_quantity)
                           VALUES (?, ?, ?, 100000, ?) RETURNING id, stock_quantity),
                v AS (INSERT INTO product_variants (product_id, stock_quantity) SELECT id, stock_quantity FROM p)
                SELECT id FROM p""", Long.class, categoryId, name, slug, stock);
    }

    private int stock(String table, long id) {
        return jdbc.queryForObject("SELECT stock_quantity FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    private ResultActions adjust(Map<String, Object> body) throws Exception {
        return mvc.perform(post(BASE + "/adjustments").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private static List<String> names(JsonNode items) {
        List<String> out = new ArrayList<>();
        items.forEach(i -> out.add(i.get("name").asText()));
        return out;
    }
}
