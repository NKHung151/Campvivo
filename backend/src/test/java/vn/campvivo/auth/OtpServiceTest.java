package vn.campvivo.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OTP (BR-11) trên PostgreSQL thật. KHÔNG dùng @Transactional ở lớp test: verify() chạy giao dịch riêng
 * (REQUIRES_NEW) nên dữ liệu phải được commit; mỗi test tự dọn user của mình. Đồng hồ giả để kiểm hạn dùng và gửi lại
 * mà không cần sleep.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class OtpServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-26T01:00:00Z");

    /** Đồng hồ chỉnh tay (class lồng riêng; hạ tầng test chung MutableClock là việc của F-04). */
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

    @Autowired OtpService otp;
    @Autowired UserRepository users;
    @Autowired MutableTestClock clock;
    @Autowired PlatformTransactionManager transactionManager;

    private final List<Long> createdUsers = new ArrayList<>();

    @BeforeEach
    void resetClock() {
        clock.set(T0);
    }

    @AfterEach
    void cleanUp() {
        createdUsers.forEach(users::deleteById);
        createdUsers.clear();
    }

    private User newUser() {
        User user = users.saveAndFlush(new User("otp-" + UUID.randomUUID() + "@campvivo.vn", "hash", "Người Dùng"));
        createdUsers.add(user.getId());
        return user;
    }

    /** Cấp OTP và lưu như người gọi thật sẽ làm (entity được lưu trong giao dịch của họ). */
    private String issueAndSave(User user, OtpPurpose purpose) {
        String code = otp.issue(user, purpose);
        users.saveAndFlush(user);
        return code;
    }

    private User reload(User user) {
        return users.findById(user.getId()).orElseThrow();
    }

    private static ErrorCode errorOf(Runnable action) {
        try {
            action.run();
        } catch (BusinessException e) {
            return ErrorCode.valueOf(e.getErrorCode());
        }
        return null;
    }

    private ErrorCode verifyError(User user, OtpPurpose purpose, String code) {
        return errorOf(() -> otp.verify(user.getId(), purpose, code));
    }

    private static String wrongCodeFor(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    // --- Cấp mã ---

    @Test
    void issueStoresOnlyBcryptHashWithPurposeExpiryAndSendTime() {
        User user = newUser();

        String code = issueAndSave(user, OtpPurpose.VERIFY_EMAIL);

        assertThat(code).matches("\\d{6}");
        User saved = reload(user);
        assertThat(saved.getOtpCodeHash()).startsWith("$2").isNotEqualTo(code).doesNotContain(code);
        assertThat(saved.getOtpPurpose()).isEqualTo(OtpPurpose.VERIFY_EMAIL);
        assertThat(saved.getOtpExpiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
        assertThat(saved.getOtpLastSentAt()).isEqualTo(T0);
        assertThat(saved.getOtpAttempts()).isZero();
    }

    @Test
    void generatedCodesAreSixDigitsIncludingLeadingZeros() {
        User user = newUser();
        boolean sawLeadingZero = false;
        for (int i = 0; i < 300; i++) {
            user.setOtpLastSentAt(null); // bỏ qua giới hạn gửi lại để sinh nhiều mã
            String code = otp.issue(user, OtpPurpose.VERIFY_EMAIL);
            assertThat(code).hasSize(6).containsOnlyDigits();
            sawLeadingZero |= code.startsWith("0");
        }
        assertThat(sawLeadingZero).as("300 mã ngẫu nhiên phải có ít nhất một mã bắt đầu bằng 0").isTrue();
    }

    // --- Kiểm mã ---

    @Test
    void correctCodeVerifiesAndClearsTheOtp() {
        User user = newUser();
        String code = issueAndSave(user, OtpPurpose.VERIFY_EMAIL);

        otp.verify(user.getId(), OtpPurpose.VERIFY_EMAIL, code);

        User saved = reload(user);
        assertThat(saved.getOtpCodeHash()).isNull();
        assertThat(saved.getOtpPurpose()).isNull();
        assertThat(saved.getOtpExpiresAt()).isNull();
        assertThat(saved.getOtpAttempts()).isZero();
        assertThat(saved.getOtpLastSentAt()).as("giữ lại để vẫn giới hạn tần suất gửi").isEqualTo(T0);
    }

    @Test
    void codeCanOnlyBeUsedOnce() {
        User user = newUser();
        String code = issueAndSave(user, OtpPurpose.RESET_PASSWORD);
        otp.verify(user.getId(), OtpPurpose.RESET_PASSWORD, code);

        assertThat(verifyError(user, OtpPurpose.RESET_PASSWORD, code)).isEqualTo(ErrorCode.INVALID_OTP);
    }

    @Test
    void wrongCodeIsInvalidAndTheAttemptIsPersisted() {
        User user = newUser();
        String code = issueAndSave(user, OtpPurpose.VERIFY_EMAIL);

        assertThat(verifyError(user, OtpPurpose.VERIFY_EMAIL, wrongCodeFor(code))).isEqualTo(ErrorCode.INVALID_OTP);

        assertThat(reload(user).getOtpAttempts()).isEqualTo(1);
    }

    @Test
    void attemptCountSurvivesARollbackOfTheCallersTransaction() {
        User user = newUser();
        String code = issueAndSave(user, OtpPurpose.VERIFY_EMAIL);

        new TransactionTemplate(transactionManager).execute(status -> {
            assertThat(verifyError(user, OtpPurpose.VERIFY_EMAIL, wrongCodeFor(code))).isEqualTo(ErrorCode.INVALID_OTP);
            status.setRollbackOnly(); // người gọi (vd. service đăng ký) bị rollback vì ngoại lệ
            return null;
        });

        assertThat(reload(user).getOtpAttempts()).as("lượt đoán không được bị xoá theo rollback").isEqualTo(1);
    }

    @Test
    void afterMaxAttemptsEvenTheCorrectCodeIsRejected() {
        User user = newUser();
        String code = issueAndSave(user, OtpPurpose.VERIFY_EMAIL);
        String wrong = wrongCodeFor(code);
        for (int i = 0; i < 5; i++) {
            assertThat(verifyError(user, OtpPurpose.VERIFY_EMAIL, wrong)).isEqualTo(ErrorCode.INVALID_OTP);
        }

        assertThat(verifyError(user, OtpPurpose.VERIFY_EMAIL, wrong)).isEqualTo(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
        assertThat(verifyError(user, OtpPurpose.VERIFY_EMAIL, code)).isEqualTo(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
        assertThat(reload(user).getOtpAttempts()).isEqualTo(5);
    }

    @Test
    void expiredCodeIsRejectedAtExactlyTheTtlButAcceptedJustBefore() {
        User user = newUser();
        String code = issueAndSave(user, OtpPurpose.VERIFY_EMAIL);

        clock.set(T0.plus(Duration.ofMinutes(10)));
        assertThat(verifyError(user, OtpPurpose.VERIFY_EMAIL, code)).isEqualTo(ErrorCode.OTP_EXPIRED);
        assertThat(reload(user).getOtpAttempts()).as("hết hạn không tính lượt thử").isZero();

        clock.set(T0.plus(Duration.ofMinutes(10)).minusSeconds(1));
        otp.verify(user.getId(), OtpPurpose.VERIFY_EMAIL, code);
    }

    @Test
    void codeForAnotherPurposeIsInvalidAndDoesNotCountAsAttempt() {
        User user = newUser();
        String code = issueAndSave(user, OtpPurpose.VERIFY_EMAIL);

        assertThat(verifyError(user, OtpPurpose.RESET_PASSWORD, code)).isEqualTo(ErrorCode.INVALID_OTP);

        assertThat(reload(user).getOtpAttempts()).isZero();
        otp.verify(user.getId(), OtpPurpose.VERIFY_EMAIL, code); // mã vẫn dùng được đúng mục đích
    }

    @Test
    void missingOtpOrUnknownUserIsInvalid() {
        User user = newUser();

        assertThat(verifyError(user, OtpPurpose.VERIFY_EMAIL, "123456")).isEqualTo(ErrorCode.INVALID_OTP);
        assertThat(errorOf(() -> otp.verify(-1L, OtpPurpose.VERIFY_EMAIL, "123456"))).isEqualTo(ErrorCode.INVALID_OTP);
        assertThat(verifyError(user, OtpPurpose.VERIFY_EMAIL, null)).isEqualTo(ErrorCode.INVALID_OTP);
    }

    // --- Gửi lại ---

    @Test
    void resendTooSoonIsRejectedAndAfterTheIntervalReplacesTheOldCode() {
        User user = newUser();
        String first = issueAndSave(user, OtpPurpose.VERIFY_EMAIL);

        clock.advance(Duration.ofSeconds(30));
        assertThatThrownBy(() -> otp.issue(reload(user), OtpPurpose.VERIFY_EMAIL))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("OTP_RESEND_TOO_SOON"));

        clock.advance(Duration.ofSeconds(30)); // đủ 60 giây
        User current = reload(user);
        String second = otp.issue(current, OtpPurpose.VERIFY_EMAIL);
        users.saveAndFlush(current);

        if (!second.equals(first)) {
            assertThat(verifyError(user, OtpPurpose.VERIFY_EMAIL, first)).isEqualTo(ErrorCode.INVALID_OTP);
        }
        otp.verify(user.getId(), OtpPurpose.VERIFY_EMAIL, second);
    }

    @Test
    void resendResetsTheAttemptCounterAfterTooManyAttempts() {
        User user = newUser();
        String code = issueAndSave(user, OtpPurpose.VERIFY_EMAIL);
        for (int i = 0; i < 5; i++) {
            verifyError(user, OtpPurpose.VERIFY_EMAIL, wrongCodeFor(code));
        }
        assertThat(verifyError(user, OtpPurpose.VERIFY_EMAIL, code)).isEqualTo(ErrorCode.OTP_TOO_MANY_ATTEMPTS);

        clock.advance(Duration.ofSeconds(60));
        User current = reload(user);
        String fresh = otp.issue(current, OtpPurpose.VERIFY_EMAIL);
        users.saveAndFlush(current);

        assertThat(reload(user).getOtpAttempts()).isZero();
        otp.verify(user.getId(), OtpPurpose.VERIFY_EMAIL, fresh);
    }

    // --- Đồng thời ---

    @Test
    void parallelWrongGuessesNeverExceedTheAllowedAttempts() throws Exception {
        User user = newUser();
        String code = issueAndSave(user, OtpPurpose.VERIFY_EMAIL);
        String wrong = wrongCodeFor(code);
        int threads = 20;

        List<ErrorCode> results = runInParallel(threads, () -> verifyError(user, OtpPurpose.VERIFY_EMAIL, wrong));

        assertThat(results.stream().filter(r -> r == ErrorCode.INVALID_OTP).count()).isEqualTo(5);
        assertThat(results.stream().filter(r -> r == ErrorCode.OTP_TOO_MANY_ATTEMPTS).count()).isEqualTo(threads - 5);
        assertThat(reload(user).getOtpAttempts()).isEqualTo(5);
        assertThat(verifyError(user, OtpPurpose.VERIFY_EMAIL, code)).isEqualTo(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
    }

    @Test
    void parallelUseOfTheCorrectCodeSucceedsExactlyOnce() throws Exception {
        User user = newUser();
        String code = issueAndSave(user, OtpPurpose.RESET_PASSWORD);

        List<ErrorCode> results = runInParallel(5, () -> verifyError(user, OtpPurpose.RESET_PASSWORD, code));

        assertThat(results.stream().filter(r -> r == null).count()).as("đúng một lần thành công").isEqualTo(1);
        assertThat(results.stream().filter(r -> r == ErrorCode.INVALID_OTP).count()).isEqualTo(4);
    }

    private static <T> List<T> runInParallel(int threads, java.util.concurrent.Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
