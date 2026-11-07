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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Quên / đặt lại mật khẩu (FR-03, N3-04) từ đầu đến cuối: PostgreSQL thật, máy chủ SMTP giả (GreenMail, cổng 3025) để đọc
 * mã OTP từ thư, đồng hồ chỉnh tay để kiểm hạn 10 phút / giới hạn gửi lại 60 giây không cần sleep. Không dùng @Transactional
 * ở lớp test vì OtpService.verify chạy giao dịch riêng; mỗi test tự dọn user của mình.
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
class PasswordResetIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-26T01:00:00Z");
    private static final String OLD_PASSWORD = "MatKhauCu1";
    private static final String NEW_PASSWORD = "MatKhauMoi9";
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
    }

    @AfterEach
    void cleanUp() {
        createdEmails.forEach(email -> users.findByEmail(email).ifPresent(users::delete));
        createdEmails.clear();
    }

    /** Tạo trực tiếp một tài khoản (đã hoặc chưa xác thực email) với mật khẩu cũ. */
    private String newUser(boolean emailVerified) {
        String email = "rst-" + UUID.randomUUID() + "@campvivo.vn";
        User user = new User(email, passwordEncoder.encode(OLD_PASSWORD), "Nguyễn Văn An");
        user.setEmailVerified(emailVerified);
        users.saveAndFlush(user);
        createdEmails.add(email);
        return email;
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    private MvcResult postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andReturn();
    }

    private MvcResult forgot(String email) throws Exception {
        return postJson("/api/auth/forgot-password", Map.of("email", email));
    }

    private MvcResult reset(String email, String code, String newPassword) throws Exception {
        return postJson("/api/auth/reset-password", Map.of("email", email, "code", code, "newPassword", newPassword));
    }

    private MvcResult login(String email, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("email", email, "password", password));
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

    // --- Quên mật khẩu ---

    @Test
    void forgotPasswordEmailsAResetCodeToAnExistingAccount() throws Exception {
        String email = newUser(true);

        MvcResult result = forgot(email);

        assertThat(status(result)).isEqualTo(200);
        assertThat(json(result).at("/success").asBoolean()).isTrue();
        latestCode(1);
        assertThat(mails()[0].getSubject()).isEqualTo("Mã đặt lại mật khẩu Campvivo");
        assertThat(user(email).getOtpPurpose()).isEqualTo(OtpPurpose.RESET_PASSWORD);
        assertThat(user(email).getOtpCodeHash()).as("chỉ lưu bản băm").doesNotContain(latestCode(1));
    }

    @Test
    void forgotPasswordForAnUnknownEmailIsOkAndSendsNothing() throws Exception {
        MvcResult result = forgot("khong-co-" + UUID.randomUUID() + "@campvivo.vn");

        assertThat(status(result)).isEqualTo(200);
        assertThat(mails()).isEmpty();
    }

    @Test
    void forgotPasswordIgnoresLetterCaseOfTheEmail() throws Exception {
        String email = newUser(true);

        assertThat(status(forgot(email.toUpperCase()))).isEqualTo(200);

        latestCode(1);
    }

    @Test
    void forgotPasswordForADisabledAccountIsOkAndSendsNothing() throws Exception {
        String email = newUser(true);
        User user = user(email);
        user.setActive(false);
        users.saveAndFlush(user);

        MvcResult result = forgot(email);

        assertThat(status(result)).isEqualTo(200);
        assertThat(mails()).isEmpty();
        assertThat(user(email).getOtpCodeHash()).isNull();
    }

    @Test
    void forgotPasswordTooSoonIsStillOkButSendsNoSecondMailAndKeepsTheFirstCode() throws Exception {
        String email = newUser(true);
        forgot(email);
        String code = latestCode(1);
        String hashBefore = user(email).getOtpCodeHash();
        clock.advance(Duration.ofSeconds(30));

        MvcResult again = forgot(email);

        assertThat(status(again)).as("không lộ email có tồn tại").isEqualTo(200);
        assertThat(mails()).hasSize(1);
        assertThat(user(email).getOtpCodeHash()).isEqualTo(hashBefore);
        assertThat(status(reset(email, code, NEW_PASSWORD))).as("mã đầu vẫn dùng được").isEqualTo(200);
    }

    @Test
    void forgotPasswordAfterTheIntervalIssuesANewCode() throws Exception {
        String email = newUser(true);
        forgot(email);
        String first = latestCode(1);
        clock.advance(Duration.ofSeconds(60));

        assertThat(status(forgot(email))).isEqualTo(200);

        String second = latestCode(2);
        if (!second.equals(first)) {
            assertThat(errorCode(reset(email, first, NEW_PASSWORD))).isEqualTo("INVALID_OTP");
        }
        assertThat(status(reset(email, second, NEW_PASSWORD))).isEqualTo(200);
    }

    @Test
    void forgotPasswordReportsAFailureToSendTheMail() throws Exception {
        String email = newUser(true);
        greenMail.stop();

        MvcResult result = forgot(email);

        assertThat(status(result)).isEqualTo(502);
        assertThat(errorCode(result)).isEqualTo("EMAIL_SEND_FAILED");
        assertThat(user(email).getOtpCodeHash()).as("rollback: không lưu mã chưa từng gửi được").isNull();
    }

    // --- Đặt lại mật khẩu ---

    @Test
    void resetPasswordChangesThePasswordAndLogsEveryOldSessionOut() throws Exception {
        String email = newUser(true);
        String oldToken = json(login(email, OLD_PASSWORD)).at("/data/accessToken").asText();
        assertThat(oldToken).isNotBlank();
        int versionBefore = user(email).getTokenVersion();
        forgot(email);
        String code = latestCode(1);

        MvcResult result = reset(email, code, NEW_PASSWORD);

        assertThat(status(result)).isEqualTo(200);
        assertThat(json(result).at("/success").asBoolean()).isTrue();
        assertThat(json(result).at("/data").isNull()).as("không đăng nhập luôn, không trả token").isTrue();

        assertThat(status(login(email, NEW_PASSWORD))).as("mật khẩu mới đăng nhập được").isEqualTo(200);
        MvcResult oldPassword = login(email, OLD_PASSWORD);
        assertThat(status(oldPassword)).isEqualTo(401);
        assertThat(errorCode(oldPassword)).isEqualTo("INVALID_CREDENTIALS");

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        User after = user(email);
        assertThat(after.getTokenVersion()).isEqualTo(versionBefore + 1);
        assertThat(after.getOtpCodeHash()).as("OTP bị xoá").isNull();
        assertThat(after.getPasswordHash()).doesNotContain(NEW_PASSWORD);
    }

    @Test
    void resettingAnUnverifiedAccountAlsoVerifiesTheEmail() throws Exception {
        String email = newUser(false);
        assertThat(errorCode(login(email, OLD_PASSWORD))).isEqualTo("EMAIL_NOT_VERIFIED");
        forgot(email);
        String code = latestCode(1);

        assertThat(status(reset(email, code, NEW_PASSWORD))).isEqualTo(200);

        assertThat(user(email).isEmailVerified()).isTrue();
        assertThat(status(login(email, NEW_PASSWORD))).isEqualTo(200);
    }

    @Test
    void theResetCodeCanOnlyBeUsedOnce() throws Exception {
        String email = newUser(true);
        forgot(email);
        String code = latestCode(1);
        assertThat(status(reset(email, code, NEW_PASSWORD))).isEqualTo(200);

        MvcResult replay = reset(email, code, "KhacHan12345");

        assertThat(status(replay)).isEqualTo(400);
        assertThat(errorCode(replay)).isEqualTo("INVALID_OTP");
        assertThat(status(login(email, NEW_PASSWORD))).as("mật khẩu không đổi lần hai").isEqualTo(200);
        assertThat(status(login(email, "KhacHan12345"))).isEqualTo(401);
    }

    @Test
    void aWrongCodeIsRejectedAndFiveWrongGuessesLockTheCodeEvenIfLaterCorrect() throws Exception {
        String email = newUser(true);
        forgot(email);
        String code = latestCode(1);
        String wrong = wrongCodeFor(code);
        int versionBefore = user(email).getTokenVersion();

        for (int i = 0; i < 5; i++) {
            MvcResult attempt = reset(email, wrong, NEW_PASSWORD);
            assertThat(status(attempt)).isEqualTo(400);
            assertThat(errorCode(attempt)).isEqualTo("INVALID_OTP");
        }
        MvcResult locked = reset(email, code, NEW_PASSWORD);

        assertThat(errorCode(locked)).isEqualTo("OTP_TOO_MANY_ATTEMPTS");
        assertThat(status(login(email, OLD_PASSWORD))).as("mật khẩu cũ vẫn nguyên").isEqualTo(200);
        assertThat(user(email).getTokenVersion()).isEqualTo(versionBefore);

        clock.advance(Duration.ofSeconds(60));
        forgot(email);
        assertThat(status(reset(email, latestCode(2), NEW_PASSWORD))).as("gửi lại mã sẽ mở khoá").isEqualTo(200);
    }

    @Test
    void anExpiredCodeIsRejected() throws Exception {
        String email = newUser(true);
        forgot(email);
        String code = latestCode(1);
        clock.advance(Duration.ofMinutes(10));

        MvcResult result = reset(email, code, NEW_PASSWORD);

        assertThat(errorCode(result)).isEqualTo("OTP_EXPIRED");
        assertThat(status(login(email, OLD_PASSWORD))).isEqualTo(200);
    }

    @Test
    void aVerifyEmailCodeCannotBeUsedToResetThePassword() throws Exception {
        String email = newUser(false);
        MvcResult resend = postJson("/api/auth/resend-code", Map.of("email", email, "purpose", "VERIFY_EMAIL"));
        assertThat(status(resend)).isEqualTo(200);
        String verifyCode = latestCode(1);

        MvcResult result = reset(email, verifyCode, NEW_PASSWORD);

        assertThat(status(result)).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("INVALID_OTP");
        assertThat(user(email).isEmailVerified()).isFalse();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, user(email).getPasswordHash())).isTrue();
    }

    @Test
    void anUnknownEmailAndAWrongCodeGiveTheSameAnswer() throws Exception {
        String email = newUser(true);
        forgot(email);
        String code = latestCode(1);

        MvcResult unknown = reset("khong-co-" + UUID.randomUUID() + "@campvivo.vn", code, NEW_PASSWORD);
        MvcResult wrong = reset(email, wrongCodeFor(code), NEW_PASSWORD);

        assertThat(status(unknown)).isEqualTo(status(wrong)).isEqualTo(400);
        assertThat(errorCode(unknown)).isEqualTo(errorCode(wrong)).isEqualTo("INVALID_OTP");
    }

    @Test
    void aWeakNewPasswordIsRejectedWithoutSpendingTheCode() throws Exception {
        String email = newUser(true);
        forgot(email);
        String code = latestCode(1);

        for (String weak : List.of("ngan1", "chicochucai", "123456789")) {
            MvcResult result = reset(email, code, weak);
            assertThat(status(result)).isEqualTo(400);
            assertThat(errorCode(result)).isEqualTo("VALIDATION_ERROR");
            assertThat(json(result).at("/details/fieldErrors/newPassword").asText()).isNotBlank();
        }

        assertThat(user(email).getOtpAttempts()).as("mật khẩu sai không làm mất lượt thử").isZero();
        assertThat(status(reset(email, code, NEW_PASSWORD))).as("mã vẫn dùng được").isEqualTo(200);
    }

    @Test
    void aPasswordOver72BytesIsRejectedWithoutSpendingTheCode() throws Exception {
        String email = newUser(true);
        forgot(email);
        String code = latestCode(1);
        String tooLongInBytes = "ậ".repeat(30) + "1"; // 31 ký tự nhưng 91 byte UTF-8

        MvcResult result = reset(email, code, tooLongInBytes);

        assertThat(status(result)).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("VALIDATION_ERROR");
        assertThat(json(result).at("/details/fieldErrors/newPassword").asText()).isEqualTo("Mật khẩu quá dài");
        assertThat(user(email).getOtpAttempts()).isZero();
        assertThat(status(reset(email, code, NEW_PASSWORD))).isEqualTo(200);
    }

    @Test
    void aMalformedCodeIsAValidationError() throws Exception {
        MvcResult result = reset("a@campvivo.vn", "12ab", NEW_PASSWORD);

        assertThat(status(result)).isEqualTo(400);
        assertThat(errorCode(result)).isEqualTo("VALIDATION_ERROR");
        assertThat(json(result).at("/details/fieldErrors/code").asText()).isEqualTo("Mã xác thực gồm 6 chữ số");
    }

    @Test
    void aDisabledAccountCannotResetItsPassword() throws Exception {
        String email = newUser(true);
        forgot(email);
        String code = latestCode(1);
        User user = user(email);
        user.setActive(false);
        users.saveAndFlush(user);
        int versionBefore = user(email).getTokenVersion();

        MvcResult result = reset(email, code, NEW_PASSWORD);

        assertThat(status(result)).isEqualTo(403);
        assertThat(errorCode(result)).isEqualTo("ACCOUNT_DISABLED");
        assertThat(passwordEncoder.matches(OLD_PASSWORD, user(email).getPasswordHash())).isTrue();
        assertThat(user(email).getTokenVersion()).isEqualTo(versionBefore);
    }

    @Test
    void parallelResetsWithTheSameCodeLetExactlyOneWin() throws Exception {
        String email = newUser(true);
        forgot(email);
        String code = latestCode(1);
        List<String> passwords = List.of("SongSong111", "SongSong222", "SongSong333");
        ExecutorService pool = Executors.newFixedThreadPool(passwords.size());
        try {
            CountDownLatch ready = new CountDownLatch(passwords.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (String password : passwords) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return status(reset(email, code, password));
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }

            assertThat(statuses).containsExactlyInAnyOrder(200, 400, 400);
            long working = passwords.stream().filter(p -> {
                try {
                    return status(login(email, p)) == 200;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).count();
            assertThat(working).as("đúng một mật khẩu mới có hiệu lực").isEqualTo(1);
            assertThat(user(email).getTokenVersion()).as("tăng đúng một lần").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- Khác ---

    @Test
    void thePasswordResetEndpointsAreReachableWithoutAToken() throws Exception {
        assertThat(status(forgot("someone@campvivo.vn"))).isEqualTo(200);
        assertThat(status(reset("someone@campvivo.vn", "123456", NEW_PASSWORD))).isEqualTo(400);
    }
}
