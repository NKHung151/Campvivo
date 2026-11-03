package vn.campvivo.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.campvivo.user.User;

import java.io.IOException;
import java.util.Optional;

/**
 * Đọc "Authorization: Bearer <JWT>" (authentication.md mục 3):
 * 1. Không có header -> đi tiếp như khách.
 * 2. Token sai chữ ký / hết hạn -> 401.
 * 3. Tải user theo sub; không tồn tại, bị khoá, hoặc tv != token_version -> 401.
 * 4. Authority ROLE_<role> lấy TỪ DB, không tin role trong token. Principal = userId (Long).
 * Việc kiểm token nằm ở {@link TokenAuthenticator} (dùng chung với WebSocket).
 * Không phải bean (để Spring Boot không đăng ký thêm vào servlet chain lần nữa): SecurityConfig tự tạo.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenAuthenticator tokenAuthenticator;
    private final JsonSecurityErrorHandler errorHandler;

    public JwtAuthenticationFilter(TokenAuthenticator tokenAuthenticator, JsonSecurityErrorHandler errorHandler) {
        this.tokenAuthenticator = tokenAuthenticator;
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = TokenAuthenticator.bearerToken(request.getHeader("Authorization"));
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }

        Optional<User> user = tokenAuthenticator.authenticate(token);
        if (user.isEmpty()) {
            SecurityContextHolder.clearContext();
            errorHandler.commence(request, response, new BadCredentialsException("Token không hợp lệ"));
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(TokenAuthenticator.toAuthentication(user.get()));
        chain.doFilter(request, response);
    }
}
