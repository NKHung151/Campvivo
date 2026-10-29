package vn.campvivo.chatbot.security;

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

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Giống JwtAuthenticationFilter của backend: có header Bearer thì token PHẢI hợp lệ (sai → 401 ngay), user còn
 * hoạt động và token_version khớp DB (đăng xuất ở backend → token chết ở đây). Principal = userId (Long).
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtTokenParser parser;
    private final UserAccountReader users;
    private final JsonSecurityErrorHandler errorHandler;

    public JwtAuthFilter(JwtTokenParser parser, UserAccountReader users, JsonSecurityErrorHandler errorHandler) {
        this.parser = parser;
        this.users = users;
        this.errorHandler = errorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<UserAccountReader.UserAccount> user = authenticate(header.substring(BEARER.length()).trim());
        if (user.isEmpty()) {
            SecurityContextHolder.clearContext();
            errorHandler.commence(request, response, new BadCredentialsException("Token không hợp lệ"));
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                user.get().id(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.get().role())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private Optional<UserAccountReader.UserAccount> authenticate(String token) {
        try {
            JwtTokenParser.TokenClaims claims = parser.parse(token);
            return users.findById(claims.userId())
                    .filter(UserAccountReader.UserAccount::active)
                    .filter(u -> u.tokenVersion() == claims.tokenVersion());
        } catch (JwtException e) {
            return Optional.empty();
        }
    }
}
