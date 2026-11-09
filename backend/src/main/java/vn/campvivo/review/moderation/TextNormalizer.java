package vn.campvivo.review.moderation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chuẩn hoá nội dung đánh giá trước khi so với từ điển (D-33). Thuần hàm, không phụ thuộc Spring.
 * <ol>
 *   <li>Unicode NFC (gõ dấu tổ hợp = dựng sẵn), bỏ ký tự vô hình, chữ thường.</li>
 *   <li>Tách token: chữ, số và {@code @ $ *}; {@code *} ở giữa token là chữ bị che ({@code c*t}).</li>
 *   <li>Nối chuỗi chữ cái đơn bị chèn ký tự: {@code đ.m}, {@code d - k - m}, {@code D K M} → {@code đm}, {@code dkm}.</li>
 *   <li>Leetspeak trên token có ≥ 2 chữ cái lẫn số/ký hiệu: {@code l0n} → {@code lon}. Token như {@code D1}, {@code 5L} giữ nguyên.</li>
 *   <li>Gộp ký tự lặp ≥ 3 lần: {@code nguuuu} → {@code ngu}.</li>
 * </ol>
 * Token "cố tình biến dạng" (nối chữ đơn, leetspeak, che chữ) được đánh dấu {@code obfuscated}: khi so với mục
 * {@code [accented]} được phép khớp theo bản bỏ dấu, vì người viết đã chủ động làm mất dấu.
 */
public final class TextNormalizer {

    /** Một token sau chuẩn hoá. {@code mask} khác null khi token có chữ bị che bằng {@code *}. */
    public record Token(String accented, String plain, boolean obfuscated, Pattern mask) {

        /** Khớp mẫu che chữ với một từ (bản bỏ dấu) của từ điển. */
        public boolean maskMatches(String plainWord) {
            return mask != null && mask.matcher(plainWord).matches();
        }
    }

    private static final Pattern INVISIBLE = Pattern.compile("[\\u00AD\\u200B-\\u200D\\u2060\\uFEFF]");
    private static final Pattern COMBINING = Pattern.compile("\\p{M}+");
    private static final Pattern REPEAT_3 = Pattern.compile("(.)\\1{2,}");
    private static final Pattern MASK_RUN = Pattern.compile("\\*+");
    private static final char MASK = '*';
    /** Khoảng cách tối đa (số ký tự) giữa hai chữ cái đơn để coi là một từ bị chèn ký tự. */
    private static final int MAX_JOIN_GAP = 3;

    private TextNormalizer() {
    }

    /** Bước 1: NFC, bỏ ký tự vô hình, chữ thường. */
    public static String clean(String raw) {
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        return INVISIBLE.matcher(nfc).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** Bỏ dấu tiếng Việt: {@code đ} → {@code d}, bỏ dấu thanh và dấu mũ. */
    public static String removeDiacritics(String s) {
        String nfd = Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD);
        return COMBINING.matcher(nfd).replaceAll("");
    }

    public static List<Token> tokenize(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String s = clean(raw);
        List<Word> words = splitWords(s);
        List<Token> tokens = new ArrayList<>(words.size());
        int i = 0;
        while (i < words.size()) {
            int j = i;
            if (isSingleLetter(words.get(i).text)) {
                while (j + 1 < words.size() && isSingleLetter(words.get(j + 1).text)
                        && words.get(j + 1).start - words.get(j).end <= MAX_JOIN_GAP) {
                    j++;
                }
            }
            if (j > i) {
                StringBuilder joined = new StringBuilder();
                for (int k = i; k <= j; k++) {
                    joined.append(words.get(k).text);
                }
                tokens.add(token(joined.toString(), true));
            } else {
                tokens.add(token(words.get(i).text, false));
            }
            i = j + 1;
        }
        return tokens;
    }

    private record Word(String text, int start, int end) {}

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '@' || c == '$' || c == MASK;
    }

    private static boolean isSingleLetter(String w) {
        return w.length() == 1 && Character.isLetter(w.charAt(0));
    }

    /** Tách theo ký tự không thuộc từ; bỏ {@code *} ở hai đầu token (chỉ {@code *} ở giữa mới là chữ bị che). */
    private static List<Word> splitWords(String s) {
        List<Word> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            if (!isWordChar(s.charAt(i))) {
                i++;
                continue;
            }
            int start = i;
            while (i < s.length() && isWordChar(s.charAt(i))) {
                i++;
            }
            int end = i;
            while (start < end && s.charAt(start) == MASK) {
                start++;
            }
            while (end > start && s.charAt(end - 1) == MASK) {
                end--;
            }
            if (start < end) {
                out.add(new Word(s.substring(start, end), start, end));
            }
        }
        return out;
    }

    private static Token token(String text, boolean joined) {
        boolean obfuscated = joined;
        Pattern mask = null;
        if (text.indexOf(MASK) >= 0) {
            mask = maskPattern(text);
            text = text.replace(String.valueOf(MASK), "");
            obfuscated = true;
        }
        String leet = leet(text);
        if (!leet.equals(text)) {
            obfuscated = true;
            text = leet;
        }
        text = REPEAT_3.matcher(text).replaceAll("$1");
        return new Token(text, removeDiacritics(text), obfuscated, mask);
    }

    /** {@code c*t} → {@code c\p{L}{1,3}t}, so với bản bỏ dấu của từ điển. */
    private static Pattern maskPattern(String text) {
        StringBuilder regex = new StringBuilder();
        for (String part : MASK_RUN.split(removeDiacritics(text), -1)) {
            if (!regex.isEmpty()) {
                regex.append("\\p{L}{1,3}");
            }
            regex.append(Pattern.quote(leetChars(part)));
        }
        return Pattern.compile(regex.toString());
    }

    /** Đổi leetspeak khi token có ≥ 2 chữ cái và có số/ký hiệu; ngược lại giữ nguyên. */
    static String leet(String text) {
        int letters = 0;
        boolean hasLeet = false;
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            if (Character.isLetter(c)) {
                letters++;
            } else if (leetChar(c) != c) {
                hasLeet = true;
            }
        }
        return letters >= 2 && hasLeet ? leetChars(text) : text;
    }

    private static String leetChars(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int k = 0; k < text.length(); k++) {
            sb.append(leetChar(text.charAt(k)));
        }
        return sb.toString();
    }

    private static char leetChar(char c) {
        return switch (c) {
            case '0' -> 'o';
            case '1' -> 'i';
            case '3' -> 'e';
            case '4' -> 'a';
            case '5' -> 's';
            case '7' -> 't';
            case '@' -> 'a';
            case '$' -> 's';
            default -> c;
        };
    }
}
