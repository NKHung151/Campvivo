package vn.campvivo.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.campvivo.auth.dto.ForgotPasswordRequest;
import vn.campvivo.auth.dto.ResendCodeRequest;
import vn.campvivo.auth.dto.ResetPasswordRequest;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.user.OtpPurpose;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.util.Optional;

/**
 * Quên / đặt lại mật khẩu (FR-03, docs/03-san-pham/features.md; BR-11). Dùng lại OTP của đăng ký với mục đích
 * RESET_PASSWORD. Không log mật khẩu, mã OTP.
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final RegistrationService registrationService;
    /** Băm giả: email không tồn tại cũng tốn thời gian BCrypt như email thật (chống dò email qua độ trễ). */
    private final String dummyHash;

    public PasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder, OtpService otpService,
                                RegistrationService registrationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpService = otpService;
        this.registrationService = registrationService;
        this.dummyHash = passwordEncoder.encode("campvivo-dummy-reset");
    }

    /**
     * Gửi mã đặt lại mật khẩu. Luôn thành công (200) với email không tồn tại hoặc tài khoản bị khoá: không gửi gì (chống dò
     * email). Gửi lại quá sớm (OTP_RESEND_TOO_SOON) cũng trả 200 im lặng vì lỗi đó chỉ có với email thật. EMAIL_SEND_FAILED
     * vẫn được báo để người dùng biết thử lại. Tài khoản chưa xác thực email vẫn được gửi mã: đặt lại xong sẽ được coi là
     * đã xác thực.
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        try {
            registrationService.resendCode(new ResendCodeRequest(request.email(), OtpPurpose.RESET_PASSWORD));
        } catch (BusinessException e) {
            if (!ErrorCode.OTP_RESEND_TOO_SOON.name().equals(e.getErrorCode())) {
                throw e;
            }
        }
    }

    /**
     * Đặt lại mật khẩu bằng mã OTP. Thứ tự: mật khẩu quá 72 byte bị từ chối TRƯỚC khi đụng tới mã (mật khẩu sai không làm
     * mất lượt thử); email không tồn tại và mã sai cùng trả INVALID_OTP; đúng mã thì đổi hash, tăng token_version và đánh
     * dấu email đã xác thực trong một câu UPDATE. KHÔNG đánh dấu @Transactional: OtpService.verify dùng giao dịch riêng.
     */
    public void resetPassword(ResetPasswordRequest request) {
        PasswordPolicy.requireBcryptSafe(request.newPassword(), "newPassword");

        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(request.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.code(), dummyHash);
            throw new BusinessException(ErrorCode.INVALID_OTP);
        }

        Long userId = found.get().getId();
        otpService.verify(userId, OtpPurpose.RESET_PASSWORD, request.code());

        if (userRepository.resetPassword(userId, passwordEncoder.encode(request.newPassword())) == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
    }
}
