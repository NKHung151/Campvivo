package vn.campvivo.article;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import vn.campvivo.support.AdminMockMvc;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bài viết (FR-21): admin `/api/admin/articles` và public `/api/articles` trên PostgreSQL thật.
 * Bài test dùng slug bắt đầu bằng "bvt-" để dọn. Cấu hình giống các test review để dùng chung Spring context.
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
class ArticleIntegrationTest {

    private static final String ADMIN = "/api/admin/articles";
    private static final String IMG = "https://res.cloudinary.com/demo/image/upload/a.jpg";

    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @MockBean private GhnService ghn;

    private MockMvc admin;
    private MockMvc guest;

    @BeforeEach
    void setUp() {
        cleanUp();
        admin = AdminMockMvc.admin(context, users, jwt);
        guest = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM articles WHERE slug LIKE 'bvt-%'");
    }

    @Test
    void draftCanBeIncompleteSlugIsGeneratedAndContentIsSanitized() throws Exception {
        Map<String, Object> body = body("Bvt Chọn túi ngủ theo nhiệt độ", false);
        body.put("summary", null);
        body.put("thumbnailUrl", "");
        body.put("content", "<p onclick=\"x()\">Ấm <script>alert(1)</script><b>đủ</b></p>");

        JsonNode a = data(admin.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()));

        assertThat(a.get("slug").asText()).isEqualTo("bvt-chon-tui-ngu-theo-nhiet-do");
        assertThat(a.get("isPublished").asBoolean()).isFalse();
        assertThat(a.get("publishedAt").isNull()).isTrue();
        assertThat(a.get("authorName").asText()).isEqualTo("Test ADMIN");
        assertThat(a.get("content").asText()).contains("<b>đủ</b>").doesNotContain("script", "onclick");

