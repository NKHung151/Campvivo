package vn.campvivo.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.user.OtpPurpose;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * OTP qua email cho xác thực email và đặt lại mật khẩu (BR-11, D-10). Mã 6 chữ số sinh bằng SecureRandom, chỉ lưu
 * BCrypt, hạn `app.otp.ttl`, tối đa `app.otp.max-attempts` lần thử, gửi lại cách nhau `app.otp.resend-interval`.
 * KHÔNG BAO GIỜ log hay trả mã (ngoài giá trị trả về của {@link #issue} để gửi mail).
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_DIGITS = 6;
    private static final int CODE_BOUND = 1_000_000;

    private enum Outcome { OK, INVALID, EXPIRED, TOO_MANY }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AppProperties.Otp config;
    /** Giao dịch RIÊNG cho verify: số lần thử phải được ghi nhận ngay cả khi người gọi sau đó bị rollback. */
    private final TransactionTemplate verifyTransaction;

    public OtpService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock,
                      AppProperties properties, PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.config = properties.otp();
        this.verifyTransaction = new TransactionTemplate(transactionManager);
        this.verifyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Sinh OTP mới cho `user` và ghi (bản băm, mục đích, hạn, số lần thử = 0, lần gửi) vào entity; OTP cũ bị ghi đè.
     * Người gọi chịu trách nhiệm lưu user (entity đang được quản lý trong giao dịch của họ) và gửi mã qua email.
     * Nếu gửi mail lỗi thì rollback để không lưu OTP chưa từng tới tay người dùng.
     *
     * @return mã thô 6 chữ số — chỉ dùng để gửi email, không lưu, không log
     * @throws BusinessException OTP_RESEND_TOO_SOON nếu lần gửi trước cách chưa đủ `resend-interval`
     */
    public String issue(User user, OtpPurpose purpose) {
        Instant now = clock.instant();
        Instant lastSent = user.getOtpLastSentAt();
        if (lastSent != null && Duration.between(lastSent, now).compareTo(config.resendInterval()) < 0) {
            throw new BusinessException(ErrorCode.OTP_RESEND_TOO_SOON);
        }

        String code = generateCode();
        user.setOtpCodeHash(passwordEncoder.encode(code));
        user.setOtpPurpose(purpose);
        user.setOtpExpiresAt(now.plus(config.ttl()));
        user.setOtpAttempts(0);
        user.setOtpLastSentAt(now);
        return code;
    }

    /**
     * Kiểm tra mã. Thứ tự lỗi theo BR-11: không có OTP hoặc sai mục đích → INVALID_OTP; hết hạn → OTP_EXPIRED; đã đủ
     * số lần thử → OTP_TOO_MANY_ATTEMPTS; sai mã → tính một lần thử rồi INVALID_OTP; đúng → xoá OTP (dùng một lần).
     * Chạy trong giao dịch riêng (REQUIRES_NEW) nên đừng gọi khi giao dịch của người gọi đang giữ thay đổi chưa commit
     * trên chính dòng user này.
     */
    public void verify(Long userId, OtpPurpose purpose, String code) {
        Outcome outcome = verifyTransaction.execute(status -> check(userId, purpose, code));
        // Ném SAU khi giao dịch riêng đã commit: số lần thử không bị mất theo ngoại lệ.
        switch (outcome) {
            case OK -> { }
            case EXPIRED -> throw new BusinessException(ErrorCode.OTP_EXPIRED);
            case TOO_MANY -> throw new BusinessException(ErrorCode.OTP_TOO_MANY_ATTEMPTS);
            default -> throw new BusinessException(ErrorCode.INVALID_OTP);
        }
    }

    private Outcome check(Long userId, OtpPurpose purpose, String code) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getOtpCodeHash() == null || user.getOtpPurpose() != purpose
                || user.getOtpExpiresAt() == null) {
            return Outcome.INVALID;
        }
        if (!clock.instant().isBefore(user.getOtpExpiresAt())) {
            return Outcome.EXPIRED;
        }
        // Tính lượt thử TRƯỚC khi so mã: nhiều yêu cầu đoán song song vẫn bị chặn ở đúng số lần cho phép.
        if (userRepository.incrementOtpAttemptsIfBelow(userId, config.maxAttempts()) == 0) {
            return Outcome.TOO_MANY;
        }
        if (code != null && passwordEncoder.matches(code, user.getOtpCodeHash())) {
            return userRepository.clearOtp(userId) == 1 ? Outcome.OK : Outcome.INVALID;
        }
        return Outcome.INVALID;
    }

    private static String generateCode() {
        return String.format("%0" + CODE_DIGITS + "d", RANDOM.nextInt(CODE_BOUND));
    }
}
