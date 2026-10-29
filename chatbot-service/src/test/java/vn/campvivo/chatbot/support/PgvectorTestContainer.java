package vn.campvivo.chatbot.support;

import org.flywaydb.core.Flyway;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL 16 + pgvector thật (D-27) cho test chatbot. Trước khi context chạy migration của chatbot, chạy migration +
 * seed THẬT của backend (copy vào test classpath ở `backend-db/`, xem pom.xml) — giống DB dùng chung khi chạy thật
 * (D-28), và query của chatbot luôn được kiểm trên schema hiện hành của backend.
 */
public class PgvectorTestContainer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("campvivo_chatbot_test")
                    .withUsername("campvivo")
                    .withPassword("campvivo");

    static {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:backend-db/migration", "classpath:backend-db/seed")
                .load()
                .migrate();
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
