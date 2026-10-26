package vn.campvivo.common.util;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Làm sạch HTML do người dùng/admin nhập (mô tả sản phẩm, nội dung bài viết) TRƯỚC khi lưu — chống XSS.
 * Cho phép: định dạng (b, i, em, strong...), khối (p, h1-h6, ul/ol/li, blockquote, div...), liên kết
 * http/https/mailto, ảnh http/https. Loại: script, style, iframe, sự kiện on*, javascript:.
 */
public final class HtmlSanitizer {

    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.IMAGES);

    private HtmlSanitizer() {}

    /** Trả chuỗi rỗng nếu đầu vào null. */
    public static String sanitize(String html) {
        return html == null ? "" : POLICY.sanitize(html);
    }
}
