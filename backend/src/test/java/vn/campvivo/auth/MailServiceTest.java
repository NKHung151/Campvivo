package vn.campvivo.auth;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.user.OtpPurpose;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MailService với máy chủ SMTP giả (GreenMail, cổng 3025): thư tới đúng người, tiêu đề theo mục đích, có mã và hạn,
 * escape HTML, lỗi gửi → EMAIL_SEND_FAILED mà không lộ mã. Không cần DB.
 */
class MailServiceTest {

    private static final String CODE = "482915";

    @RegisterExtension
    static final GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @EnableConfigurationProperties(AppProperties.class)
    @Import(MailService.class)
    static class Config {}

    private ApplicationContextRunner runner(int smtpPort) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ConfigurationPropertiesAutoConfiguration.class,
                        MailSenderAutoConfiguration.class,
                        ThymeleafAutoConfiguration.class))
                .withUserConfiguration(Config.class)
                .withPropertyValues(
                        "spring.mail.host=localhost",
                        "spring.mail.port=" + smtpPort,
                        "app.jwt.secret=test_secret_with_at_least_32_characters_ok");
    }

    private MimeMessage singleReceivedMessage() {
        assertThat(greenMail.waitForIncomingEmail(5000, 1)).as("phải nhận đúng 1 thư").isTrue();
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        return messages[0];
    }

    private static String htmlBody(MimeMessage message) throws Exception {
        return (String) message.getContent();
    }

    @Test
    void verifyEmailMailReachesTheRecipientWithCodeAndExpiry() {
        runner(ServerSetupTest.SMTP.getPort()).run(context -> {
            context.getBean(MailService.class)
                    .sendOtp("khach@campvivo.vn", "Nguyễn Văn An", CODE, OtpPurpose.VERIFY_EMAIL, Duration.ofMinutes(10));

            MimeMessage message = singleReceivedMessage();
            assertThat(message.getAllRecipients()).extracting(Object::toString).containsExactly("khach@campvivo.vn");
            assertThat(message.getSubject()).isEqualTo("Mã xác thực email Campvivo");
            InternetAddress from = (InternetAddress) message.getFrom()[0];
            assertThat(from.getAddress()).isEqualTo("no-reply@campvivo.local");
            assertThat(from.getPersonal()).isEqualTo("Campvivo");
            assertThat(message.getContentType()).startsWith("text/html");

            String html = htmlBody(message);
            assertThat(html).contains(CODE).contains("Nguyễn Văn An").contains(">10<").contains("phút");
        });
    }

    @Test
    void resetPasswordMailHasItsOwnSubjectAndWording() {
        runner(ServerSetupTest.SMTP.getPort()).run(context -> {
            context.getBean(MailService.class)
                    .sendOtp("khach@campvivo.vn", "An", CODE, OtpPurpose.RESET_PASSWORD, Duration.ofMinutes(10));

            MimeMessage message = singleReceivedMessage();
            assertThat(message.getSubject()).isEqualTo("Mã đặt lại mật khẩu Campvivo");
            assertThat(htmlBody(message)).contains(CODE).contains("đặt lại mật khẩu");
        });
    }

    @Test
    void userSuppliedNameIsHtmlEscaped() {
        runner(ServerSetupTest.SMTP.getPort()).run(context -> {
            context.getBean(MailService.class).sendOtp("khach@campvivo.vn", "<script>alert(1)</script>", CODE,
                    OtpPurpose.VERIFY_EMAIL, Duration.ofMinutes(10));

            String html = htmlBody(singleReceivedMessage());
            assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
        });
    }

    @Test
    void expiryIsRoundedUpToWholeMinutesWithMinimumOfOne() {
        runner(ServerSetupTest.SMTP.getPort()).run(context -> {
            MailService mail = context.getBean(MailService.class);
            mail.sendOtp("a@campvivo.vn", "An", CODE, OtpPurpose.VERIFY_EMAIL, Duration.ofSeconds(90));
            mail.sendOtp("b@campvivo.vn", "An", CODE, OtpPurpose.VERIFY_EMAIL, Duration.ofSeconds(30));

            assertThat(greenMail.waitForIncomingEmail(5000, 2)).isTrue();
            String first = htmlBody(greenMail.getReceivedMessages()[0]);
            String second = htmlBody(greenMail.getReceivedMessages()[1]);
            assertThat(first).contains(">2<");
            assertThat(second).contains(">1<");
        });
    }

    @Test
    void unreachableSmtpServerIsEmailSendFailedWithoutLeakingTheCode() {
        // Cổng 1 không có máy chủ SMTP nào lắng nghe.
        runner(1).run(context -> {
            MailService mail = context.getBean(MailService.class);

            assertThatThrownBy(() -> mail.sendOtp("khach@campvivo.vn", "An", CODE, OtpPurpose.VERIFY_EMAIL,
                    Duration.ofMinutes(10)))
                    .isInstanceOfSatisfying(BusinessException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo("EMAIL_SEND_FAILED");
                        assertThat(e.getMessage()).doesNotContain(CODE);
                        assertThat(e.getDetails()).isNull();
                    });
        });
    }
}
