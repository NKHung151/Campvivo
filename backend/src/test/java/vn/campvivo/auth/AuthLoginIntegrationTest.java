package vn.campvivo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đăng nhập, đăng xuất và GET /users/me trên PostgreSQL thật (FR-02, N3-03, N3-05 phần xem hồ sơ).
 * Tạo user trực tiếp bằng UserRepository. @Transactional: rollback sau mỗi test; MockMvc cùng thread nên thấy
 * dữ liệu chưa commit. ddl-auto=none vì các bảng module khác chưa có migration.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
@Transactional
class AuthLoginIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    private User saveUser(String email, Role role, boolean verified, boolean active) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Người Dùng");
        user.setRole(role);
        user.setEmailVerified(verified);
        user.setActive(active);
        return users.saveAndFlush(user);
    }

    private String loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password))).andReturn();
    }

    private String bearerFrom(MvcResult loginResult) throws Exception {
        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return "Bearer " + body.at("/data/accessToken").asText();
    }

    // --- Đăng nhập thành công ---

    @Test
    void everyRoleCanLoginAndGetsProfileWithItsRole() throws Exception {
        for (Role role : Role.values()) {
            String email = role.name().toLowerCase() + "@campvivo.vn";
            saveUser(email, role, true, true);

            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email, PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.user.email").value(email))
                    .andExpect(jsonPath("$.data.user.role").value(role.name()));
        }
    }

    @Test
    void loginResponseNeverLeaksPasswordHashOrOtpFields() throws Exception {
        saveUser("safe@campvivo.vn", Role.CUSTOMER, true, true);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("safe@campvivo.vn", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("passwordHash"))))
                .andExpect(content().string(not(containsString("$2a$"))))
                .andExpect(content().string(not(containsString("otp"))))
                .andExpect(content().string(not(containsString("tokenVersion"))));
    }

    @Test
    void emailIsNormalizedOnLogin() throws Exception {
        saveUser("mixed@campvivo.vn", Role.CUSTOMER, true, true);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("  Mixed@Campvivo.VN ", PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void tokenFromLoginWorksOnProtectedEndpoint() throws Exception {
        saveUser("me@campvivo.vn", Role.EMPLOYEE, true, true);
        String bearer = bearerFrom(login("me@campvivo.vn", PASSWORD));

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@campvivo.vn"))
                .andExpect(jsonPath("$.data.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.data.fullName").value("Người Dùng"))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    // --- Lỗi đăng nhập ---

    @Test
    void wrongPasswordAndUnknownEmailGiveIdenticalError() throws Exception {
        saveUser("exists@campvivo.vn", Role.CUSTOMER, true, true);

        MvcResult wrongPassword = login("exists@campvivo.vn", "SaiMatKhau1");
        MvcResult unknownEmail = login("ghost@campvivo.vn", PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownEmail.getResponse().getContentAsString());
        assertThat(objectMapper.readTree(wrongPassword.getResponse().getContentAsString())
                .get("errorCode").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void unverifiedEmailIsReportedOnlyWhenPasswordIsCorrect() throws Exception {
        saveUser("unverified@campvivo.vn", Role.CUSTOMER, false, true);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("unverified@campvivo.vn", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("unverified@campvivo.vn", "SaiMatKhau1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void disabledAccountIsReportedOnlyWhenPasswordIsCorrect() throws Exception {
        saveUser("locked@campvivo.vn", Role.CUSTOMER, true, false);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("locked@campvivo.vn", PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("locked@campvivo.vn", "SaiMatKhau1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void missingFieldsAreValidationErrors() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fieldErrors.email").value("Email không được để trống"))
                .andExpect(jsonPath("$.details.fieldErrors.password").value("Mật khẩu không được để trống"));
    }

    // --- Đăng xuất ---

    @Test
    void logoutInvalidatesTheOldTokenButANewLoginWorks() throws Exception {
        User user = saveUser("bye@campvivo.vn", Role.CUSTOMER, true, true);
        String oldBearer = bearerFrom(login("bye@campvivo.vn", PASSWORD));
        int versionBefore = user.getTokenVersion();

        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, oldBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(users.findById(user.getId()).orElseThrow().getTokenVersion()).isEqualTo(versionBefore + 1);
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, oldBearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        String newBearer = bearerFrom(login("bye@campvivo.vn", PASSWORD));
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, newBearer))
                .andExpect(status().isOk());
    }

    @Test
    void logoutKillsEverySessionOfThatUser() throws Exception {
        saveUser("multi@campvivo.vn", Role.CUSTOMER, true, true);
        String deviceA = bearerFrom(login("multi@campvivo.vn", PASSWORD));
        String deviceB = bearerFrom(login("multi@campvivo.vn", PASSWORD));

        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, deviceA)).andExpect(status().isOk());

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, deviceB))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutOfOneUserDoesNotAffectAnother() throws Exception {
        saveUser("u1@campvivo.vn", Role.CUSTOMER, true, true);
        saveUser("u2@campvivo.vn", Role.CUSTOMER, true, true);
        String u1 = bearerFrom(login("u1@campvivo.vn", PASSWORD));
        String u2 = bearerFrom(login("u2@campvivo.vn", PASSWORD));

        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, u1)).andExpect(status().isOk());

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, u2)).andExpect(status().isOk());
    }

    @Test
    void logoutWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    // --- GET /users/me ---

    @Test
    void profileWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileShowsOptionalFieldsAsNullWhenEmpty() throws Exception {
        saveUser("nofields@campvivo.vn", Role.CUSTOMER, true, true);
        String bearer = bearerFrom(login("nofields@campvivo.vn", PASSWORD));

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.address").doesNotExist());
    }
}
