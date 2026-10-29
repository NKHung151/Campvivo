package vn.campvivo.chatbot.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import vn.campvivo.chatbot.common.config.ChatbotProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;

/**
 * Đọc JWT do backend cấp (HS256, chung JWT_SECRET — authentication.md mục 3): sub = userId, tv = token_version.
 * Chatbot không tự cấp token. Role trong token không dùng — quyền thật lấy từ bảng users.
 * Cách làm giống {@code vn.campvivo.auth.JwtService.parse} của backend; bỏ đi khi gộp vào backend (D-28).
 */
@Component
public class JwtTokenParser {

    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecretKey key;
    private final Clock clock;

    public JwtTokenParser(ChatbotProperties props, Clock clock) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    /**
     * @throws JwtException nếu token sai chữ ký, hết hạn, sai định dạng hoặc thiếu claim.
     */
    public TokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            long userId = Long.parseLong(claims.getSubject());
            Integer tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
            if (tokenVersion == null) {
                throw new JwtException("Thiếu claim tv");
            }
            return new TokenClaims(userId, tokenVersion);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("Token không hợp lệ", e);
        }
    }

    public record TokenClaims(long userId, int tokenVersion) {}
}
