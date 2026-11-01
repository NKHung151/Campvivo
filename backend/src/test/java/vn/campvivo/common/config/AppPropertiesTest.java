package vn.campvivo.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra application.yml bind đúng vào AppProperties. Không cần DB.
 * "spring.profiles.active=test" để không nạp application-dev.yml của máy cá nhân.
 */
class AppPropertiesTest {

    private static final String SECRET = "test_secret_with_at_least_32_characters_ok";

    @EnableConfigurationProperties(AppProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues("spring.profiles.active=test");

    @Test
    void bindsDefaultsFromApplicationYml() {
        runner.withPropertyValues("app.jwt.secret=" + SECRET).run(context -> {
            AppProperties props = context.getBean(AppProperties.class);

            assertThat(props.jwt().ttl()).isEqualTo(Duration.ofHours(24));
            assertThat(props.otp().ttl()).isEqualTo(Duration.ofMinutes(10));
            assertThat(props.otp().maxAttempts()).isEqualTo(5);
            assertThat(props.otp().resendInterval()).isEqualTo(Duration.ofSeconds(60));
            assertThat(props.order().paymentTimeout()).isEqualTo(Duration.ofMinutes(15));
            assertThat(props.returns().windowDays()).isEqualTo(7);
            assertThat(props.shipping().freeThreshold()).isEqualTo(1000000L);
            assertThat(props.inventory().lowStockThreshold()).isEqualTo(5);
            assertThat(props.cors().allowedOrigins()).containsExactly("http://localhost:3000");
            assertThat(props.frontendUrl()).isEqualTo("http://localhost:3000");
            assertThat(props.vnpay().payUrl()).contains("sandbox.vnpayment.vn");
            assertThat(props.mail().from()).isEqualTo("no-reply@campvivo.local");
            assertThat(props.mail().fromName()).isEqualTo("Campvivo");
        });
    }

    @Test
    void bindsReturnKeyDespiteJavaKeyword() {
        runner.withPropertyValues("app.jwt.secret=" + SECRET, "app.return.window-days=9").run(context ->
                assertThat(context.getBean(AppProperties.class).returns().windowDays()).isEqualTo(9));
    }

    @Test
    void failsToStartWhenJwtSecretMissing() {
        runner.withPropertyValues("app.jwt.secret=").run(context -> assertThat(context).hasFailed());
    }

    /** .env cho phép KEY="giá trị"; khi nạp qua spring.config.import dấu nháy còn nguyên nên phải tự bỏ. */
    @Test
    void stripsQuotesFromGhnValuesWrittenInDotEnv() {
        runner.withPropertyValues("app.jwt.secret=" + SECRET,
                        "app.ghn.from-ward-code=\"1A0404\"", "app.ghn.token='abc' ", "app.ghn.shop-id=123")
                .run(context -> {
                    AppProperties.Ghn ghn = context.getBean(AppProperties.class).ghn();
                    assertThat(ghn.fromWardCode()).isEqualTo("1A0404");
                    assertThat(ghn.token()).isEqualTo("abc");
                    assertThat(ghn.shopId()).isEqualTo("123");
                });
    }

    @Test
    void failsToStartWhenJwtSecretTooShort() {
        runner.withPropertyValues("app.jwt.secret=short").run(context -> assertThat(context).hasFailed());
    }
}
