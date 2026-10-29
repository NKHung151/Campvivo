package vn.campvivo.chatbot.knowledge;

import java.util.regex.Pattern;

/**
 * Làm sạch chữ trích từ tài liệu trước khi chia đoạn: bỏ ký tự điều khiển, gộp khoảng trắng thừa, bỏ dòng trống lặp.
 */
public final class TextCleaner {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private TextCleaner() {
    }

    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace("\r\n", "\n").replace('\r', '\n');
        t = CONTROL.matcher(t).replaceAll("");
        t = SPACES.matcher(t).replaceAll(" ");
        t = t.lines().map(String::strip).reduce((a, b) -> a + "\n" + b).orElse("");
        return BLANK_LINES.matcher(t).replaceAll("\n\n").strip();
    }
}
