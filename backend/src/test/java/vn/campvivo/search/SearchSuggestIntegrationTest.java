package vn.campvivo.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Gợi ý khi gõ (`GET /search/suggestions`), "Có phải bạn muốn tìm…?" (`suggestion` của `GET /search`) và migration V14 (`unaccent`,
 * `pg_trgm`) trên PostgreSQL thật. Cùng cấu hình với {@link SearchIntegrationTest} để dùng lại ngữ cảnh Spring. Dữ liệu gắn một NHÃN ngẫu nhiên
 * vào tên / slug / ảnh và mọi truy vấn chứa nhãn đó; mỗi test tự dọn dữ liệu của mình.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class SearchSuggestIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    private final String tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private long category;
    private User author;

    @BeforeEach
    void setUp() {
        category = jdbc.queryForObject("INSERT INTO categories (name, slug, path) VALUES (?, ?, '') RETURNING id", Long.class,
                "Danh mục " + tag, "dm-" + tag);
        jdbc.update("UPDATE categories SET path = ? WHERE id = ?", "/" + category + "/", category);
        author = users.saveAndFlush(new User(tag + "@campvivo.vn", passwordEncoder.encode("Passw0rd1"), "Tác giả " + tag));

        product("Lều " + tag, "leu-" + tag, null, true, null);
        product("Siêu Lều " + tag, "sieu-leu-" + tag, null, true, null);
        product("Naturehike Cắm Trại " + tag, "naturehike-cam-trai-" + tag, null, true, null);
        product("Bếpgasxyzabc " + tag, "bepgasxyzabc-" + tag, null, true, null);
        product("Đèn Pin " + tag, "den-pin-" + tag, null, true, "https://img.example/den-pin-" + tag + ".jpg");
        product("Balo Leo Núi " + tag, "balo-leo-nui-" + tag, "Zenith" + tag, true, null);
        product("Lều Ẩn " + tag, "leu-an-" + tag, null, false, null);

        combo("Combo Lều " + tag, "combo-leu-" + tag, true, "https://img.example/combo-leu-" + tag + ".jpg");
        combo("Combo Ẩn " + tag, "combo-an-" + tag, false, null);

        article("Lều Cắm Trại Cho Người Mới " + tag, "leu-cam-trai-bai-" + tag, true);
        article("Bài Nháp Lều " + tag, "bai-nhap-leu-" + tag, false);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM articles WHERE slug LIKE ?", "%" + tag);
        jdbc.update("DELETE FROM combo_items WHERE combo_id IN (SELECT id FROM product_combos WHERE slug LIKE ?)", "%" + tag);
        jdbc.update("DELETE FROM images WHERE image_url LIKE ?", "%" + tag + "%");
        jdbc.update("DELETE FROM product_combos WHERE slug LIKE ?", "%" + tag);
        jdbc.update("DELETE FROM product_variants WHERE product_id IN (SELECT id FROM products WHERE slug LIKE ?)", "%" + tag);
        jdbc.update("DELETE FROM products WHERE slug LIKE ?", "%" + tag);
        jdbc.update("DELETE FROM categories WHERE slug = ?", "dm-" + tag);
        users.findById(author.getId()).ifPresent(users::delete);
    }

    // --- Dữ liệu ---

    private void product(String name, String slug, String brand, boolean active, String imageUrl) {
        Long id = jdbc.queryForObject("""
                WITH p AS (INSERT INTO products (category_id, name, slug, brand, price, is_active) VALUES (?, ?, ?, ?, 1000000, ?) RETURNING id),
                v AS (INSERT INTO product_variants (product_id) SELECT id FROM p)
                SELECT id FROM p""", Long.class, category, name, slug, brand, active);
        if (imageUrl != null) {
            jdbc.update("INSERT INTO images (product_id, image_url, is_primary, display_order) VALUES (?, ?, true, 0)", id, imageUrl);
        }
    }

    private void combo(String name, String slug, boolean active, String imageUrl) {
        Long id = jdbc.queryForObject("""
                INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity, is_active)
                VALUES (?, ?, ?, 900000, 3, ?) RETURNING id""", Long.class, category, name, slug, active);
        if (imageUrl != null) {
            jdbc.update("INSERT INTO images (combo_id, image_url, is_primary, display_order) VALUES (?, ?, true, 0)", id, imageUrl);
        }
    }

    private void article(String title, String slug, boolean published) {
        jdbc.update("""
                INSERT INTO articles (user_id, title, slug, summary, content, category, is_published, published_at)
                VALUES (?, ?, ?, 'Tóm tắt', 'Nội dung', 'BLOG', ?, CASE WHEN ? THEN now() END)""",
                author.getId(), title, slug, published, published);
    }

    // --- Gọi API ---

    private MvcResult suggest(String q) throws Exception {
        return mvc.perform(get("/api/search/suggestions").param("q", q)).andReturn();
    }

    private MvcResult suggest(String q, int limit) throws Exception {
        return mvc.perform(get("/api/search/suggestions").param("q", q).param("limit", String.valueOf(limit))).andReturn();
    }

    private MvcResult search(String q) throws Exception {
        return mvc.perform(get("/api/search").param("q", q)).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private List<String> slugsOf(MvcResult result) throws Exception {
        return StreamSupport.stream(json(result).at("/data").spliterator(), false).map(n -> n.get("slug").asText()).toList();
    }

    private JsonNode itemBySlug(MvcResult result, String slug) throws Exception {
        return StreamSupport.stream(json(result).at("/data").spliterator(), false)
                .filter(n -> n.get("slug").asText().equals(slug)).findFirst().orElseThrow();
    }

    // --- Gợi ý khi gõ ---

    @Test
    void suggestionsArePublicAndRankedByPrefixThenContains() throws Exception {
        MvcResult result = suggest("leu " + tag);

        assertThat(status(result)).isEqualTo(200);
        assertThat(json(result).get("success").asBoolean()).isTrue();
        // Nhãn dài nằm trong từ khoá nên các mục chung nhãn cũng "gần giống" và nối ĐUÔI danh sách; hạng đầu là khớp thật:
        // hạng 0 (tên bắt đầu bằng từ khoá), rồi hạng 1 (tên chứa từ khoá, cùng độ giống thì theo tên A-Z).
        assertThat(slugsOf(result).subList(0, 3)).containsExactly("leu-" + tag, "combo-leu-" + tag, "sieu-leu-" + tag);
    }

    @Test
    void suggestionsIgnoreCaseAndAccents() throws Exception {
        List<String> expected = List.of("leu-" + tag, "combo-leu-" + tag, "sieu-leu-" + tag);
        List<String> reference = slugsOf(suggest("leu " + tag));
        for (String q : List.of("LỀU " + tag, "lều " + tag, "LEU " + tag, "Lều " + tag.toUpperCase())) {
            List<String> slugs = slugsOf(suggest(q));
            assertThat(slugs.subList(0, 3)).as(q).containsExactlyElementsOf(expected);
            assertThat(slugs).as(q + " cho đúng cùng danh sách và thứ tự như không dấu").isEqualTo(reference);
        }
        assertThat(slugsOf(suggest("den pin " + tag)).get(0)).isEqualTo("den-pin-" + tag);
        assertThat(slugsOf(suggest("đèn pin " + tag)).get(0)).isEqualTo("den-pin-" + tag);
    }

    @Test
    void aBrandMatchComesAfterNameMatches() throws Exception {
        // Chỉ thương hiệu "Zenith<nhãn>" chứa từ khoá (tên thì không): hạng 2, đứng trước các mục chỉ "gần giống" (hạng 3).
        // "Aaa…" đứng đầu bảng chữ cái và cũng gần giống (chung nhãn), nên nếu hạng thương hiệu bị bỏ thì nó sẽ lên trước.
        product("Aaa Sản Phẩm " + tag, "aaa-san-pham-" + tag, null, true, null);

        assertThat(slugsOf(suggest("zenith" + tag)).get(0)).isEqualTo("balo-leo-nui-" + tag);
    }

    @Test
    void aMistypedNameIsStillSuggested() throws Exception {
        // "natrehike" thiếu chữ u: không khớp chứa, nhưng đủ gần để gợi ý (từ khoá từ 4 ký tự bật khớp gần giống)
        assertThat(slugsOf(suggest("natrehike " + tag))).contains("naturehike-cam-trai-" + tag);
        // Từ vô nghĩa không gợi ý gì của test này
        assertThat(slugsOf(suggest("zzzzqqqq"))).doesNotContain("naturehike-cam-trai-" + tag, "leu-" + tag);
    }

    @Test
    void shortQueriesNeverUseFuzzyMatching() throws Exception {
        // Tên chứa từ "abcd". "abx" (3 ký tự) KHÔNG nằm trong tên nhưng chung 2/4 trigram với "abcd" (≈ 0,5 ≥ ngưỡng 0,4):
        // nếu khớp gần giống bật cho cả từ khoá ngắn thì "abx" sẽ ra sản phẩm này; "abcx" (4 ký tự, ≈ 0,6) thì phải ra.
        product("Abcd " + tag, "abcd-" + tag, null, true, null);

        assertThat(slugsOf(suggest("abx"))).as("3 ký tự: chỉ khớp đầu / chứa").doesNotContain("abcd-" + tag);
        assertThat(slugsOf(suggest("abcx"))).as("4 ký tự: bật khớp gần giống").contains("abcd-" + tag);
    }

    @Test
    void onlyActiveProductsAndCombosAreSuggestedNeverArticles() throws Exception {
        List<String> slugs = slugsOf(suggest(tag, 10));

        assertThat(slugs).containsExactlyInAnyOrder("leu-" + tag, "sieu-leu-" + tag, "naturehike-cam-trai-" + tag,
                "bepgasxyzabc-" + tag, "den-pin-" + tag, "balo-leo-nui-" + tag, "combo-leu-" + tag);
        assertThat(slugs).as("hàng ẩn không được gợi ý").doesNotContain("leu-an-" + tag, "combo-an-" + tag);
        assertThat(slugs).as("bài viết chưa có trong gợi ý").doesNotContain("leu-cam-trai-bai-" + tag, "bai-nhap-leu-" + tag);
        MvcResult r = suggest(tag, 10);
        for (JsonNode n : json(r).get("data")) {
            assertThat(n.get("type").asText()).isIn("PRODUCT", "COMBO");
        }
        assertThat(itemBySlug(r, "combo-leu-" + tag).get("type").asText()).isEqualTo("COMBO");
        assertThat(itemBySlug(r, "leu-" + tag).get("type").asText()).isEqualTo("PRODUCT");
    }

    @Test
    void imagesComeFromTheProductOrTheComboAndAreNullWhenMissing() throws Exception {
        MvcResult r = suggest(tag, 10);

        assertThat(itemBySlug(r, "den-pin-" + tag).get("imageUrl").asText()).isEqualTo("https://img.example/den-pin-" + tag + ".jpg");
        assertThat(itemBySlug(r, "combo-leu-" + tag).get("imageUrl").asText()).isEqualTo("https://img.example/combo-leu-" + tag + ".jpg");
        assertThat(itemBySlug(r, "leu-" + tag).get("imageUrl").isNull()).as("không có ảnh thì null").isTrue();
        assertThat(itemBySlug(r, "leu-" + tag).get("name").asText()).isEqualTo("Lều " + tag);
    }

    @Test
    void theLimitCapsTheNumberOfSuggestions() throws Exception {
        assertThat(slugsOf(suggest(tag, 2))).hasSize(2);
        assertThat(slugsOf(suggest(tag, 10))).hasSize(7);
        assertThat(slugsOf(suggest(tag))).as("mặc định 6").hasSize(6);
    }

    @Test
    void anInvalidQueryOrLimitIsRejectedWithTheReason() throws Exception {
        for (String q : List.of("", "   ", "a", "  a  ", "!!", "a".repeat(101))) {
            MvcResult r = suggest(q);
            assertThat(status(r)).as("q='" + q + "'").isEqualTo(400);
            assertThat(json(r).get("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(json(r).at("/details/fieldErrors/q").asText()).as("q='" + q + "'").isNotBlank();
        }
        assertThat(status(mvc.perform(get("/api/search/suggestions")).andReturn())).as("thiếu q").isEqualTo(400);
        for (int limit : new int[]{0, 11, -1}) {
            assertThat(status(suggest("lều " + tag, limit))).as("limit=" + limit).isEqualTo(400);
        }
        assertThat(status(mvc.perform(get("/api/search/suggestions").param("q", "lều " + tag).param("limit", "x")).andReturn())).isEqualTo(400);
        assertThat(status(suggest("ab"))).as("đúng 2 ký tự thì được").isEqualTo(200);
    }

    @Test
    void wildcardsAndSqlInTheQueryAreTakenLiterally() throws Exception {
        // Từ khoá 3 ký tự nên KHÔNG bật khớp gần giống: chỉ còn so khớp LIKE, kết quả phụ thuộc hoàn toàn vào việc thoát ký tự.
        // Nếu không thoát: "a%b" khớp "Aqxz Bqxz…" (a, rồi bất kỳ, rồi b); "a_b" khớp "Axb…"; "a\b" thành "ab" và khớp "Abxy…".
        product("Aqxz Bqxz " + tag, "aqxz-bqxz-" + tag, null, true, null);
        product("Axb " + tag, "axb-" + tag, null, true, null);
        product("Abxy " + tag, "abxy-" + tag, null, true, null);
        assertThat(slugsOf(suggest("a%b"))).as("% là ký tự thường").doesNotContain("aqxz-bqxz-" + tag);
        assertThat(slugsOf(suggest("a_b"))).as("_ là ký tự thường").doesNotContain("axb-" + tag);
        assertThat(slugsOf(suggest("a\\b"))).as("\\ là ký tự thường").doesNotContain("abxy-" + tag);
        // Kiểm chứng ngược: từ khoá thường cùng độ dài vẫn khớp, để chắc test không xanh chỉ vì sản phẩm không bao giờ khớp
        assertThat(slugsOf(suggest("axb"))).contains("axb-" + tag);

        MvcResult injection = suggest("'; DROP TABLE products; --");
        assertThat(status(injection)).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE slug LIKE ?", Integer.class, "%" + tag))
                .as("bảng products còn nguyên").isEqualTo(10);
    }

    // --- Có phải bạn muốn tìm…? ---

    @Test
    void aMistypedQueryWithNoResultsGetsACorrectedSuggestionWithTheOriginalAccents() throws Exception {
        MvcResult result = search("bepgasxyzabd");

        assertThat(status(result)).isEqualTo(200);
        assertThat(json(result).at("/totals/products").asLong()).as("từ khoá gõ sai không có kết quả").isZero();
        JsonNode suggestion = json(result).get("suggestion");
        assertThat(suggestion).as("phải có gợi ý sửa lỗi").isNotNull();
        assertThat(suggestion.get("q").asText()).as("dạng có dấu của từ trong kho").isEqualTo("bếpgasxyzabc");
        assertThat(suggestion.at("/totals/products").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void everyMistypedWordIsCorrectedAndOtherWordsAreKept() throws Exception {
        MvcResult result = search("bepgasxyzabd " + tag);

        JsonNode suggestion = json(result).get("suggestion");
        assertThat(suggestion).isNotNull();
        assertThat(suggestion.get("q").asText()).isEqualTo("bếpgasxyzabc " + tag);
        assertThat(suggestion.at("/totals/products").asLong()).isEqualTo(1);
        // Kiểm chứng ngược: chạy chính từ khoá được gợi ý thật sự ra kết quả
        assertThat(json(search(suggestion.get("q").asText())).at("/totals/products").asLong()).isEqualTo(1);
    }

    @Test
    void noSuggestionWhenThereAreResults() throws Exception {
        MvcResult result = search("lều " + tag);

        assertThat(json(result).at("/totals/products").asLong()).isPositive();
        assertThat(json(result).has("suggestion")).isFalse();

        // "natureh" là đầu của từ "naturehike" (sửa được thành "naturehike") nhưng ĐÃ có kết quả vì khớp chứa: không được gợi ý sửa
        MvcResult partial = search("natureh");
        assertThat(json(partial).at("/totals/products").asLong()).as("khớp chứa nên đã có kết quả").isPositive();
        assertThat(json(partial).has("suggestion")).as("đã có kết quả thì không gợi ý sửa lỗi").isFalse();
    }

    @Test
    void noSuggestionWhenNoWordIsCloseToAnythingKnown() throws Exception {
        assertThat(json(search("qqqqzzzzwwww")).has("suggestion")).isFalse();
    }

    @Test
    void noSuggestionWhenTheCorrectedQueryWouldAlsoFindNothing() throws Exception {
        // "bepgasxyzabd" sửa được thành "bếpgasxyzabc", nhưng từ thứ hai không có ở đâu nên cả cụm vẫn không ra kết quả
        MvcResult result = search("bepgasxyzabd quenmatxyzqq");

        assertThat(json(result).at("/totals/products").asLong()).isZero();
        assertThat(json(result).has("suggestion")).as("không gợi ý một từ khoá cũng trống").isFalse();
    }

    @Test
    void noSuggestionWhenATypeIsAsked() throws Exception {
        MvcResult result = mvc.perform(get("/api/search").param("q", "bepgasxyzabd").param("type", "PRODUCT")).andReturn();

        assertThat(status(result)).isEqualTo(200);
        assertThat(json(result).has("suggestion")).isFalse();
        assertThat(json(result).has("pagination")).isTrue();
    }

    @Test
    void anAlreadyCorrectWordIsNeverRewritten() throws Exception {
        // "quenmatxyzqq" không có trong kho: không được "sửa" thành từ khác chỉ vì cùng vài trigram
        MvcResult result = search("quenmatxyzqq " + tag);

        assertThat(json(result).at("/totals/products").asLong()).isZero();
        assertThat(json(result).has("suggestion")).isFalse();
    }

    // --- Migration V14 ---

    @Test
    void theMigrationInstalledTheExtensionsTheImmutableWrapperAndTheTrigramIndexes() {
        assertThat(jdbc.queryForList("SELECT extname FROM pg_extension WHERE extname IN ('unaccent', 'pg_trgm')", String.class))
                .containsExactlyInAnyOrder("unaccent", "pg_trgm");
        assertThat(jdbc.queryForObject("SELECT f_unaccent('Đường Lều Cắm Trại')", String.class)).isEqualTo("Duong Leu Cam Trai");
        assertThat(jdbc.queryForObject("SELECT provolatile FROM pg_proc WHERE proname = 'f_unaccent'", String.class))
                .as("phải IMMUTABLE mới đánh chỉ mục được").isEqualTo("i");
        assertThat(jdbc.queryForList("""
                SELECT indexname FROM pg_indexes WHERE indexname IN
                ('idx_products_name_trgm', 'idx_products_brand_trgm', 'idx_product_combos_name_trgm', 'idx_articles_title_trgm')""",
                String.class)).hasSize(4);
    }
}
