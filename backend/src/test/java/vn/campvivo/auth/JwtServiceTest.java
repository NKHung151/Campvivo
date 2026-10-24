package vn.campvivo.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test_secret_with_at_least_32_characters_ok";
    private static final Instant T0 = Instant.parse("2026-10-23T00:00:00Z");

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static JwtService serviceAt(Instant instant) {
        return new JwtService(SECRET, Duration.ofHours(24), clockAt(instant));
    }

    private static User userWithIdAndVersion(long id, Role role, int tokenVersion) throws Exception {
        User user = new User("a@campvivo.vn", "hash", "A");
        var idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
        user.setRole(role);
        user.setTokenVersion(tokenVersion);
        return user;
    }

    @Test
    void tokenRoundTripKeepsUserIdRoleAndTokenVersion() throws Exception {
        JwtService jwt = serviceAt(T0);

        String token = jwt.createToken(userWithIdAndVersion(42L, Role.EMPLOYEE, 3));
        JwtService.TokenClaims claims = jwt.parse(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.role()).isEqualTo(Role.EMPLOYEE);
        assertThat(claims.tokenVersion()).isEqualTo(3);
    }

    @Test
    void tokenIsRejectedAfterTtl() throws Exception {
        String token = serviceAt(T0).createToken(userWithIdAndVersion(1L, Role.CUSTOMER, 0));

        assertThat(serviceAt(T0.plus(Duration.ofHours(23))).parse(token).userId()).isEqualTo(1L);
        assertThatThrownBy(() -> serviceAt(T0.plus(Duration.ofHours(25))).parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() throws Exception {
        String token = new JwtService("another_secret_with_at_least_32_chars_xx", Duration.ofHours(24), clockAt(T0))
                .createToken(userWithIdAndVersion(1L, Role.CUSTOMER, 0));

        assertThatThrownBy(() -> serviceAt(T0).parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        String token = serviceAt(T0).createToken(userWithIdAndVersion(1L, Role.CUSTOMER, 0));
        String[] parts = token.split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"role\":\"ADMIN\",\"tv\":0,\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> serviceAt(T0).parse(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedTokenIsRejected() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"role\":\"ADMIN\",\"tv\":0}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> serviceAt(T0).parse(header + "." + payload + "."))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenMissingTokenVersionIsRejected() {
        String token = Jwts.builder()
                .subject("1").claim("role", "CUSTOMER")
                .expiration(Date.from(T0.plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> serviceAt(T0).parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> serviceAt(T0).parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }
}
