package vn.campvivo.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import vn.campvivo.support.PostgresTestContainerConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chạy trên PostgreSQL thật (Testcontainers) với Flyway + ddl-auto=validate, chỉ quét entity User
 * (các entity khác chưa có bảng). Context khởi động được nghĩa là entity khớp migration.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class UserRepositoryTest {

    @TestConfiguration
    @EntityScan(basePackageClasses = User.class)
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    static class OnlyUserModule {}

    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void savesWithDefaultsFromSpec() {
        User saved = users.saveAndFlush(new User("an@campvivo.vn", "hash", "Nguyễn An"));

        User found = users.findById(saved.getId()).orElseThrow();
        assertThat(found.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(found.isActive()).isTrue();
        assertThat(found.isEmailVerified()).isFalse();
        assertThat(found.getOtpAttempts()).isZero();
        assertThat(found.getTokenVersion()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void normalizesEmailToLowerCaseAndTrimmed() {
        users.saveAndFlush(new User("  Binh@Campvivo.VN ", "hash", "Binh"));

        assertThat(users.findByEmail("binh@campvivo.vn")).isPresent();
    }

    @Test
    void duplicateEmailIsRejected() {
        users.saveAndFlush(new User("dup@campvivo.vn", "hash", "A"));

        assertThatThrownBy(() -> users.saveAndFlush(new User("DUP@campvivo.vn", "hash", "B")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleOutsideAllowedValuesIsRejectedByDatabase() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, role) VALUES (?, ?, ?, ?)",
                "hack@campvivo.vn", "hash", "Hack", "SUPERUSER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void otpPurposeOutsideAllowedValuesIsRejectedByDatabase() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (email, password_hash, full_name, otp_purpose) VALUES (?, ?, ?, ?)",
                "otp@campvivo.vn", "hash", "Otp", "SOMETHING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
