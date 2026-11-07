package vn.campvivo.auth;

import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * Biến JWT thành người dùng hợp lệ (authentication.md mục 3), dùng chung cho HTTP ({@link JwtAuthenticationFilter}) và
 * WebSocket ({@link StompAuthChannelInterceptor}) để hai nơi không lệch nhau: token đúng chữ ký, chưa hết hạn, user còn tồn
 * tại và còn hoạt động, `tv` khớp `users.token_version`. Quyền (role) lấy TỪ DB, không tin role trong token.
 */
@Component
public class TokenAuthenticator {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public TokenAuthenticator(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /** Lấy phần token từ giá trị header "Bearer <JWT>"; trả null nếu không có hoặc sai dạng. */
    public static String bearerToken(String headerValue) {
        if (headerValue == null || !headerValue.startsWith(BEARER)) {
            return null;
        }
        return headerValue.substring(BEARER.length()).trim();
    }

    /** @return người dùng nếu token hợp lệ; rỗng nếu token sai chữ ký / hết hạn / user không tồn tại, bị khoá hoặc `tv` cũ. */
    public Optional<User> authenticate(String token) {
        try {
            JwtService.TokenClaims claims = jwtService.parse(token);
            return userRepository.findById(claims.userId())
                    .filter(User::isActive)
                    .filter(u -> u.getTokenVersion() == claims.tokenVersion());
        } catch (JwtException e) {
            return Optional.empty();
        }
    }

    /** Principal = userId (Long); authority ROLE_<role> lấy từ DB. */
    public static Authentication toAuthentication(User user) {
        return new UsernamePasswordAuthenticationToken(
                user.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}
