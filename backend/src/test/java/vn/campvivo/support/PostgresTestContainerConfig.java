package vn.campvivo.support;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Dùng PostgreSQL thật qua Testcontainers cho test liên quan transaction/locking —
 * theo .agents/rules/testing.md: KHÔNG mock DB cho các test này.
 * Đăng ký trong test bằng @ContextConfiguration(initializers = PostgresTestContainerConfig.class)
 * hoặc @DynamicPropertySource tương đương.
 */
public class PostgresTestContainerConfig
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("campvivo_test")
                    .withUsername("campvivo")
                    .withPassword("campvivo");

    static {
        POSTGRES.start();
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword()
        ).applyTo(context.getEnvironment());
    }
}
