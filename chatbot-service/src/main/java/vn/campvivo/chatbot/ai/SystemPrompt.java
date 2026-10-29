package vn.campvivo.chatbot.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Prompt hệ thống tiếng Việt (`prompts/system-vi.txt`). Thay {{TODAY}} bằng ngày hiện tại; không dùng template
 * engine để nội dung prompt có ký tự { } không bị hiểu nhầm.
 */
@Component
public class SystemPrompt {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String template;
    private final Clock clock;

    public SystemPrompt(Clock clock) {
        this.clock = clock;
        try {
            this.template = new ClassPathResource("prompts/system-vi.txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được prompts/system-vi.txt", e);
        }
    }

    public String render() {
        return template.replace("{{TODAY}}", LocalDate.now(clock).format(DATE));
    }
}
