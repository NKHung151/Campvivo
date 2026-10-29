package vn.campvivo.chatbot.common.util;

import org.jsoup.Jsoup;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Tiện ích chữ cho dữ liệu đưa vào model: bỏ HTML, rút gọn, định dạng tiền.
 */
public final class TextUtil {

    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    private TextUtil() {
    }

    /** HTML (mô tả sản phẩm/combo) → chữ thường, gộp khoảng trắng. null → "". */
    public static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text().replaceAll("\\s+", " ").trim();
    }

    /** Cắt còn tối đa `max` ký tự, thêm "…" nếu bị cắt. */
    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)).trim() + "…";
    }

    /** 1250000 → "1.250.000đ" (giống cách hiển thị giá ở storefront). */
    public static String formatVnd(long amount) {
        return NumberFormat.getIntegerInstance(VI).format(amount) + "đ";
    }
}
