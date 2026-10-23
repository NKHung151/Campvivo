package vn.campvivo.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.auth.dto.LoginRequest;
import vn.campvivo.auth.dto.LoginResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;
import vn.campvivo.user.dto.UserProfileResponse;

import java.util.Optional;

/**
 * Đăng nhập / đăng xuất (FR-02, docs/03-san-pham/features.md). Không log mật khẩu, token.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Băm giả để email không tồn tại cũng tốn thời gian BCrypt như email có thật (chống dò email qua độ trễ). */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("campvivo-dummy-password");
    }

    /**
     * Sai email hoặc sai mật khẩu đều trả cùng INVALID_CREDENTIALS. Chỉ khi mật khẩu ĐÚNG mới báo tiếp
     * trạng thái tài khoản (chưa xác thực email / bị khoá) để không lộ thông tin cho người đoán mật khẩu.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(request.email()));
        boolean passwordOk = passwordEncoder.matches(
                request.password(), found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = found.get();
        if (!user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return new LoginResponse(jwtService.createToken(user), UserProfileResponse.from(user));
    }

    /** Tăng token_version: mọi token đã cấp (ở mọi thiết bị) hết hiệu lực (D-11). */
    @Transactional
    public void logout(Long userId) {
        userRepository.incrementTokenVersion(userId);
    }
}
