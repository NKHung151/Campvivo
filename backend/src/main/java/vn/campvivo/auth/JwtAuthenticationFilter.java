package vn.campvivo.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Đọc "Authorization: Bearer <JWT>" (authentication.md mục 3):
 * 1. Không có header -> đi tiếp như khách.
 * 2. Token sai chữ ký / hết hạn -> 401.
 * 3. Tải user theo sub; không tồn tại, bị khoá, hoặc tv != token_version -> 401.
 * 4. Authority ROLE_<role> lấy TỪ DB, không tin role trong token. Principal = userId (Long).
 * Không phải bean (để Spring Boot không đăng ký thêm vào servlet chain lần nữa): SecurityConfig tự tạo.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final JsonSecurityErrorHandler errorHandler;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                   JsonSecurityErrorHandler errorHandler) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<User> user = authenticate(header.substring(BEARER.length()).trim());
        if (user.isEmpty()) {
            SecurityContextHolder.clearContext();
            errorHandler.commence(request, response, new BadCredentialsException("Token không hợp lệ"));
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                user.get().getId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.get().getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private Optional<User> authenticate(String token) {
        try {
            JwtService.TokenClaims claims = jwtService.parse(token);
            return userRepository.findById(claims.userId())
                    .filter(User::isActive)
                    .filter(u -> u.getTokenVersion() == claims.tokenVersion());
        } catch (JwtException e) {
            return Optional.empty();
        }
    }
}
