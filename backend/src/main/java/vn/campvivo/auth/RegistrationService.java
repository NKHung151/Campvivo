package vn.campvivo.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.auth.dto.LoginResponse;
import vn.campvivo.auth.dto.RegisterRequest;
import vn.campvivo.auth.dto.RegisterResponse;
import vn.campvivo.auth.dto.ResendCodeRequest;
import vn.campvivo.auth.dto.VerifyEmailRequest;
import vn.campvivo.cart.CartService;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.user.OtpPurpose;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;
import vn.campvivo.user.dto.UserProfileResponse;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Đăng ký, xác thực email bằng OTP, gửi lại mã (FR-01, docs/03-san-pham/features.md; BR-11). Không log mật khẩu,
 * mã OTP, token.
 */
@Service
public class RegistrationService {

    /** BCrypt chỉ dùng 72 byte đầu của mật khẩu; chặn mật khẩu dài hơn thay vì cắt âm thầm (ký tự tiếng Việt chiếm 2–3 byte). */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final MailService mailService;
    private final JwtService jwtService;
    private final CartService cartService;
    private final Duration otpTtl;
    /** Băm giả: email không tồn tại cũng tốn thời gian BCrypt như email thật khi xác thực (chống dò email qua độ trễ). */
    private final String dummyHash;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder, OtpService otpService,
                               MailService mailService, JwtService jwtService, CartService cartService,
                               AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.mailService = mailService;
        this.jwtService = jwtService;
        this.cartService = cartService;
        this.otpTtl = properties.otp().ttl();
        this.dummyHash = passwordEncoder.encode("campvivo-dummy-otp");
    }

    /**
     * Đăng ký (hoặc đăng ký lại với email CHƯA xác thực: ghi đè mật khẩu + họ tên, gửi mã mới). Gửi mail trong cùng
     * giao dịch: gửi lỗi (EMAIL_SEND_FAILED) hoặc gửi lại quá sớm (OTP_RESEND_TOO_SOON) thì rollback — không tạo user,
     * không đổi mật khẩu của tài khoản đang chờ xác thực.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        validatePasswordLength(request.password());
        String fullName = request.fullName().trim();
        if (fullName.length() < 2) {
            throw validationError("fullName", "Họ tên phải từ 2 đến 100 ký tự");
        }
        String email = User.normalizeEmail(request.email());

        Optional<User> existing = userRepository.findByEmail(email);
        User user;
        boolean created = false;
        if (existing.isPresent()) {
            user = existing.get();
            if (user.isEmailVerified()) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setFullName(fullName);
        } else {
            try {
                user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(request.password()), fullName));
            } catch (DataIntegrityViolationException e) {
                // Một yêu cầu đăng ký song song cùng email vừa tạo xong dòng này.
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            created = true;
        }

        if (created) {
            cartService.createForUser(user.getId());
        }
        String code = otpService.issue(user, OtpPurpose.VERIFY_EMAIL);
        mailService.sendOtp(user.getEmail(), user.getFullName(), code, OtpPurpose.VERIFY_EMAIL, otpTtl);
        return new RegisterResponse(user.getEmail());
    }

    /**
     * Xác thực email bằng OTP rồi đăng nhập luôn. Email không tồn tại và mã sai cùng trả INVALID_OTP. KHÔNG đánh dấu
     * @Transactional: OtpService.verify dùng giao dịch riêng nên ở đây không được giữ thay đổi chưa commit trên dòng user.
     */
    public LoginResponse verifyEmail(VerifyEmailRequest request) {
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(request.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.code(), dummyHash);
            throw new BusinessException(ErrorCode.INVALID_OTP);
        }

        Long userId = found.get().getId();
        otpService.verify(userId, OtpPurpose.VERIFY_EMAIL, request.code());

        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OTP));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        userRepository.markEmailVerified(userId);
        User verified = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OTP));
        return new LoginResponse(jwtService.createToken(verified), UserProfileResponse.from(verified));
    }

    /**
     * Gửi lại mã. Luôn thành công (200) nếu không có lỗi gửi, kể cả email không tồn tại, tài khoản bị khoá, hoặc email đã
     * xác thực khi xin mã VERIFY_EMAIL: những trường hợp đó im lặng không gửi gì (chống dò email). Hai lỗi vẫn có thể xảy ra
     * với email thật: OTP_RESEND_TOO_SOON và EMAIL_SEND_FAILED.
     */
    @Transactional
    public void resendCode(ResendCodeRequest request) {
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(request.email()));
        if (found.isEmpty()) {
            return;
        }
        User user = found.get();
        if (!user.isActive() || (request.purpose() == OtpPurpose.VERIFY_EMAIL && user.isEmailVerified())) {
            return;
        }
        String code = otpService.issue(user, request.purpose());
        mailService.sendOtp(user.getEmail(), user.getFullName(), code, request.purpose(), otpTtl);
    }

    private static void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw validationError("password", "Mật khẩu quá dài");
        }
    }

    private static BusinessException validationError(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                Map.of("fieldErrors", Map.of(field, message)));
    }
}
