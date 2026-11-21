package vn.campvivo.search;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Chuẩn hoá từ khoá cho tìm kiếm gần giống: bỏ dấu, viết thường, gộp khoảng trắng. */
final class SearchText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SearchText() {
    }

    /** "Lều  Cắm Trại" → "leu cam trai" (chữ đ/Đ thành d). Khớp với `f_unaccent(lower(...))` bên PostgreSQL cho tiếng Việt. */
    static String fold(String s) {
        if (s == null) {
            return "";
        }
        String t = MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        t = t.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        return SPACES.matcher(t.trim()).replaceAll(" ");
    }

    /** Thoát ký tự đại diện của LIKE (dùng cùng `ESCAPE '\'`) để `%` và `_` trong từ khoá chỉ là ký tự thường. */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
