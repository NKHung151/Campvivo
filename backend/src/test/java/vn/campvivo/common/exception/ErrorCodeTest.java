package vn.campvivo.common.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ErrorCodeTest {

    @Test
    void everyCodeHasVietnameseMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.getMessage()).as(code.name()).isNotBlank();
        }
    }

    @Test
    void statusMatchesContract() {
        assertThat(ErrorCode.OUT_OF_STOCK.getStatus().value()).isEqualTo(409);
        assertThat(ErrorCode.OTP_RESEND_TOO_SOON.getStatus().value()).isEqualTo(429);
        assertThat(ErrorCode.EMAIL_SEND_FAILED.getStatus().value()).isEqualTo(502);
        assertThat(ErrorCode.NOT_PURCHASED.getStatus().value()).isEqualTo(403);
    }

    /** Chống lệch docs: mọi mã trong api.md mục 2 phải có trong enum đúng HTTP status, và ngược lại. */
    @Test
    void enumMatchesApiDocErrorTable() throws IOException {
        Path doc = Path.of("..", "docs", "02-ky-thuat", "api.md");
        assumeTrue(Files.exists(doc), "Không thấy api.md, bỏ qua");
        String text = Files.readString(doc, StandardCharsets.UTF_8);
        int start = text.indexOf("## 2. Danh mục mã lỗi");
        int end = text.indexOf("## 3.");
        assertThat(start).isGreaterThanOrEqualTo(0);
        String section = text.substring(start, end);

        Matcher m = Pattern.compile("\\|\\s*(\\d{3})\\s*\\|\\s*([A-Z_]+)\\s*\\|").matcher(section);
        int documented = 0;
        while (m.find()) {
            documented++;
            ErrorCode code = ErrorCode.valueOf(m.group(2));
            assertThat(code.getStatus().value()).as(code.name()).isEqualTo(Integer.parseInt(m.group(1)));
        }
        assertThat(ErrorCode.values()).hasSize(documented);
    }
}
