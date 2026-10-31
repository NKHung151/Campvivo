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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.campvivo.auth.JwtService;
import vn.campvivo.support.AdminMockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration test quản lý danh mục (FR-05, BR-13) và khung thông số (D-21). */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.locations=classpath:db/migration",
        "app.jwt.secret=test_only_secret_at_least_32_characters_long"
})
class AdminCategoryControllerTest {

    private static final String BASE = "/api/admin/categories";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    /** Đi qua security thật (JWT filter + @PreAuthorize) với token ADMIN. */
    private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        mvc = AdminMockMvc.admin(context, users, jwt);
        jdbc.update("DELETE FROM inventory_logs");
        jdbc.update("DELETE FROM product_combos");
        jdbc.update("DELETE FROM images");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
    }

    @Test
    void createBuildsPathAndSlugAndTreeShowsCounts() throws Exception {
        long camping = id(post(body("Cắm trại", null)).andExpect(status().isCreated()));
        JsonNode tent = data(post(body("Lều trại", camping)).andExpect(status().isCreated()));

        assertThat(tent.get("slug").asText()).isEqualTo("leu-trai");
        assertThat(path(tent.get("id").asLong())).isEqualTo("/" + camping + "/" + tent.get("id").asLong() + "/");

        insertProduct(tent.get("id").asLong(), "leu-2-nguoi");
        JsonNode root = data(mvc.perform(get(BASE)).andExpect(status().isOk())).get(0);
        assertThat(root.get("name").asText()).isEqualTo("Cắm trại");
        assertThat(root.get("productCount").asLong()).isZero();
        assertThat(root.get("children").get(0).get("productCount").asLong()).isEqualTo(1);
    }

    @Test
    void autoSlugGetsSuffixAndExplicitDuplicateIsRejected() throws Exception {
        post(body("Bếp", null)).andExpect(status().isCreated());
        assertThat(data(post(body("Bếp", null))).get("slug").asText()).isEqualTo("bep-2");

        Map<String, Object> dup = body("Bếp ga", null);
        dup.put("slug", "bep");
        post(dup).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("SLUG_ALREADY_EXISTS"));
    }

    @Test
    void moveUpdatesPathOfWholeSubtreeAndBlocksCycles() throws Exception {
        long a = id(post(body("A", null)));
        long b = id(post(body("B", null)));
        long a1 = id(post(body("A1", a)));
        long a11 = id(post(body("A11", a1)));

        put(a1, body("A1", b)).andExpect(status().isOk());
        assertThat(path(a1)).isEqualTo("/" + b + "/" + a1 + "/");
        assertThat(path(a11)).isEqualTo("/" + b + "/" + a1 + "/" + a11 + "/");

        put(a1, body("A1", a11)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CIRCULAR_CATEGORY_DEPENDENCY"));
        put(a1, body("A1", a1)).andExpect(status().isBadRequest());

        put(a1, body("A1", null)).andExpect(status().isOk());
        assertThat(path(a11)).isEqualTo("/" + a1 + "/" + a11 + "/");
    }

    @Test
    void deleteIsBlockedWhenCategoryHasChildrenOrProducts() throws Exception {
        long a = id(post(body("A", null)));
        long a1 = id(post(body("A1", a)));
        insertProduct(a1, "san-pham");

        mvc.perform(delete(BASE + "/" + a)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_NOT_EMPTY"));
        mvc.perform(delete(BASE + "/" + a1)).andExpect(status().isConflict());

        jdbc.update("DELETE FROM products");
        mvc.perform(delete(BASE + "/" + a1)).andExpect(status().isOk());
        mvc.perform(delete(BASE + "/" + a)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM categories", Long.class)).isZero();
    }

    @Test
    void invalidRequestReturnsFieldErrors() throws Exception {
        Map<String, Object> bad = body(" ", null);
        bad.put("slug", "Sai Slug");
        post(bad).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.name").exists())
                .andExpect(jsonPath("$.details.fieldErrors.slug").exists());
        post(body("X", 999_999L)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PARENT_CATEGORY_NOT_FOUND"));
    }

    // ---------------------------------------------------------------------------------------------

    private static Map<String, Object> body(String name, Long parentId) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("parentId", parentId);
        return m;
    }

    private void insertProduct(long categoryId, String slug) {
        jdbc.update("INSERT INTO products (category_id, name, slug, price, created_at, updated_at) VALUES (?, ?, ?, 100000, now(), now())",
                categoryId, slug, slug);
    }

    private String path(long id) {
        return jdbc.queryForObject("SELECT path FROM categories WHERE id = ?", String.class, id);
    }

    private static List<String> texts(JsonNode arr) {
        return StreamSupport.stream(arr.spliterator(), false).map(JsonNode::asText).toList();
    }

    private ResultActions post(Map<String, Object> body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(BASE)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private ResultActions put(long id, Map<String, Object> body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(BASE + "/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private long id(ResultActions result) throws Exception {
        return data(result).get("id").asLong();
    }

    private JsonNode data(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }
}
