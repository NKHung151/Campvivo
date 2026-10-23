package vn.campvivo.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Tạo slug từ tiếng Việt: "Lều cắm trại 2 người" -> "leu-cam-trai-2-nguoi". */
public final class SlugUtil {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private SlugUtil() {}

    /** Trả chuỗi rỗng nếu đầu vào null hoặc không còn ký tự chữ/số nào. */
    public static String toSlug(String input) {
        if (input == null) {
            return "";
        }
        String s = Normalizer.normalize(input, Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("");
        s = s.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        s = NON_ALNUM.matcher(s).replaceAll("-");
        return s.replaceAll("^-+|-+$", "");
    }
}
