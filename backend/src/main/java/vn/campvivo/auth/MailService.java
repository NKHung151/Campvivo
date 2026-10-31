package vn.campvivo.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.user.OtpPurpose;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.util.Locale;

/**
 * Gửi email OTP ĐỒNG BỘ (architecture.md: ngoại lệ duy nhất vì người dùng cần biết ngay nếu gửi lỗi). Lỗi gửi →
 * EMAIL_SEND_FAILED để người gọi rollback. Mã OTP chỉ nằm trong nội dung thư, KHÔNG được log.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final String OTP_TEMPLATE = "mail/otp";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties.Mail config;

    public MailService(JavaMailSender mailSender, TemplateEngine templateEngine, AppProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.config = properties.mail();
    }

    /**
     * @param ttl thời hạn của mã, hiển thị trong thư (làm tròn lên phút, tối thiểu 1 phút)
     * @throws BusinessException EMAIL_SEND_FAILED nếu không gửi được
     */
    public void sendOtp(String to, String fullName, String code, OtpPurpose purpose, Duration ttl) {
        String subject = subjectFor(purpose);

        Context context = new Context(VIETNAMESE);
        context.setVariable("subject", subject);
        context.setVariable("appName", config.fromName());
        context.setVariable("fullName", fullName);
        context.setVariable("code", code);
        context.setVariable("intro", introFor(purpose));
        context.setVariable("ttlMinutes", Math.max(1, (ttl.toSeconds() + 59) / 60));
        String html = templateEngine.process(OTP_TEMPLATE, context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(config.from(), config.fromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException e) {
            // Không log nội dung thư; chỉ loại lỗi và thông điệp của thư viện (không chứa mã OTP).
            log.warn("Không gửi được email OTP ({}): {}", purpose, e.getMessage());
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private static String subjectFor(OtpPurpose purpose) {
        return switch (purpose) {
            case VERIFY_EMAIL -> "Mã xác thực email Campvivo";
            case RESET_PASSWORD -> "Mã đặt lại mật khẩu Campvivo";
        };
    }

    private static String introFor(OtpPurpose purpose) {
        return switch (purpose) {
            case VERIFY_EMAIL -> "Cảm ơn bạn đã đăng ký tài khoản. Mã xác thực email của bạn là:";
            case RESET_PASSWORD -> "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. Mã xác thực của bạn là:";
        };
    }
}
