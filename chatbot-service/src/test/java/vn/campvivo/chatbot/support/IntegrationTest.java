package vn.campvivo.chatbot.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Nền cho test tích hợp: một context dùng chung, DB pgvector thật (migration + seed backend), model AI giả.
 * Có hàm tạo user và ký JWT giống backend (sub = userId, tv = token_version).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PgvectorTestContainer.class)
@Import(TestAiConfig.class)
public abstract class IntegrationTest {

    protected static final String SECRET = "test_secret_with_at_least_32_characters_ok";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected FakeChatModel chatModel;
    @Autowired
    protected FakeEmbeddingModel embeddingModel;

    /** Tạo user (email ngẫu nhiên) và trả id. */
    protected long createUser(String role) {
        return jdbc.sql("""
                        INSERT INTO users (email, password_hash, full_name, role, is_active, email_verified)
                        VALUES (?, 'x', 'Người test', ?, true, true) RETURNING id
                        """)
                .params("u-" + UUID.randomUUID() + "@test.vn", role)
                .query(Long.class).single();
    }

    protected String bearer(long userId) {
        int tv = jdbc.sql("SELECT token_version FROM users WHERE id = ?").param(userId).query(Integer.class).single();
        return "Bearer " + token(userId, tv);
    }

    protected static String token(long userId, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", "CUSTOMER")
                .claim("tv", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }
}
