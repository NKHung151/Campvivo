package vn.campvivo.user.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.campvivo.auth.JwtService;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Danh sách người dùng của Admin (FR-14, N3-06) trên PostgreSQL thật. DB dùng chung với các test khác nên mỗi test gắn một NHÃN
 * riêng vào email và họ tên rồi tìm theo nhãn đó để cô lập dữ liệu. Không dùng @Transactional; mỗi test tự dọn user của mình.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class AdminUserIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final String tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private User admin;

    @AfterEach
    void cleanUp() {
        createdUserIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        createdUserIds.clear();
    }

    // --- Hạ tầng test ---

    private User newUser(String emailLocal, String fullName, Role role, boolean active) {
        User user = new User(emailLocal + "-" + tag + "@campvivo.vn", passwordEncoder.encode("Passw0rd1"), fullName);
        user.setRole(role);
        user.setActive(active);
        user.setEmailVerified(true);
        user = users.saveAndFlush(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private User admin() {
        if (admin == null) {
            admin = newUser("admin", tag + " Quản Trị", Role.ADMIN, true);
        }
        return admin;
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.createToken(user);
    }

    private MvcResult list(String query) throws Exception {
        return list(bearer(admin()), query);
    }

    private MvcResult list(String authorization, String query) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/admin/users" + (query == null || query.isEmpty() ? "" : "?" + query));
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mvc.perform(request).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private List<Long> ids(MvcResult result) throws Exception {
        return StreamSupport.stream(json(result).at("/data").spliterator(), false).map(n -> n.get("id").asLong()).toList();
    }

    /**
     * `q=<giá trị>` để NGUYÊN THÔ: `MockMvcRequestBuilders.get(String)` coi chuỗi là mẫu URI và tự mã hoá (khoảng trắng, %, \, chữ có
     * dấu...). Nếu mã hoá trước, `%20` bị mã hoá lần nữa thành `%2520` và server nhận chữ "%20" nguyên văn.
     */
    private static String q(String value) {
        return "q=" + value;
    }

    /** Lọc theo nhãn của test (email luôn chứa nhãn) cộng thêm điều kiện khác. */
    private String withTag(String extra) {
        return q(tag) + (extra == null || extra.isEmpty() ? "" : "&" + extra);
    }

    // --- Quyền ---

    @Test
    void onlyAdminsCanListUsers() throws Exception {
        User employee = newUser("nhanvien", "Nhân Viên", Role.EMPLOYEE, true);
        User customer = newUser("khach", "Khách Hàng", Role.CUSTOMER, true);

        MvcResult noToken = list(null, withTag(null));
        MvcResult asCustomer = list(bearer(customer), withTag(null));
        MvcResult asEmployee = list(bearer(employee), withTag(null));
        MvcResult asAdmin = list(withTag(null));

        assertThat(status(noToken)).isEqualTo(401);
        assertThat(json(noToken).at("/errorCode").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(status(asCustomer)).isEqualTo(403);
        assertThat(status(asEmployee)).as("EMPLOYEE qua được luật path /api/admin/** nhưng phải bị chặn ở endpoint").isEqualTo(403);
        assertThat(json(asEmployee).at("/errorCode").asText()).isEqualTo("FORBIDDEN");
        assertThat(status(asAdmin)).isEqualTo(200);
    }

    // --- Dữ liệu trả về ---

    @Test
    void eachRowHasOnlySafeFieldsAndThePaginationBlock() throws Exception {
        User user = newUser("an", tag + " Nguyen Van An", Role.CUSTOMER, true);
        user.setPhone("0912345678");
        user.setAddress("12 Lê Lợi");
        users.saveAndFlush(user);

        MvcResult result = list(withTag("role=CUSTOMER"));

        assertThat(status(result)).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.at("/success").asBoolean()).isTrue();
        assertThat(body.at("/pagination/currentPage").asInt()).isEqualTo(1);
        assertThat(body.at("/pagination/limit").asInt()).isEqualTo(12);
        assertThat(body.at("/pagination/totalItems").asLong()).isEqualTo(1);
        assertThat(body.at("/pagination/totalPages").asInt()).isEqualTo(1);
        JsonNode row = body.at("/data/0");
        assertThat(row.get("id").asLong()).isEqualTo(user.getId());
        assertThat(row.get("email").asText()).isEqualTo(user.getEmail());
        assertThat(row.get("fullName").asText()).isEqualTo(tag + " Nguyen Van An");
        assertThat(row.get("phone").asText()).isEqualTo("0912345678");
        assertThat(row.get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(row.get("isActive").asBoolean()).isTrue();
        assertThat(row.get("emailVerified").asBoolean()).isTrue();
        assertThat(row.get("createdAt").asText()).as("ISO-8601 có múi giờ +07:00").endsWith("+07:00");
        assertThat(row.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "email", "fullName", "phone", "role", "isActive", "emailVerified", "createdAt");
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(raw).doesNotContain("$2").doesNotContain("passwordHash").doesNotContain("otp").doesNotContain("tokenVersion")
                .doesNotContain("12 Lê Lợi");
    }

    // --- Tìm kiếm ---

    @Test
    void searchMatchesEmailOrFullNameIgnoringCase() throws Exception {
        User byName = newUser("a1", tag + " Nguyen Van An", Role.CUSTOMER, true);
        User byEmail = newUser("nguyen.thi.binh", tag + " Tran Thi B", Role.CUSTOMER, true);
        User other = newUser("c1", tag + " Le Van C", Role.CUSTOMER, true);

        List<Long> lower = ids(list(q(tag + " nguyen")));
        List<Long> byEmailPart = ids(list(q("NGUYEN.THI.BINH-" + tag.toUpperCase())));
        List<Long> upper = ids(list(q(tag.toUpperCase() + " NGUYEN VAN")));

        assertThat(lower).as("tên chứa 'nguyen' (khác hoa thường)").containsExactly(byName.getId());
        assertThat(byEmailPart).as("email chứa 'NGUYEN.THI.BINH' (khác hoa thường)").containsExactly(byEmail.getId());
        assertThat(upper).containsExactly(byName.getId());
        assertThat(ids(list(q(tag)))).containsExactlyInAnyOrder(byName.getId(), byEmail.getId(), other.getId(), admin().getId());
    }

    @Test
    void searchTreatsVietnameseUppercaseAndLowercaseAlike() throws Exception {
        User user = newUser("vn", tag + " NGUYỄN VĂN AN", Role.CUSTOMER, true);

        assertThat(ids(list(q(tag + " nguyễn")))).as("họ tên lưu chữ hoa có dấu, tìm bằng chữ thường có dấu")
                .containsExactly(user.getId());
    }

    @Test
    void theKeywordIsTrimmedAndABlankKeywordMeansNoFilter() throws Exception {
        User user = newUser("an", tag + " Nguyen Van An", Role.CUSTOMER, true);

        assertThat(ids(list(q("   " + tag + "   ") + "&role=CUSTOMER"))).as("khoảng trắng hai đầu bị bỏ")
                .containsExactly(user.getId());
        MvcResult blank = list(q("   ") + "&limit=100");
        assertThat(status(blank)).isEqualTo(200);
        assertThat(json(blank).at("/pagination/totalItems").asLong()).as("q rỗng = không lọc").isGreaterThanOrEqualTo(2);
    }

    @Test
    void wildcardCharactersInTheKeywordAreTreatedAsPlainText() throws Exception {
        User percent = newUser("p", tag + " 100% Cotton", Role.CUSTOMER, true);
        User plain = newUser("q", tag + " 100 Cotton", Role.CUSTOMER, true);
        User underscore = newUser("u", tag + " a_b", Role.CUSTOMER, true);
        User letter = newUser("v", tag + " axb", Role.CUSTOMER, true);
        User backslash = newUser("w", tag + " c\\d", Role.CUSTOMER, true);
        User noBackslash = newUser("x", tag + " cxd", Role.CUSTOMER, true);

        assertThat(ids(list(q(tag + " 100%")))).as("% là chữ thường").containsExactly(percent.getId());
        assertThat(ids(list(q(tag + " a_b")))).as("_ không khớp một ký tự bất kỳ").containsExactly(underscore.getId());
        assertThat(ids(list(q(tag + " c\\d")))).as("dấu gạch ngược là chữ thường").containsExactly(backslash.getId());
        assertThat(ids(list(q("%")))).as("chỉ '%' không khớp mọi người")
                .doesNotContain(plain.getId(), letter.getId(), noBackslash.getId());
    }

    @Test
    void anOverlyLongKeywordIsRejected() throws Exception {
        MvcResult result = list(q("x".repeat(101)));

        assertThat(status(result)).isEqualTo(400);
        assertThat(json(result).at("/errorCode").asText()).isEqualTo("VALIDATION_ERROR");
    }

    // --- Lọc ---

    @Test
    void filtersByRoleAndByActiveState() throws Exception {
        User customer = newUser("c", "Khách", Role.CUSTOMER, true);
        User lockedCustomer = newUser("cl", "Khách Bị Khoá", Role.CUSTOMER, false);
        User employee = newUser("e", "Nhân Viên", Role.EMPLOYEE, true);
        User lockedEmployee = newUser("el", "Nhân Viên Khoá", Role.EMPLOYEE, false);
        Long adminId = admin().getId();

        assertThat(ids(list(withTag("role=EMPLOYEE")))).containsExactlyInAnyOrder(employee.getId(), lockedEmployee.getId());
        assertThat(ids(list(withTag("role=ADMIN")))).containsExactly(adminId);
        assertThat(ids(list(withTag("isActive=false")))).containsExactlyInAnyOrder(lockedCustomer.getId(), lockedEmployee.getId());
        assertThat(ids(list(withTag("isActive=true")))).containsExactlyInAnyOrder(customer.getId(), employee.getId(), adminId);
        assertThat(ids(list(withTag("role=CUSTOMER&isActive=false")))).containsExactly(lockedCustomer.getId());
        assertThat(ids(list(withTag("")))).as("không lọc thì thấy cả tài khoản bị khoá")
                .contains(customer.getId(), lockedCustomer.getId(), employee.getId(), lockedEmployee.getId());
    }

    @Test
    void anUnknownRoleOrABadBooleanIsRejected() throws Exception {
        for (String query : List.of("role=HACKER", "role=customer", "isActive=maybe")) {
            MvcResult result = list(withTag(query));
            assertThat(status(result)).as(query).isEqualTo(400);
            assertThat(json(result).at("/errorCode").asText()).as(query).isEqualTo("VALIDATION_ERROR");
        }
    }

    // --- Phân trang và sắp xếp ---

    @Test
    void paginatesNewestFirstWithStableOrder() throws Exception {
        List<Long> newestFirst = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            newestFirst.add(newUser("u" + i, "Tên " + i, Role.CUSTOMER, true).getId());
        }
        Collections.reverse(newestFirst);

        MvcResult page1 = list(withTag("role=CUSTOMER&limit=2&page=1"));
        MvcResult page2 = list(withTag("role=CUSTOMER&limit=2&page=2"));
        MvcResult page3 = list(withTag("role=CUSTOMER&limit=2&page=3"));
        MvcResult beyond = list(withTag("role=CUSTOMER&limit=2&page=4"));

        assertThat(ids(page1)).containsExactly(newestFirst.get(0), newestFirst.get(1));
        assertThat(ids(page2)).containsExactly(newestFirst.get(2), newestFirst.get(3));
        assertThat(ids(page3)).containsExactly(newestFirst.get(4));
        assertThat(ids(beyond)).isEmpty();
        JsonNode pagination = json(page1).at("/pagination");
        assertThat(pagination.get("totalItems").asLong()).isEqualTo(5);
        assertThat(pagination.get("totalPages").asInt()).isEqualTo(3);
        assertThat(pagination.get("limit").asInt()).isEqualTo(2);
        assertThat(json(page2).at("/pagination/currentPage").asInt()).isEqualTo(2);
    }

    @Test
    void invalidPagingParametersAreRejected() throws Exception {
        for (String query : List.of("page=0", "page=-1", "limit=0", "limit=101", "limit=abc")) {
            MvcResult result = list(query);
            assertThat(status(result)).as(query).isEqualTo(400);
            assertThat(json(result).at("/errorCode").asText()).as(query).isEqualTo("VALIDATION_ERROR");
        }
        assertThat(status(list("limit=100"))).as("100 là giới hạn trên hợp lệ").isEqualTo(200);
    }
}
