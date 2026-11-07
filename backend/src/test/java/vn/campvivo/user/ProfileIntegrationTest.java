package vn.campvivo.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import vn.campvivo.auth.JwtService;
import vn.campvivo.support.PostgresTestContainerConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Sửa hồ sơ và đổi mật khẩu (FR-04, N3-05) trên PostgreSQL thật. Không dùng @Transactional ở lớp test vì ca "đổi song song" cần
 * nhiều giao dịch thật; mỗi test tự dọn user của mình.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class ProfileIntegrationTest {

    private static final String PASSWORD = "MatKhauCu1";
    private static final String NEW_PASSWORD = "MatKhauMoi9";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        createdUserIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        createdUserIds.clear();
    }

    // --- Hạ tầng test ---

    private User newUser(Role role) {
        User user = new User("prof-" + UUID.randomUUID() + "@campvivo.vn", passwordEncoder.encode(PASSWORD), "Người Dùng");
        user.setRole(role);
        user.setEmailVerified(true);
        user = users.saveAndFlush(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private User reload(User user) {
        return users.findById(user.getId()).orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.createToken(user);
    }

    private Map<String, Object> profileBody(String fullName, String phone, String address) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", fullName);
        body.put("phone", phone);
        body.put("address", address);
        return body;
    }

    private MvcResult updateProfile(String authorization, Map<String, Object> body) throws Exception {
        var request = put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult changePassword(String authorization, String current, String next) throws Exception {
        var request = put("/api/users/me/password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("currentPassword", current, "newPassword", next)));
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
    }

    private MvcResult me(String authorization) throws Exception {
        return mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, authorization)).andReturn();
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

    // --- Sửa hồ sơ ---

    @Test
    void updatingTheProfileChangesNameAddressAndPhoneAndReturnsTheNewProfile() throws Exception {
        User user = newUser(Role.CUSTOMER);

        MvcResult result = updateProfile(bearer(user), profileBody("Nguyễn Văn An", "0912345678", "12 Lê Lợi, Quận 1"));

        assertThat(status(result)).isEqualTo(200);
        JsonNode data = json(result).at("/data");
        assertThat(data.at("/fullName").asText()).isEqualTo("Nguyễn Văn An");
        assertThat(data.at("/phone").asText()).isEqualTo("0912345678");
        assertThat(data.at("/address").asText()).isEqualTo("12 Lê Lợi, Quận 1");
        assertThat(data.at("/email").asText()).isEqualTo(user.getEmail());
        User saved = reload(user);
        assertThat(saved.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(saved.getPhone()).isEqualTo("0912345678");
        assertThat(saved.getAddress()).isEqualTo("12 Lê Lợi, Quận 1");
        assertThat(json(me(bearer(user))).at("/data/fullName").asText()).as("GET /users/me thấy giá trị mới").isEqualTo("Nguyễn Văn An");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("$2").doesNotContain("otp").doesNotContain("passwordHash");
    }

    @Test
    void valuesAreTrimmedAndBlankPhoneOrAddressClearsTheStoredValue() throws Exception {
        User user = newUser(Role.CUSTOMER);
        user.setPhone("0912345678");
        user.setAddress("Địa chỉ cũ");
        users.saveAndFlush(user);

        MvcResult result = updateProfile(bearer(user), profileBody("  Trần Thị Bình  ", "", "   "));

        assertThat(status(result)).isEqualTo(200);
        User saved = reload(user);
        assertThat(saved.getFullName()).isEqualTo("Trần Thị Bình");
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getAddress()).isNull();
        assertThat(json(result).at("/data/phone").isNull()).isTrue();
    }

    @Test
    void omittingPhoneAndAddressAlsoClearsThem() throws Exception {
        User user = newUser(Role.CUSTOMER);
        user.setPhone("0912345678");
        users.saveAndFlush(user);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Lê Văn Cường");

        assertThat(status(updateProfile(bearer(user), body))).isEqualTo(200);

        assertThat(reload(user).getPhone()).as("PUT thay thế toàn bộ ba trường").isNull();
    }

    @Test
    void invalidInputGivesFieldErrorsAndChangesNothing() throws Exception {
        User user = newUser(Role.CUSTOMER);
        String token = bearer(user);

        MvcResult tooShortName = updateProfile(token, profileBody("A", null, null));
        MvcResult blankName = updateProfile(token, profileBody("   ", null, null));
        MvcResult paddedShortName = updateProfile(token, profileBody(" A ", null, null)); // qua @Size nhưng ngắn sau trim
        MvcResult tooLongName = updateProfile(token, profileBody("x".repeat(101), null, null));

        for (MvcResult result : List.of(tooShortName, blankName, paddedShortName, tooLongName)) {
            assertThat(status(result)).isEqualTo(400);
            assertThat(errorCode(result)).isEqualTo("VALIDATION_ERROR");
            assertThat(json(result).at("/details/fieldErrors/fullName").asText()).isNotBlank();
        }
        for (String phone : List.of("0912", "84912345678", "09123456789", "abcdefghij", "1912345678", " 0912345678")) {
            MvcResult result = updateProfile(token, profileBody("Nguyễn Văn An", phone, null));
            assertThat(status(result)).as("phone=" + phone).isEqualTo(400);
            assertThat(json(result).at("/details/fieldErrors/phone").asText()).isEqualTo("Số điện thoại phải gồm 10 số, bắt đầu bằng 0");
        }
        MvcResult longAddress = updateProfile(token, profileBody("Nguyễn Văn An", null, "x".repeat(501)));
        assertThat(json(longAddress).at("/details/fieldErrors/address").asText()).isNotBlank();
        assertThat(reload(user).getFullName()).isEqualTo("Người Dùng");
    }

    @Test
    void emailRoleAndOtherFieldsInTheBodyAreIgnored() throws Exception {
        User user = newUser(Role.CUSTOMER);
        String originalEmail = user.getEmail();
        Map<String, Object> body = profileBody("Nguyễn Văn An", "0912345678", null);
        body.put("email", "ke-tan-cong@evil.example");
        body.put("role", "ADMIN");
        body.put("active", false);
        body.put("isActive", false);
        body.put("emailVerified", false);
        body.put("tokenVersion", 99);
        body.put("id", 1);

        MvcResult result = updateProfile(bearer(user), body);

        assertThat(status(result)).isEqualTo(200);
        User saved = reload(user);
        assertThat(saved.getEmail()).as("email không sửa được").isEqualTo(originalEmail);
        assertThat(saved.getRole()).as("không tự nâng quyền").isEqualTo(Role.CUSTOMER);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.getTokenVersion()).isZero();
        assertThat(saved.getId()).isEqualTo(user.getId());
        assertThat(json(result).at("/data/role").asText()).isEqualTo("CUSTOMER");
    }

    @Test
    void updatingOneProfileNeverTouchesAnotherUser() throws Exception {
        User alice = newUser(Role.CUSTOMER);
        User bob = newUser(Role.CUSTOMER);

        updateProfile(bearer(alice), profileBody("Alice Mới", "0912345678", "Hà Nội"));

        User untouched = reload(bob);
        assertThat(untouched.getFullName()).isEqualTo("Người Dùng");
        assertThat(untouched.getPhone()).isNull();
    }

    @Test
    void updatingTheProfileDoesNotSignAnyoneOut() throws Exception {
        User user = newUser(Role.CUSTOMER);
        String token = bearer(user);

        updateProfile(token, profileBody("Nguyễn Văn An", null, null));

        assertThat(status(me(token))).as("sửa hồ sơ không làm token hết hiệu lực").isEqualTo(200);
        assertThat(reload(user).getTokenVersion()).isZero();
    }

    @Test
    void staffCanEditTheirOwnProfileToo() throws Exception {
        User employee = newUser(Role.EMPLOYEE);

        MvcResult result = updateProfile(bearer(employee), profileBody("Nhân Viên Mới", "0987654321", null));

        assertThat(status(result)).isEqualTo(200);
        assertThat(reload(employee).getFullName()).isEqualTo("Nhân Viên Mới");
    }

    @Test
    void editingTheProfileOrPasswordRequiresALogin() throws Exception {
        MvcResult noToken = updateProfile(null, profileBody("Nguyễn Văn An", null, null));
        MvcResult badToken = updateProfile("Bearer khong-phai-jwt", profileBody("Nguyễn Văn An", null, null));
        MvcResult noTokenPassword = changePassword(null, PASSWORD, NEW_PASSWORD);

        for (MvcResult result : List.of(noToken, badToken, noTokenPassword)) {
            assertThat(status(result)).isEqualTo(401);
            assertThat(errorCode(result)).isEqualTo("UNAUTHORIZED");
        }
    }

    // --- Đổi mật khẩu ---

    @Test
    void changingThePasswordSignsEveryOldSessionOutAndTheNewPasswordWorks() throws Exception {
        User user = newUser(Role.CUSTOMER);
        String oldToken = bearer(user);
        int versionBefore = user.getTokenVersion();
        String hashBefore = user.getPasswordHash();

        MvcResult result = changePassword(oldToken, PASSWORD, NEW_PASSWORD);

        assertThat(status(result)).isEqualTo(200);
        assertThat(json(result).at("/success").asBoolean()).isTrue();
        MvcResult oldTokenAfter = me(oldToken);
        assertThat(status(oldTokenAfter)).as("token cũ hết hiệu lực").isEqualTo(401);
        assertThat(errorCode(oldTokenAfter)).isEqualTo("UNAUTHORIZED");
        assertThat(status(login(user.getEmail(), NEW_PASSWORD))).as("mật khẩu mới đăng nhập được").isEqualTo(200);
        MvcResult oldPassword = login(user.getEmail(), PASSWORD);
        assertThat(status(oldPassword)).isEqualTo(401);
        assertThat(errorCode(oldPassword)).isEqualTo("INVALID_CREDENTIALS");
        User saved = reload(user);
        assertThat(saved.getTokenVersion()).isEqualTo(versionBefore + 1);
        assertThat(saved.getPasswordHash()).isNotEqualTo(hashBefore).doesNotContain(NEW_PASSWORD);
    }

    @Test
    void aWrongCurrentPasswordIsRejectedAndNothingChanges() throws Exception {
        User user = newUser(Role.CUSTOMER);
        String token = bearer(user);
        String hashBefore = user.getPasswordHash();

        MvcResult result = changePassword(token, "SaiMatKhau9", NEW_PASSWORD);

        assertThat(status(result)).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("INVALID_CURRENT_PASSWORD");
        User saved = reload(user);
        assertThat(saved.getPasswordHash()).isEqualTo(hashBefore);
        assertThat(saved.getTokenVersion()).isZero();
        assertThat(status(me(token))).as("token vẫn dùng được").isEqualTo(200);
        assertThat(status(login(user.getEmail(), PASSWORD))).isEqualTo(200);
    }

    @Test
    void aWeakOrTooLongNewPasswordIsRejectedBeforeAnythingIsChanged() throws Exception {
        User user = newUser(Role.CUSTOMER);
        String token = bearer(user);
        String hashBefore = user.getPasswordHash();

        for (String weak : List.of("ngan1", "chicochucai", "123456789", "a1".repeat(33))) {
            MvcResult result = changePassword(token, PASSWORD, weak);
            assertThat(status(result)).as(weak).isEqualTo(400);
            assertThat(errorCode(result)).isEqualTo("VALIDATION_ERROR");
            assertThat(json(result).at("/details/fieldErrors/newPassword").asText()).isNotBlank();
        }
        MvcResult tooManyBytes = changePassword(token, PASSWORD, "ậ".repeat(30) + "1"); // 31 ký tự nhưng 91 byte UTF-8
        assertThat(status(tooManyBytes)).isEqualTo(400);
        assertThat(json(tooManyBytes).at("/details/fieldErrors/newPassword").asText()).isEqualTo("Mật khẩu quá dài");

        User saved = reload(user);
        assertThat(saved.getPasswordHash()).isEqualTo(hashBefore);
        assertThat(saved.getTokenVersion()).isZero();
    }

    @Test
    void aBlankCurrentPasswordIsAValidationError() throws Exception {
        User user = newUser(Role.CUSTOMER);

        MvcResult result = changePassword(bearer(user), "   ", NEW_PASSWORD);

        assertThat(status(result)).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("VALIDATION_ERROR");
        assertThat(json(result).at("/details/fieldErrors/currentPassword").asText()).isNotBlank();
    }

    @Test
    void theNewPasswordMayEqualTheCurrentOne() throws Exception {
        User user = newUser(Role.CUSTOMER);

        MvcResult result = changePassword(bearer(user), PASSWORD, PASSWORD);

        assertThat(status(result)).isEqualTo(200);
        assertThat(reload(user).getTokenVersion()).isEqualTo(1);
        assertThat(status(login(user.getEmail(), PASSWORD))).isEqualTo(200);
    }

    @Test
    void staffCanChangeTheirOwnPassword() throws Exception {
        User admin = newUser(Role.ADMIN);

        assertThat(status(changePassword(bearer(admin), PASSWORD, NEW_PASSWORD))).isEqualTo(200);
        assertThat(status(login(admin.getEmail(), NEW_PASSWORD))).isEqualTo(200);
    }

    @Test
    void theUpdateOnlyWritesWhileThePasswordIsStillTheOneThatWasConfirmed() {
        User user = newUser(Role.CUSTOMER);
        String confirmedHash = user.getPasswordHash();

        // Một lần đổi / đặt lại mật khẩu khác chen vào sau khi người dùng xác nhận mật khẩu cũ.
        assertThat(users.changePassword(user.getId(), confirmedHash, passwordEncoder.encode("NguoiKhac111"))).isEqualTo(1);
        String hashAfterFirst = reload(user).getPasswordHash();

        int rows = users.changePassword(user.getId(), confirmedHash, passwordEncoder.encode("GhiDe999999"));

        assertThat(rows).as("mật khẩu đã đổi nên bản xác nhận cũ không còn hợp lệ").isZero();
        User saved = reload(user);
        assertThat(saved.getPasswordHash()).as("không bị ghi đè").isEqualTo(hashAfterFirst);
        assertThat(saved.getTokenVersion()).as("chỉ tăng một lần").isEqualTo(1);
    }

    @Test
    void parallelChangesFromTheSameCurrentPasswordLetExactlyOneWin() throws Exception {
        User user = newUser(Role.CUSTOMER);
        String token = bearer(user);
        List<String> candidates = List.of("SongSong111", "SongSong222", "SongSong333");
        ExecutorService pool = Executors.newFixedThreadPool(candidates.size());
        try {
            CountDownLatch ready = new CountDownLatch(candidates.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (String candidate : candidates) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return changePassword(token, PASSWORD, candidate);
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get();
                statuses.add(status(result));
                if (status(result) != 200) {
                    assertThat(errorCode(result)).isEqualTo("INVALID_CURRENT_PASSWORD");
                }
            }

            assertThat(statuses).containsExactlyInAnyOrder(200, 400, 400);
            long working = candidates.stream().filter(candidate -> {
                try {
                    return status(login(user.getEmail(), candidate)) == 200;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).count();
            assertThat(working).as("đúng một mật khẩu mới có hiệu lực").isEqualTo(1);
            assertThat(reload(user).getTokenVersion()).as("tăng đúng một lần").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
