package vn.campvivo.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    @Test
    void removesScriptTagAndContent() {
        String out = HtmlSanitizer.sanitize("<p>Xin chào</p><script>alert(1)</script>");
        assertThat(out).contains("<p>Xin chào</p>").doesNotContain("script").doesNotContain("alert");
    }

    @Test
    void removesEventHandlers() {
        String out = HtmlSanitizer.sanitize("<img src=\"https://x.test/a.png\" onerror=\"alert(1)\">");
        assertThat(out).contains("https://x.test/a.png").doesNotContain("onerror").doesNotContain("alert");
    }

    @Test
    void blocksJavascriptLinks() {
        String out = HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">bấm</a>");
        assertThat(out).doesNotContain("javascript").doesNotContain("href");
        assertThat(out).contains("bấm");
    }

    @Test
    void keepsAllowedFormattingAndSafeLinks() {
        String out = HtmlSanitizer.sanitize(
                "<h2>Tiêu đề</h2><ul><li><b>Đậm</b></li></ul><a href=\"https://campvivo.vn\">Link</a>");
        assertThat(out).contains("<h2>Tiêu đề</h2>", "<ul>", "<li>", "<b>Đậm</b>", "https://campvivo.vn");
    }

    @Test
    void removesIframe() {
        assertThat(HtmlSanitizer.sanitize("<iframe src=\"https://evil.test\"></iframe>ok"))
                .doesNotContain("iframe").contains("ok");
    }

    @Test
    void nullBecomesEmpty() {
        assertThat(HtmlSanitizer.sanitize(null)).isEmpty();
    }
}
