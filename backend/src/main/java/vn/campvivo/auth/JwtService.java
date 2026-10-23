package vn.campvivo.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Tạo / đọc JWT HS256 (docs/02-ky-thuat/authentication.md mục 3). Claims: sub = userId, role,
 * tv = token_version. Thời gian lấy từ bean Clock. Không log token.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public JwtService(AppProperties props, Clock clock) {
        this(props.jwt().secret(), props.jwt().ttl(), clock);
    }

    JwtService(String secret, Duration ttl, Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
        this.clock = clock;
    }

    public String createToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
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
            Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
            Integer tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
            if (tokenVersion == null) {
                throw new JwtException("Thiếu claim tv");
            }
            return new TokenClaims(userId, role, tokenVersion);
        } catch (IllegalArgumentException | NullPointerException e) {
            // subject không phải số, role lạ, token rỗng...
            throw new JwtException("Token không hợp lệ", e);
        }
    }

    /** Nội dung token đã được xác thực chữ ký. Role ở đây chỉ để tham khảo — quyền thật lấy từ DB. */
    public record TokenClaims(long userId, Role role, int tokenVersion) {}
}
