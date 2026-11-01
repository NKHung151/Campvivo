package vn.campvivo.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.campvivo.cart.CartService;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.OtpPurpose;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đăng ký, xác thực email, gửi lại mã (FR-01, N3-02) từ đầu đến cuối: PostgreSQL thật, máy chủ SMTP giả (GreenMail,
 * cổng 3025) để đọc mã OTP từ thư, đồng hồ chỉnh tay để kiểm hạn 10 phút / giới hạn gửi lại 60 giây không cần sleep.
 * Không dùng @Transactional ở lớp test vì OtpService.verify chạy giao dịch riêng; mỗi test tự dọn user của mình.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok",
        "spring.mail.host=localhost",
        "spring.mail.port=3025"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class RegistrationIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-26T01:00:00Z");
    private static final String PASSWORD = "Passw0rd!";
    private static final Pattern CODE_IN_MAIL = Pattern.compile(">(\\d{6})<");

    static class MutableTestClock extends Clock {
        private volatile Instant now = T0;

        void set(Instant instant) { this.now = instant; }
        void advance(Duration duration) { this.now = now.plus(duration); }

        @Override public ZoneId getZone() { return ZoneId.of("Asia/Ho_Chi_Minh"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        MutableTestClock testClock() { return new MutableTestClock(); }
    }

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired MutableTestClock clock;
    @MockBean CartService cartService;

    private final List<String> createdEmails = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clock.set(T0);
        reset(cartService);
    }

    @AfterEach
    void cleanUp() {
        createdEmails.forEach(email -> users.findByEmail(email).ifPresent(users::delete));
        createdEmails.clear();
    }

    private String newEmail() {
        String email = "reg-" + UUID.randomUUID() + "@campvivo.vn";
        createdEmails.add(email);
        return email;
    }

    private MvcResult postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andReturn();
    }

    private MvcResult register(String email, String password, String fullName) throws Exception {
        return postJson("/api/auth/register", Map.of("email", email, "password", password, "fullName", fullName));
    }

    private MvcResult verifyEmail(String email, String code) throws Exception {
        return postJson("/api/auth/verify-email", Map.of("email", email, "code", code));
    }

    private MvcResult resend(String email, String purpose) throws Exception {
        return postJson("/api/auth/resend-code", Map.of("email", email, "purpose", purpose));
    }

    private MvcResult login(String email, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("email", email, "password", password));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    /** Nội dung phản hồi giải mã UTF-8 (MockHttpServletResponse mặc định ISO-8859-1 nên làm hỏng tiếng Việt). */
    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String wrongCodeFor(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    private MimeMessage[] mails() {
        return greenMail.getReceivedMessages();
    }

    /** Chờ thư mới nhất và rút mã 6 số từ nội dung. */
    private String latestCode(int expectedMailCount) throws Exception {
        assertThat(greenMail.waitForIncomingEmail(5000, expectedMailCount)).as("thư OTP phải tới").isTrue();
        MimeMessage[] all = mails();
        Matcher matcher = CODE_IN_MAIL.matcher((String) all[all.length - 1].getContent());
        assertThat(matcher.find()).as("thư phải chứa mã 6 số").isTrue();
        return matcher.group(1);
    }

    // --- Đăng ký ---

    @Test
    void registerCreatesAnUnverifiedCustomerAndEmailsTheCode() throws Exception {
        String email = newEmail();

        MvcResult result = register(email, PASSWORD, "Nguyễn Văn An");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).at("/data/email").asText()).isEqualTo(email);
        assertThat(json(result).at("/success").asBoolean()).isTrue();

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(user.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(user.getOtpPurpose()).isEqualTo(OtpPurpose.VERIFY_EMAIL);
        assertThat(user.getOtpExpiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(10)));

        String code = latestCode(1);
        assertThat(code).matches("\\d{6}");
        assertThat(user.getOtpCodeHash()).doesNotContain(code);
        assertThat(mails()[0].getAllRecipients()[0].toString()).isEqualTo(email);
        verify(cartService, times(1)).createForUser(user.getId());
    }

    @Test
    void registerNeverLeaksPasswordHashOrOtpFields() throws Exception {
        MvcResult result = register(newEmail(), PASSWORD, "An Nguyễn");

        String body = body(result);
        assertThat(body).doesNotContain("$2").doesNotContain("otp").doesNotContain("passwordHash");
    }

    @Test
    void emailIsStoredLowerCased() throws Exception {
        String lower = newEmail();
        String mixed = lower.toUpperCase().replace("@CAMPVIVO.VN", "@Campvivo.VN");

        MvcResult result = register(mixed, PASSWORD, "An Nguyễn");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(result).at("/data/email").asText()).isEqualTo(lower);
        assertThat(users.findByEmail(lower)).isPresent();
    }

    @Test
    void invalidInputGivesFieldErrors() throws Exception {
        String[][] badPasswords = {
                {"abcdefgh", "không có số"}, {"12345678", "không có chữ"}, {"a1", "quá ngắn"},
                {"a1".repeat(33), "quá dài (66 ký tự)"}, {"ắ".repeat(25) + "1", "vượt giới hạn 72 byte của BCrypt"},
        };
        for (String[] bad : badPasswords) {
            MvcResult result = register(newEmail(), bad[0], "An Nguyễn");
            assertThat(result.getResponse().getStatus()).as("mật khẩu " + bad[1]).isEqualTo(400);
            assertThat(json(result).at("/errorCode").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(json(result).at("/details/fieldErrors/password").asText()).as(bad[1]).isNotBlank();
        }

        MvcResult badEmailAndName = register("khong-phai-email", PASSWORD, "A");
        assertThat(badEmailAndName.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(badEmailAndName).at("/details/fieldErrors/email").asText()).isEqualTo("Email không hợp lệ");
        assertThat(json(badEmailAndName).at("/details/fieldErrors/fullName").asText()).contains("2 đến 100");

        MvcResult blankName = register(newEmail(), PASSWORD, "   ");
        assertThat(blankName.getResponse().getStatus()).isEqualTo(400);
        assertThat(mails()).as("đầu vào sai không được gửi thư").isEmpty();
        verify(cartService, never()).createForUser(anyLong());
    }

    @Test
    void fullNameIsTrimmedBeforeTheLengthCheck() throws Exception {
        MvcResult result = register(newEmail(), PASSWORD, " A ");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).at("/details/fieldErrors/fullName").asText()).contains("2 đến 100");
    }

    @Test
    void registeringAnAlreadyVerifiedEmailIsRejectedWithoutChangingTheAccount() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Người Thật");
        verifyEmail(email, latestCode(1));
        String hashBefore = users.findByEmail(email).orElseThrow().getPasswordHash();
        clock.advance(Duration.ofMinutes(5));

        MvcResult again = register(email, "KhacMatKhau9", "Kẻ Mạo Danh");

        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(again).at("/errorCode").asText()).isEqualTo("EMAIL_ALREADY_EXISTS");
        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getPasswordHash()).isEqualTo(hashBefore);
        assertThat(user.getFullName()).isEqualTo("Người Thật");
        assertThat(mails()).as("chỉ thư OTP lần đầu").hasSize(1);
    }

    @Test
    void registeringAnUnverifiedEmailAgainOverwritesTheAccountAndSendsANewCode() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Tên Cũ");
        String firstCode = latestCode(1);
        clock.advance(Duration.ofSeconds(60));

        MvcResult again = register(email, "MatKhauMoi9", "Tên Mới");

        assertThat(again.getResponse().getStatus()).isEqualTo(201);
        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getFullName()).isEqualTo("Tên Mới");
        assertThat(passwordEncoder.matches("MatKhauMoi9", user.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isFalse();
        verify(cartService, times(1)).createForUser(user.getId()); // chỉ lần tạo mới, không tạo lại khi đăng ký lại

        String secondCode = latestCode(2);
        if (!secondCode.equals(firstCode)) {
            assertThat(json(verifyEmail(email, firstCode)).at("/errorCode").asText()).isEqualTo("INVALID_OTP");
        }
        assertThat(verifyEmail(email, secondCode).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void reRegisteringTooSoonIsRejectedAndDoesNotChangeThePendingAccount() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Tên Cũ");
        String hashBefore = users.findByEmail(email).orElseThrow().getPasswordHash();
        clock.advance(Duration.ofSeconds(30));

        MvcResult again = register(email, "MatKhauMoi9", "Tên Mới");

        assertThat(again.getResponse().getStatus()).isEqualTo(429);
        assertThat(json(again).at("/errorCode").asText()).isEqualTo("OTP_RESEND_TOO_SOON");
        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getPasswordHash()).as("bị rollback, không ghi đè").isEqualTo(hashBefore);
        assertThat(user.getFullName()).isEqualTo("Tên Cũ");
        assertThat(mails()).hasSize(1);
    }

    @Test
    void whenTheMailServerIsDownRegistrationFailsAndNoUserIsCreated() throws Exception {
        String email = newEmail();
        greenMail.stop();

        MvcResult result = register(email, PASSWORD, "Nguyễn Văn An");

        assertThat(result.getResponse().getStatus()).isEqualTo(502);
        assertThat(json(result).at("/errorCode").asText()).isEqualTo("EMAIL_SEND_FAILED");
        assertThat(users.findByEmail(email)).as("rollback: không tạo user nếu không gửi được mã").isEmpty();
    }

    @Test
    void parallelRegistrationsOfTheSameEmailCreateExactlyOneAccount() throws Exception {
        String email = newEmail();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return register(email, PASSWORD, "Nguyễn Văn An").getResponse().getStatus();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(users.findByEmail(email)).isPresent();
            assertThat(mails()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- Xác thực email ---

    @Test
    void correctCodeVerifiesTheEmailAndLogsTheUserIn() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Nguyễn Văn An");
        String code = latestCode(1);

        MvcResult result = verifyEmail(email, code);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json(result);
        assertThat(body.at("/data/accessToken").asText()).isNotBlank();
        assertThat(body.at("/data/user/email").asText()).isEqualTo(email);
        assertThat(body.at("/data/user/role").asText()).isEqualTo("CUSTOMER");
        assertThat(body(result)).doesNotContain("$2").doesNotContain("otp");

        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getOtpCodeHash()).isNull();

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + body.at("/data/accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email));
        assertThat(login(email, PASSWORD).getResponse().getStatus()).as("giờ đăng nhập được").isEqualTo(200);
    }

    @Test
    void anUnverifiedAccountCannotLoginYet() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Nguyễn Văn An");

        MvcResult result = login(email, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(result).at("/errorCode").asText()).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void theCodeCanOnlyBeUsedOnce() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Nguyễn Văn An");
        String code = latestCode(1);
        assertThat(verifyEmail(email, code).getResponse().getStatus()).isEqualTo(200);

        MvcResult replay = verifyEmail(email, code);

        assertThat(replay.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(replay).at("/errorCode").asText()).isEqualTo("INVALID_OTP");
    }

    @Test
    void wrongCodeIsRejectedAndFiveWrongGuessesLockTheCodeEvenIfLaterCorrect() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Nguyễn Văn An");
        String code = latestCode(1);
        String wrong = wrongCodeFor(code);

        for (int i = 0; i < 5; i++) {
            MvcResult result = verifyEmail(email, wrong);
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(json(result).at("/errorCode").asText()).isEqualTo("INVALID_OTP");
        }
        MvcResult locked = verifyEmail(email, code);

        assertThat(locked.getResponse().getStatus()).isEqualTo(429);
        assertThat(json(locked).at("/errorCode").asText()).isEqualTo("OTP_TOO_MANY_ATTEMPTS");
        assertThat(users.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();

        clock.advance(Duration.ofSeconds(60));
        assertThat(resend(email, "VERIFY_EMAIL").getResponse().getStatus()).isEqualTo(200);
        assertThat(verifyEmail(email, latestCode(2)).getResponse().getStatus()).as("gửi lại mã mở khoá").isEqualTo(200);
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Nguyễn Văn An");
        String code = latestCode(1);
        clock.advance(Duration.ofMinutes(10));

        MvcResult result = verifyEmail(email, code);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).at("/errorCode").asText()).isEqualTo("OTP_EXPIRED");
    }

    @Test
    void unknownEmailAndWrongCodeGiveTheSameAnswer() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Nguyễn Văn An");
        String wrong = wrongCodeFor(latestCode(1));

        MvcResult wrongCode = verifyEmail(email, wrong);
        MvcResult unknownEmail = verifyEmail("khong-co-" + UUID.randomUUID() + "@campvivo.vn", wrong);

        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(wrongCode.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(unknownEmail)).isEqualTo(body(wrongCode));
    }

    @Test
    void malformedCodeIsAValidationError() throws Exception {
        MvcResult result = verifyEmail(newEmail(), "12ab56");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).at("/errorCode").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(json(result).at("/details/fieldErrors/code").asText()).isEqualTo("Mã xác thực gồm 6 chữ số");
    }

    @Test
    void aDisabledAccountCannotGetATokenByVerifying() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Nguyễn Văn An");
        String code = latestCode(1);
        User user = users.findByEmail(email).orElseThrow();
        user.setActive(false);
        users.saveAndFlush(user);

        MvcResult result = verifyEmail(email, code);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(json(result).at("/errorCode").asText()).isEqualTo("ACCOUNT_DISABLED");
        assertThat(users.findByEmail(email).orElseThrow().isEmailVerified()).isFalse();
    }

    // --- Gửi lại mã ---

    @Test
    void resendIssuesANewCodeForAnUnverifiedAccountButNotTooSoon() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Nguyễn Văn An");

        clock.advance(Duration.ofSeconds(30));
        MvcResult tooSoon = resend(email, "VERIFY_EMAIL");
        assertThat(tooSoon.getResponse().getStatus()).isEqualTo(429);
        assertThat(json(tooSoon).at("/errorCode").asText()).isEqualTo("OTP_RESEND_TOO_SOON");
        assertThat(mails()).hasSize(1);

        clock.advance(Duration.ofSeconds(30));
        MvcResult ok = resend(email, "VERIFY_EMAIL");
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(ok).at("/success").asBoolean()).isTrue();
        assertThat(verifyEmail(email, latestCode(2)).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void resendForUnknownEmailIsSilentlyOkAndSendsNothing() throws Exception {
        MvcResult result = resend("khong-co-" + UUID.randomUUID() + "@campvivo.vn", "VERIFY_EMAIL");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(mails()).isEmpty();
    }

    @Test
    void resendVerifyCodeForAnAlreadyVerifiedAccountSendsNothing() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Nguyễn Văn An");
        verifyEmail(email, latestCode(1));
        clock.advance(Duration.ofMinutes(2));

        MvcResult result = resend(email, "VERIFY_EMAIL");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(mails()).as("chỉ thư OTP lần đăng ký").hasSize(1);
    }

    @Test
    void resendResetPasswordCodeUsesTheResetWording() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Nguyễn Văn An");
        verifyEmail(email, latestCode(1));
        clock.advance(Duration.ofMinutes(2));

        MvcResult result = resend(email, "RESET_PASSWORD");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        latestCode(2);
        assertThat(mails()[1].getSubject()).isEqualTo("Mã đặt lại mật khẩu Campvivo");
        assertThat(users.findByEmail(email).orElseThrow().getOtpPurpose()).isEqualTo(OtpPurpose.RESET_PASSWORD);
    }

    @Test
    void resendForADisabledAccountSendsNothing() throws Exception {
        String email = newEmail();
        register(email, PASSWORD, "Nguyễn Văn An");
        User user = users.findByEmail(email).orElseThrow();
        user.setActive(false);
        users.saveAndFlush(user);
        clock.advance(Duration.ofMinutes(2));

        MvcResult result = resend(email, "VERIFY_EMAIL");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(mails()).hasSize(1);
    }

    @Test
    void unknownPurposeIsAValidationError() throws Exception {
        MvcResult result = resend(newEmail(), "HACK");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(result).at("/errorCode").asText()).isEqualTo("VALIDATION_ERROR");
    }

    // --- Khác ---

    @Test
    void theRegistrationEndpointsAreReachableWithoutAToken() throws Exception {
        mvc.perform(post("/api/auth/resend-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@campvivo.vn\",\"purpose\":\"VERIFY_EMAIL\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("UNAUTHORIZED"))));
    }
}
