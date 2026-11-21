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
 * `GET /search` (FR-07, N2-11) trên PostgreSQL thật. DB dùng chung với các lớp test khác nên mọi dữ liệu gắn một NHÃN ngẫu nhiên
 * vào tên và slug, và mọi truy vấn chứa nhãn đó để kết quả chỉ gồm dữ liệu của test này; mỗi test tự dọn dữ liệu của mình.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class SearchIntegrationTest {

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

        product("Lều Dã Ngoại " + tag, "leu-da-ngoai-" + tag, true);
        product("Áo Gió Mùa Đông " + tag, "ao-gio-mua-dong-" + tag, true);
        product("Lều Ẩn " + tag, "leu-an-" + tag, false);

        combo("Combo Lều Cắm Trại " + tag, "combo-leu-cam-trai-" + tag, true);
        combo("Combo Ẩn " + tag, "combo-an-" + tag, false);

        article("Cách Chọn Lều Cắm Trại " + tag, "cach-chon-leu-cam-trai-" + tag, true, 2);
        article("Bài Nháp Lều " + tag, "bai-nhap-leu-" + tag, false, 0);
        article("Kinh Nghiệm Leo Núi " + tag, "kinh-nghiem-leo-nui-" + tag, true, 1);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM articles WHERE slug LIKE ?", "%" + tag);
        jdbc.update("DELETE FROM combo_items WHERE combo_id IN (SELECT id FROM product_combos WHERE slug LIKE ?)", "%" + tag);
        jdbc.update("DELETE FROM product_combos WHERE slug LIKE ?", "%" + tag);
        jdbc.update("DELETE FROM product_variants WHERE product_id IN (SELECT id FROM products WHERE slug LIKE ?)", "%" + tag);
        jdbc.update("DELETE FROM products WHERE slug LIKE ?", "%" + tag);
        jdbc.update("DELETE FROM categories WHERE slug = ?", "dm-" + tag);
        users.findById(author.getId()).ifPresent(users::delete);
    }

    // --- Dữ liệu ---

    private void product(String name, String slug, boolean active) {
        jdbc.queryForObject("""
                WITH p AS (INSERT INTO products (category_id, name, slug, price, is_active) VALUES (?, ?, ?, 1000000, ?) RETURNING id),
                v AS (INSERT INTO product_variants (product_id) SELECT id FROM p)
                SELECT id FROM p""", Long.class, category, name, slug, active);
    }

    private void combo(String name, String slug, boolean active) {
        jdbc.update("INSERT INTO product_combos (category_id, name, slug, combo_price, stock_quantity, is_active) VALUES (?, ?, ?, 900000, 3, ?)",
                category, name, slug, active);
    }

    /** `daysAgo`: số ngày trước hiện tại; bài nháp không có ngày xuất bản. */
    private void article(String title, String slug, boolean published, int daysAgo) {
        jdbc.update("""
                INSERT INTO articles (user_id, title, slug, summary, content, category, is_published, published_at)
                VALUES (?, ?, ?, 'Tóm tắt', 'Nội dung', 'BLOG', ?, CASE WHEN ? THEN now() - make_interval(days => ?) END)""",
                author.getId(), title, slug, published, published, daysAgo);
    }

    // --- Gọi API ---

    private MvcResult search(String q) throws Exception {
        return mvc.perform(get("/api/search").param("q", q)).andReturn();
    }

    private MvcResult search(String q, String type, int page, int limit) throws Exception {
        return mvc.perform(get("/api/search").param("q", q).param("type", type)
                .param("page", String.valueOf(page)).param("limit", String.valueOf(limit))).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private List<String> slugs(MvcResult result, String group) throws Exception {
        return StreamSupport.stream(json(result).at("/data/" + group).spliterator(), false).map(n -> n.get("slug").asText()).toList();
    }

    private void assertValidationError(MvcResult result, String expectedMessagePart) throws Exception {
        assertThat(status(result)).isEqualTo(400);
        JsonNode body = json(result);
        assertThat(body.get("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.at("/details/fieldErrors/q").asText()).as("lý do phải nêu rõ ở trường q").contains(expectedMessagePart);
    }

    // --- Cả ba loại ---

    @Test
    void searchingWithoutATypeReturnsAllThreeGroupsAndTheirTotals() throws Exception {
        MvcResult result = search("lều " + tag);

        assertThat(status(result)).isEqualTo(200);
        assertThat(slugs(result, "products")).containsExactly("leu-da-ngoai-" + tag);
        assertThat(slugs(result, "combos")).containsExactly("combo-leu-cam-trai-" + tag);
        assertThat(slugs(result, "articles")).containsExactly("cach-chon-leu-cam-trai-" + tag);
        JsonNode totals = json(result).get("totals");
        assertThat(totals.get("products").asLong()).isEqualTo(1);
        assertThat(totals.get("combos").asLong()).isEqualTo(1);
        assertThat(totals.get("articles").asLong()).isEqualTo(1);
        assertThat(json(result).has("pagination")).as("không hỏi `type` thì không có pagination").isFalse();
        assertThat(json(result).get("success").asBoolean()).isTrue();
        assertThat(json(result).at("/data/products/0/type").asText()).isEqualTo("PRODUCT");
        assertThat(json(result).at("/data/combos/0/type").asText()).isEqualTo("COMBO");
        assertThat(json(result).at("/data/articles/0/title").asText()).isEqualTo("Cách Chọn Lều Cắm Trại " + tag);
        assertThat(json(result).at("/data/articles/0/category").asText()).isEqualTo("BLOG");
    }

    @Test
    void searchIsPublicAndIgnoresCase() throws Exception {
        // Không gửi Authorization: khách vãng lai tìm được
        for (String q : List.of("LỀU " + tag, "lều " + tag, "Lều " + tag.toUpperCase())) {
            MvcResult r = search(q);
            assertThat(status(r)).as(q).isEqualTo(200);
            assertThat(slugs(r, "products")).as(q).containsExactly("leu-da-ngoai-" + tag);
            assertThat(slugs(r, "articles")).as(q).containsExactly("cach-chon-leu-cam-trai-" + tag);
        }
    }

    @Test
    void searchingWithoutAccentsFindsAccentedNames() throws Exception {
        for (String q : List.of("leu " + tag, "LEU " + tag, "lêu " + tag)) {
            MvcResult r = search(q);
            assertThat(slugs(r, "products")).as(q).containsExactly("leu-da-ngoai-" + tag);
            assertThat(slugs(r, "combos")).as(q).containsExactly("combo-leu-cam-trai-" + tag);
            assertThat(slugs(r, "articles")).as(q).containsExactly("cach-chon-leu-cam-trai-" + tag);
        }
        // "mùa đông" gõ không dấu ra sản phẩm "Áo Gió Mùa Đông"
        assertThat(slugs(search("mua dong " + tag), "products")).containsExactly("ao-gio-mua-dong-" + tag);
        assertThat(slugs(search("áo gió " + tag), "products")).containsExactly("ao-gio-mua-dong-" + tag);
    }

    @Test
    void hiddenItemsAreNeverReturnedNorCounted() throws Exception {
        MvcResult result = search(tag);

        assertThat(slugs(result, "products")).containsExactlyInAnyOrder("leu-da-ngoai-" + tag, "ao-gio-mua-dong-" + tag)
                .doesNotContain("leu-an-" + tag);
        assertThat(slugs(result, "combos")).containsExactly("combo-leu-cam-trai-" + tag);
        assertThat(slugs(result, "articles")).containsExactlyInAnyOrder("cach-chon-leu-cam-trai-" + tag, "kinh-nghiem-leo-nui-" + tag)
                .doesNotContain("bai-nhap-leu-" + tag);
        JsonNode totals = json(result).get("totals");
        assertThat(totals.get("products").asLong()).as("sản phẩm ẩn không tính").isEqualTo(2);
        assertThat(totals.get("combos").asLong()).as("combo ẩn không tính").isEqualTo(1);
        assertThat(totals.get("articles").asLong()).as("bài nháp không tính").isEqualTo(2);
    }

    @Test
    void anItemOutsideTheQueryIsNotReturned() throws Exception {
        MvcResult result = search("leo núi " + tag);

        assertThat(slugs(result, "articles")).containsExactly("kinh-nghiem-leo-nui-" + tag);
        assertThat(slugs(result, "products")).isEmpty();
        assertThat(slugs(result, "combos")).isEmpty();
        assertThat(json(result).at("/totals/products").asLong()).isZero();
    }

    @Test
    void noMatchReturnsEmptyGroupsAndZeroTotals() throws Exception {
        MvcResult result = search("khongcoketqua" + tag);

        assertThat(status(result)).isEqualTo(200);
        assertThat(slugs(result, "products")).isEmpty();
        assertThat(slugs(result, "combos")).isEmpty();
        assertThat(slugs(result, "articles")).isEmpty();
        assertThat(json(result).at("/data/products").isArray()).as("luôn là mảng, không phải null").isTrue();
        assertThat(json(result).at("/totals/articles").asLong()).isZero();
    }

    @Test
    void wildcardCharactersInTheQueryAreTakenLiterally() throws Exception {
        // Tiêu đề chữ thường KHÔNG dấu, có chữ "a" rồi "t" phía sau: nếu `%` không được thoát thì "a%t" (mẫu LIKE `%a%t%`) sẽ khớp.
        // (Các bài ở setUp toàn chữ có dấu nên không đủ để phân biệt hai trường hợp.)
        article("cam trai tren dat", "cam-trai-tren-dat-" + tag, true, 0);

        MvcResult result = search("a%t");

        assertThat(status(result)).isEqualTo(200);
        assertThat(slugs(result, "articles")).as("không bài nào chứa đúng chuỗi 'a%t'").doesNotContain("cam-trai-tren-dat-" + tag);
        assertThat(json(result).at("/totals/products").asLong()).isZero();
        assertThat(json(result).at("/totals/combos").asLong()).isZero();
        // Kiểm chứng ngược: tìm đúng cụm có trong tiêu đề vẫn ra, để chắc test không xanh chỉ vì bài viết không bao giờ khớp
        assertThat(slugs(search("tren dat " + tag), "articles")).contains("cam-trai-tren-dat-" + tag);
    }

    @Test
    void surroundingSpacesInTheQueryAreIgnored() throws Exception {
        assertThat(slugs(search("   lều " + tag + "   "), "products")).containsExactly("leu-da-ngoai-" + tag);
    }

    // --- Từ khoá không hợp lệ ---

    @Test
    void aMissingOrTooShortQueryIsRejectedWithTheReason() throws Exception {
        assertThat(status(mvc.perform(get("/api/search")).andReturn())).as("thiếu q").isEqualTo(400);
        assertValidationError(search(""), "ít nhất 2 ký tự");
        assertValidationError(search("   "), "ít nhất 2 ký tự");
        assertValidationError(search("a"), "ít nhất 2 ký tự");
        assertValidationError(search("  a  "), "ít nhất 2 ký tự");
        assertValidationError(search("l"), "ít nhất 2 ký tự");
        assertThat(status(search("ab"))).as("đúng 2 ký tự thì được").isEqualTo(200);
    }

    @Test
    void aTooLongQueryIsRejected() throws Exception {
        assertValidationError(search("a".repeat(101)), "tối đa 100");
        assertThat(status(search("a".repeat(100)))).isEqualTo(200);
    }

    @Test
    void aQueryMadeOnlyOfSymbolsIsRejectedBecauseItWouldMatchEveryCombo() throws Exception {
        assertValidationError(search("!!"), "chữ hoặc số");
        assertValidationError(search("%%"), "chữ hoặc số");
        assertValidationError(search("--"), "chữ hoặc số");
        assertThat(status(search("c#"))).as("có chữ thì được").isEqualTo(200);
    }

    @Test
    void anUnknownTypeOrBadPagingIsRejected() throws Exception {
        for (String[] bad : new String[][]{{"type", "SHOE"}, {"type", "product"}, {"page", "0"}, {"page", "x"}, {"limit", "0"}, {"limit", "51"}, {"limit", "x"}}) {
            MvcResult r = mvc.perform(get("/api/search").param("q", "lều " + tag).param(bad[0], bad[1])).andReturn();
            assertThat(status(r)).as(bad[0] + "=" + bad[1]).isEqualTo(400);
            assertThat(json(r).get("errorCode").asText()).as(bad[0] + "=" + bad[1]).isEqualTo("VALIDATION_ERROR");
        }
        assertThat(status(mvc.perform(get("/api/search").param("q", "lều " + tag).param("limit", "50")).andReturn())).isEqualTo(200);
    }

    // --- Lọc theo loại và phân trang ---

    @Test
    void askingForOneTypeReturnsOnlyThatGroupWithPaginationAndAllTotals() throws Exception {
        MvcResult products = search(tag, "PRODUCT", 1, 12);
        assertThat(slugs(products, "products")).hasSize(2);
        assertThat(slugs(products, "combos")).isEmpty();
        assertThat(slugs(products, "articles")).isEmpty();
        assertThat(json(products).at("/pagination/totalItems").asLong()).isEqualTo(2);
        assertThat(json(products).at("/pagination/currentPage").asInt()).isEqualTo(1);
        assertThat(json(products).at("/pagination/limit").asInt()).isEqualTo(12);
        assertThat(json(products).at("/totals/combos").asLong()).as("totals vẫn đủ ba loại").isEqualTo(1);
        assertThat(json(products).at("/totals/articles").asLong()).isEqualTo(2);

        MvcResult combos = search(tag, "COMBO", 1, 12);
        assertThat(slugs(combos, "combos")).containsExactly("combo-leu-cam-trai-" + tag);
        assertThat(slugs(combos, "products")).isEmpty();
        assertThat(json(combos).at("/pagination/totalItems").asLong()).isEqualTo(1);
        assertThat(json(combos).at("/totals/products").asLong()).isEqualTo(2);

        MvcResult articles = search(tag, "ARTICLE", 1, 12);
        assertThat(slugs(articles, "articles")).hasSize(2);
        assertThat(slugs(articles, "products")).isEmpty();
        assertThat(json(articles).at("/pagination/totalItems").asLong()).isEqualTo(2);
        assertThat(json(articles).at("/totals/products").asLong()).isEqualTo(2);
    }

    @Test
    void pagingThroughOneTypeNeitherSkipsNorRepeats() throws Exception {
        for (String type : List.of("PRODUCT", "ARTICLE")) {
            String group = type.equals("PRODUCT") ? "products" : "articles";
            List<String> page1 = slugs(search(tag, type, 1, 1), group);
            MvcResult second = search(tag, type, 2, 1);
            List<String> page2 = slugs(second, group);
            List<String> page3 = slugs(search(tag, type, 3, 1), group);

            assertThat(page1).as(type).hasSize(1);
            assertThat(page2).as(type).hasSize(1).doesNotContainAnyElementsOf(page1);
            assertThat(page3).as(type + " trang vượt quá").isEmpty();
            assertThat(json(second).at("/pagination/totalPages").asInt()).as(type).isEqualTo(2);
            assertThat(json(second).at("/pagination/currentPage").asInt()).as(type).isEqualTo(2);
        }
    }

    @Test
    void withoutATypeEachGroupIsCappedAtLimitAndPageIsIgnored() throws Exception {
        MvcResult first = mvc.perform(get("/api/search").param("q", tag).param("limit", "1")).andReturn();
        MvcResult pageTwo = mvc.perform(get("/api/search").param("q", tag).param("limit", "1").param("page", "2")).andReturn();

        assertThat(slugs(first, "products")).hasSize(1);
        assertThat(slugs(first, "articles")).hasSize(1);
        assertThat(json(first).at("/totals/products").asLong()).as("total vẫn là tổng thật, không phải số đang hiện").isEqualTo(2);
        assertThat(json(first).at("/totals/articles").asLong()).isEqualTo(2);
        assertThat(slugs(pageTwo, "products")).isEqualTo(slugs(first, "products"));
        assertThat(slugs(pageTwo, "articles")).isEqualTo(slugs(first, "articles"));
    }

    @Test
    void totalsAgreeWhetherOrNotATypeIsGiven() throws Exception {
        JsonNode all = json(search(tag)).get("totals");
        for (String type : List.of("PRODUCT", "COMBO", "ARTICLE")) {
            JsonNode scoped = json(search(tag, type, 1, 12)).get("totals");
            assertThat(scoped).as(type).isEqualTo(all);
        }
    }

    // --- Thứ tự bài viết ---

    @Test
    void articlesAreRankedByRelevanceThenNewestFirst() throws Exception {
        // Bài có tiêu đề BẮT ĐẦU bằng từ khoá xếp trước dù cũ hơn; hai bài còn lại (từ khoá nằm giữa) theo ngày mới nhất trước
        article(tag + " Hướng dẫn chọn lều", "huong-dan-chon-leu-" + tag, true, 30);

        List<String> order = slugs(search(tag), "articles");

        assertThat(order).containsExactly("huong-dan-chon-leu-" + tag, "kinh-nghiem-leo-nui-" + tag, "cach-chon-leu-cam-trai-" + tag);
    }
}