        // Cùng tiêu đề → slug thêm -2; tự nhập slug đã có → 409
        JsonNode second = data(admin.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(json(body("Bvt Chọn túi ngủ theo nhiệt độ", false)))).andExpect(status().isCreated()));
        assertThat(second.get("slug").asText()).isEqualTo("bvt-chon-tui-ngu-theo-nhiet-do-2");
        Map<String, Object> dup = body("Bvt khác", false);
        dup.put("slug", "bvt-chon-tui-ngu-theo-nhiet-do");
        admin.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON).content(json(dup)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("SLUG_ALREADY_EXISTS"));
    }

    @Test
    void publishingRequiresSummaryThumbnailAndContentAndKeepsFirstPublishDate() throws Exception {
        Map<String, Object> incomplete = body("Bvt Mẹo dựng lều", true);
        incomplete.put("summary", " ");
        incomplete.put("thumbnailUrl", null);
        incomplete.put("content", "<p>&nbsp;</p>");
        admin.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON).content(json(incomplete)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fieldErrors.summary").exists())
                .andExpect(jsonPath("$.details.fieldErrors.thumbnailUrl").exists())
                .andExpect(jsonPath("$.details.fieldErrors.content").exists());

        JsonNode a = data(admin.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content(json(body("Bvt Mẹo dựng lều", true)))).andExpect(status().isCreated()));
        long id = a.get("id").asLong();
        String firstPublished = a.get("publishedAt").asText();
        assertThat(a.get("isPublished").asBoolean()).isTrue();

        // Gỡ rồi đăng lại (PATCH) → giữ ngày đăng đầu
        admin.perform(patch(ADMIN + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"isPublished\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isPublished").value(false));
        JsonNode again = data(admin.perform(patch(ADMIN + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"isPublished\":true}")).andExpect(status().isOk()));
        assertThat(instant(again.get("publishedAt").asText())).isEqualTo(instant(firstPublished));

        // Sửa: bỏ ảnh mà vẫn để xuất bản → 400; slug trống giữ slug cũ
        Map<String, Object> edit = body("Bvt Mẹo dựng lều nhanh", true);
        edit.put("thumbnailUrl", "");
        admin.perform(put(ADMIN + "/" + id).contentType(MediaType.APPLICATION_JSON).content(json(edit)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.thumbnailUrl").exists());
        JsonNode edited = data(admin.perform(put(ADMIN + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json(body("Bvt Mẹo dựng lều nhanh", true)))).andExpect(status().isOk()));
        assertThat(edited.get("title").asText()).isEqualTo("Bvt Mẹo dựng lều nhanh");
        assertThat(edited.get("slug").asText()).isEqualTo("bvt-meo-dung-leu");
    }

    @Test
    void adminListFiltersAndPublicSeesOnlyPublished() throws Exception {
        Map<String, Object> guide = body("Bvt Hướng dẫn chọn balo", true);
        guide.put("category", "GUIDE");
        long guideId = data(admin.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON).content(json(guide)))
                .andExpect(status().isCreated())).get("id").asLong();
        admin.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON).content(json(body("Bvt Nháp chưa xong", false))))
                .andExpect(status().isCreated());

        // Admin: lọc theo loại, trạng thái, từ khoá không dấu
        JsonNode drafts = data(admin.perform(get(ADMIN + "?isPublished=false&q=bvt")).andExpect(status().isOk()));
        assertThat(drafts.findValuesAsText("title")).containsExactly("Bvt Nháp chưa xong");
        JsonNode guides = data(admin.perform(get(ADMIN + "?category=GUIDE&q=chon balo")).andExpect(status().isOk()));
        assertThat(guides.findValuesAsText("title")).containsExactly("Bvt Hướng dẫn chọn balo");
        admin.perform(get(ADMIN + "?category=NEWS")).andExpect(status().isBadRequest());

        // Public: chỉ bài đã đăng; bài nháp → 404
        JsonNode list = data(guest.perform(get("/api/articles?category=GUIDE")).andExpect(status().isOk()));
        assertThat(list.findValuesAsText("slug")).contains("bvt-huong-dan-chon-balo").doesNotContain("bvt-nhap-chua-xong");
        guest.perform(get("/api/articles/bvt-huong-dan-chon-balo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("<p>Nội dung bài viết</p>"))
                .andExpect(jsonPath("$.data.authorName").value("Test ADMIN"));
        guest.perform(get("/api/articles/bvt-nhap-chua-xong")).andExpect(status().isNotFound());

        // Xoá cứng → public cũng mất
        admin.perform(delete(ADMIN + "/" + guideId)).andExpect(status().isOk());
        guest.perform(get("/api/articles/bvt-huong-dan-chon-balo")).andExpect(status().isNotFound());
        admin.perform(get(ADMIN + "/" + guideId)).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminManagesArticles() throws Exception {
        MockMvc employee = AdminMockMvc.as(Role.EMPLOYEE, context, users, jwt);
        employee.perform(get(ADMIN)).andExpect(status().isForbidden());
        employee.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON).content(json(body("Bvt Nhân viên", false))))
                .andExpect(status().isForbidden());
        guest.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        admin.perform(post(ADMIN).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Bvt thiếu loại\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors.category").exists());
    }

    // ---------------------------------------------------------------------------------------------

    private static Map<String, Object> body(String title, boolean published) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("summary", "Tóm tắt ngắn");
        m.put("content", "<p>Nội dung bài viết</p>");
        m.put("thumbnailUrl", IMG);
        m.put("category", "BLOG");
        m.put("isPublished", published);
        return m;
    }

    /** Cùng thời điểm, bỏ khác biệt múi giờ/độ chính xác giữa giá trị vừa tạo và giá trị đọc lại từ DB (micro giây). */
    private static Instant instant(String iso) {
        return OffsetDateTime.parse(iso).toInstant().truncatedTo(ChronoUnit.MICROS);
    }

    private String json(Object o) throws Exception {
        return mapper.writeValueAsString(o);
    }

    private JsonNode data(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }
}
