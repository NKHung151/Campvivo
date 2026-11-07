package vn.campvivo.user.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Chi tiết và sửa người dùng của Admin (FR-14, N3-07) trên PostgreSQL thật. Đơn hàng cho phần thống kê được chèn bằng SQL tối
 * thiểu (không đi qua luồng đặt hàng). Không dùng @Transactional; mỗi test tự dọn đơn và user của mình.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class AdminUserDetailIntegrationTest {

    private static final String PASSWORD = "Passw0rd1";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final String tag = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private User admin;

    @AfterEach
    void cleanUp() {
        createdUserIds.forEach(id -> {
            jdbc.update("DELETE FROM orders WHERE user_id = ?", id);
            users.findById(id).ifPresent(users::delete);
        });
        createdUserIds.clear();
    }

    // --- Hạ tầng test ---

    private User newUser(String emailLocal, Role role, boolean active) {
        User user = new User(emailLocal + "-" + tag + "@campvivo.vn", passwordEncoder.encode(PASSWORD), "Người Dùng " + emailLocal);
        user.setRole(role);
        user.setActive(active);
        user.setEmailVerified(true);
        user = users.saveAndFlush(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private User admin() {
        if (admin == null) {
            admin = newUser("admin", Role.ADMIN, true);
        }
        return admin;
    }

    private User reload(User user) {
        return users.findById(user.getId()).orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.createToken(user);
    }

    /** Chèn một đơn tối thiểu; `final_amount = subtotal + shipping` (ràng buộc chk_orders_amounts, không giảm giá). */
    private void insertOrder(User user, String status, long subtotal, long shipping) {
        jdbc.update("INSERT INTO orders (order_code, user_id, subtotal, discount_amount, shipping_fee, final_amount, recipient_name, "
                        + "recipient_phone, shipping_address, shipping_district_id, shipping_ward_code, payment_method, status) "
                        + "VALUES (?, ?, ?, 0, ?, ?, 'Người Nhận', '0901234567', '12 Lê Lợi', 1442, '20109', 'COD', ?)",
                "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), user.getId(), subtotal, shipping,
                subtotal + shipping, status);
    }

    private MvcResult send(MockHttpServletRequestBuilder request, String authorization) throws Exception {
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult getDetail(String authorization, Object id) throws Exception {
        return send(get("/api/admin/users/{id}", id), authorization);
    }

    private MvcResult patchUser(String authorization, Object id, String jsonBody) throws Exception {
        return send(patch("/api/admin/users/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody.getBytes(StandardCharsets.UTF_8)), authorization);
    }

    private MvcResult login(User user) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD)))).andReturn();
    }

    private MvcResult me(String authorization) throws Exception {
        return send(get("/api/users/me"), authorization);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private String errorCode(MvcResult result) throws Exception {
        return json(result).at("/errorCode").asText();
    }

    // --- Quyền ---

    @Test
    void onlyAdminsCanViewOrEditUsers() throws Exception {
        User target = newUser("target", Role.CUSTOMER, true);
        User employee = newUser("nhanvien", Role.EMPLOYEE, true);
        User customer = newUser("khach", Role.CUSTOMER, true);
        String body = "{\"fullName\":\"Tên Mới\"}";

        for (MvcResult result : List.of(getDetail(null, target.getId()), patchUser(null, target.getId(), body))) {
            assertThat(status(result)).isEqualTo(401);
            assertThat(errorCode(result)).isEqualTo("UNAUTHORIZED");
        }
        for (String token : List.of(bearer(customer), bearer(employee))) {
            for (MvcResult result : List.of(getDetail(token, target.getId()), patchUser(token, target.getId(), body))) {
                assertThat(status(result)).isEqualTo(403);
                assertThat(errorCode(result)).isEqualTo("FORBIDDEN");
            }
        }
        assertThat(reload(target).getFullName()).as("không bị sửa").isEqualTo("Người Dùng target");
        assertThat(status(getDetail(bearer(admin()), target.getId()))).isEqualTo(200);
    }

    // --- Chi tiết ---

    @Test
    void detailShowsTheProfileAddressAndOrderStatistics() throws Exception {
        User target = newUser("an", Role.CUSTOMER, true);
        target.setPhone("0912345678");
        target.setAddress("12 Lê Lợi, Quận 1");
        users.saveAndFlush(target);
        User other = newUser("khac", Role.CUSTOMER, true);
        insertOrder(target, "COMPLETED", 500_000, 30_000);   // 530.000
        insertOrder(target, "COMPLETED", 200_000, 0);        // 200.000
        insertOrder(target, "PENDING", 100_000, 0);
        insertOrder(target, "CANCELLED", 150_000, 0);
        insertOrder(target, "RETURNED", 300_000, 0);         // đã hoàn trả: không tính chi tiêu
        insertOrder(other, "COMPLETED", 999_000, 0);         // của người khác: không được tính

        MvcResult result = getDetail(bearer(admin()), target.getId());

        assertThat(status(result)).isEqualTo(200);
        JsonNode data = json(result).at("/data");
        assertThat(data.get("id").asLong()).isEqualTo(target.getId());
        assertThat(data.get("email").asText()).isEqualTo(target.getEmail());
        assertThat(data.get("fullName").asText()).isEqualTo("Người Dùng an");
        assertThat(data.get("phone").asText()).isEqualTo("0912345678");
        assertThat(data.get("address").asText()).isEqualTo("12 Lê Lợi, Quận 1");
        assertThat(data.get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(data.get("isActive").asBoolean()).isTrue();
        assertThat(data.get("emailVerified").asBoolean()).isTrue();
        assertThat(data.get("createdAt").asText()).endsWith("+07:00");
        assertThat(data.get("orderCount").asLong()).as("mọi đơn của người này").isEqualTo(5);
        assertThat(data.get("completedOrderCount").asLong()).isEqualTo(2);
        assertThat(data.get("totalSpent").decimalValue()).as("chỉ đơn COMPLETED").isEqualByComparingTo("730000");
        assertThat(data.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "email", "fullName", "phone", "address", "role",
                "isActive", "emailVerified", "createdAt", "orderCount", "completedOrderCount", "totalSpent");
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(raw).doesNotContain("$2").doesNotContain("passwordHash").doesNotContain("otp").doesNotContain("tokenVersion");
    }

    @Test
    void aUserWithoutOrdersHasZeroStatistics() throws Exception {
        User target = newUser("moi", Role.CUSTOMER, true);

        JsonNode data = json(getDetail(bearer(admin()), target.getId())).at("/data");

        assertThat(data.get("orderCount").asLong()).isZero();
        assertThat(data.get("completedOrderCount").asLong()).isZero();
        assertThat(data.get("totalSpent").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void anUnknownUserIsNotFound() throws Exception {
        String token = bearer(admin());

        MvcResult missing = getDetail(token, 999_999_999L);
        MvcResult missingPatch = patchUser(token, 999_999_999L, "{\"fullName\":\"Tên Mới\"}");
        MvcResult notANumber = getDetail(token, "abc");

        assertThat(status(missing)).isEqualTo(404);
        assertThat(errorCode(missing)).isEqualTo("NOT_FOUND");
        assertThat(status(missingPatch)).isEqualTo(404);
        assertThat(status(notANumber)).isEqualTo(400);
    }

    // --- Sửa tên / SĐT ---

    @Test
    void patchChangesOnlyTheFieldsThatAreSent() throws Exception {
        User target = newUser("an", Role.CUSTOMER, true);
        target.setPhone("0912345678");
        target.setAddress("Địa chỉ cũ");
        users.saveAndFlush(target);
        String token = bearer(admin());

        MvcResult nameOnly = patchUser(token, target.getId(), "{\"fullName\":\"  Nguyễn Văn An  \"}");
        assertThat(status(nameOnly)).isEqualTo(200);
        assertThat(reload(target).getFullName()).as("đã trim").isEqualTo("Nguyễn Văn An");
        assertThat(reload(target).getPhone()).as("không gửi phone = giữ nguyên").isEqualTo("0912345678");

        patchUser(token, target.getId(), "{\"phone\":null,\"role\":null,\"isActive\":null}");
        assertThat(reload(target).getPhone()).as("null = giữ nguyên").isEqualTo("0912345678");
        assertThat(reload(target).getRole()).isEqualTo(Role.CUSTOMER);

        MvcResult clear = patchUser(token, target.getId(), "{\"phone\":\"\"}");
        assertThat(status(clear)).isEqualTo(200);
        assertThat(json(clear).at("/data/phone").isNull()).isTrue();
        assertThat(reload(target).getPhone()).as("chuỗi rỗng = xoá").isNull();

        patchUser(token, target.getId(), "{\"phone\":\"0987654321\"}");
        assertThat(reload(target).getPhone()).isEqualTo("0987654321");
        assertThat(reload(target).getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(reload(target).getAddress()).as("địa chỉ không sửa được qua đây").isEqualTo("Địa chỉ cũ");
        assertThat(status(patchUser(token, target.getId(), "{}"))).as("body rỗng không làm gì").isEqualTo(200);
    }

    @Test
    void emailPasswordAndOtherFieldsInTheBodyAreIgnored() throws Exception {
        User target = newUser("an", Role.CUSTOMER, true);
        String originalEmail = target.getEmail();
        String originalHash = target.getPasswordHash();

        MvcResult result = patchUser(bearer(admin()), target.getId(),
                "{\"fullName\":\"Tên Mới\",\"email\":\"ke-tan-cong@evil.example\",\"passwordHash\":\"x\",\"password\":\"Hacked123\","
                        + "\"tokenVersion\":99,\"emailVerified\":false,\"address\":\"Nơi khác\",\"id\":1}");

        assertThat(status(result)).isEqualTo(200);
        User saved = reload(target);
        assertThat(saved.getFullName()).isEqualTo("Tên Mới");
        assertThat(saved.getEmail()).isEqualTo(originalEmail);
        assertThat(saved.getPasswordHash()).isEqualTo(originalHash);
        assertThat(saved.getTokenVersion()).isZero();
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.getAddress()).isNull();
        assertThat(saved.getId()).isEqualTo(target.getId());
    }

    @Test
    void invalidValuesAreRejectedAndNothingChanges() throws Exception {
        User target = newUser("an", Role.CUSTOMER, true);
        String token = bearer(admin());

        for (String body : List.of("{\"fullName\":\"A\"}", "{\"fullName\":\" A \"}", "{\"fullName\":\"" + "x".repeat(101) + "\"}",
                "{\"phone\":\"0912\"}", "{\"phone\":\"84912345678\"}", "{\"role\":\"HACKER\"}", "{\"isActive\":\"maybe\"}")) {
            MvcResult result = patchUser(token, target.getId(), body);
            assertThat(status(result)).as(body).isEqualTo(400);
            assertThat(errorCode(result)).as(body).isEqualTo("VALIDATION_ERROR");
        }
        User saved = reload(target);
        assertThat(saved.getFullName()).isEqualTo("Người Dùng an");
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getTokenVersion()).isZero();
    }

    // --- Đổi quyền / khoá: token_version ---

    @Test
    void changingTheRoleSignsTheUserOutImmediatelyAndTheNewRoleTakesEffect() throws Exception {
        User target = newUser("an", Role.CUSTOMER, true);
        String oldToken = bearer(target);
        assertThat(status(me(oldToken))).isEqualTo(200);

        MvcResult result = patchUser(bearer(admin()), target.getId(), "{\"role\":\"EMPLOYEE\"}");

        assertThat(status(result)).isEqualTo(200);
        assertThat(json(result).at("/data/role").asText()).isEqualTo("EMPLOYEE");
        assertThat(reload(target).getTokenVersion()).isEqualTo(1);
        MvcResult oldTokenAfter = me(oldToken);
        assertThat(status(oldTokenAfter)).as("token cũ bị từ chối ngay").isEqualTo(401);
        assertThat(errorCode(oldTokenAfter)).isEqualTo("UNAUTHORIZED");

        String newToken = "Bearer " + json(login(target)).at("/data/accessToken").asText();
        assertThat(status(send(get("/api/admin/orders"), newToken))).as("nhân viên vào được khu đơn hàng").isEqualTo(200);
        assertThat(status(send(get("/api/admin/users"), newToken))).as("nhưng không vào được khu người dùng").isEqualTo(403);
    }

    @Test
    void lockingSignsTheUserOutAndBlocksLoginUntilUnlocked() throws Exception {
        User target = newUser("an", Role.CUSTOMER, true);
        String oldToken = bearer(target);
        String adminToken = bearer(admin());

        MvcResult lock = patchUser(adminToken, target.getId(), "{\"isActive\":false}");

        assertThat(status(lock)).isEqualTo(200);
        assertThat(json(lock).at("/data/isActive").asBoolean()).isFalse();
        assertThat(status(me(oldToken))).as("token của người bị khoá bị từ chối ngay").isEqualTo(401);
        MvcResult blocked = login(target);
        assertThat(status(blocked)).isEqualTo(403);
        assertThat(errorCode(blocked)).isEqualTo("ACCOUNT_DISABLED");
        assertThat(reload(target).getTokenVersion()).isEqualTo(1);

        assertThat(status(patchUser(adminToken, target.getId(), "{\"isActive\":true}"))).isEqualTo(200);
        assertThat(status(login(target))).as("mở khoá thì đăng nhập lại được").isEqualTo(200);
        assertThat(reload(target).getTokenVersion()).as("mở khoá cũng là một lần đổi").isEqualTo(2);
    }

    @Test
    void sendingTheSameRoleAndStateOrOnlyNameAndPhoneKeepsTheSession() throws Exception {
        User target = newUser("an", Role.CUSTOMER, true);
        String token = bearer(target);
        String adminToken = bearer(admin());

        patchUser(adminToken, target.getId(), "{\"role\":\"CUSTOMER\",\"isActive\":true}");
        patchUser(adminToken, target.getId(), "{\"fullName\":\"Tên Mới\",\"phone\":\"0912345678\"}");

        assertThat(reload(target).getTokenVersion()).as("không đổi role / trạng thái thì không tăng").isZero();
        assertThat(status(me(token))).as("phiên vẫn dùng được").isEqualTo(200);
    }

    @Test
    void changingNameAndRoleTogetherAppliesBothAndBumpsOnce() throws Exception {
        User target = newUser("an", Role.CUSTOMER, true);

        MvcResult result = patchUser(bearer(admin()), target.getId(), "{\"fullName\":\"Tên Mới\",\"role\":\"EMPLOYEE\",\"isActive\":false}");

        assertThat(status(result)).isEqualTo(200);
        User saved = reload(target);
        assertThat(saved.getFullName()).isEqualTo("Tên Mới");
        assertThat(saved.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void anAdminCanDemoteOrLockAnotherAdminAndTheirActorSessionSurvives() throws Exception {
        User other = newUser("admin2", Role.ADMIN, true);
        String actorToken = bearer(admin());
        String otherToken = bearer(other);

        assertThat(status(patchUser(actorToken, other.getId(), "{\"role\":\"CUSTOMER\"}"))).isEqualTo(200);

        assertThat(status(me(otherToken))).as("admin bị hạ quyền bị đăng xuất").isEqualTo(401);
        assertThat(status(me(actorToken))).as("phiên của người thao tác không bị ảnh hưởng").isEqualTo(200);
        assertThat(reload(admin()).getTokenVersion()).isZero();
    }

    // --- Tự sửa chính mình ---

    @Test
    void anAdminCannotLockOrChangeTheRoleOfThemselves() throws Exception {
        User me = admin();
        String token = bearer(me);

        MvcResult lockSelf = patchUser(token, me.getId(), "{\"isActive\":false}");
        MvcResult demoteSelf = patchUser(token, me.getId(), "{\"role\":\"CUSTOMER\"}");
        MvcResult mixed = patchUser(token, me.getId(), "{\"fullName\":\"Tên Mới\",\"role\":\"EMPLOYEE\"}");

        for (MvcResult result : List.of(lockSelf, demoteSelf, mixed)) {
            assertThat(status(result)).isEqualTo(400);
            assertThat(errorCode(result)).isEqualTo("CANNOT_MODIFY_SELF");
        }
        User saved = reload(me);
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getFullName()).as("yêu cầu bị từ chối trọn gói, tên cũng không đổi").isEqualTo("Người Dùng admin");
        assertThat(saved.getTokenVersion()).isZero();
        assertThat(status(me(token))).isEqualTo(200);
    }

    @Test
    void anAdminMayEditTheirOwnNameAndPhoneAndRepeatTheirCurrentRole() throws Exception {
        User me = admin();

        MvcResult result = patchUser(bearer(me), me.getId(),
                "{\"fullName\":\"Quản Trị Mới\",\"phone\":\"0912345678\",\"role\":\"ADMIN\",\"isActive\":true}");

        assertThat(status(result)).isEqualTo(200);
        assertThat(reload(me).getFullName()).isEqualTo("Quản Trị Mới");
        assertThat(reload(me).getPhone()).isEqualTo("0912345678");
        assertThat(reload(me).getTokenVersion()).isZero();
    }
}
