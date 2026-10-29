package vn.campvivo.chatbot.advisor;

import vn.campvivo.chatbot.catalog.CatalogModels.Spec;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc sức chứa (số người) của lều từ tên hoặc thông số: "Lều 4 người", "2-3 người" (lấy số lớn), "Sức chứa: 2".
 */
public final class CapacityParser {

    private static final Pattern PEOPLE = Pattern.compile("(\\d{1,2})\\s*(?:-\\s*(\\d{1,2})\\s*)?ng(?:ư|u)(?:ờ|o)i",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NUMBER = Pattern.compile("(\\d{1,2})");

    private CapacityParser() {
    }

    public static OptionalInt capacity(String name, List<Spec> specs) {
        OptionalInt fromName = fromText(name);
        if (fromName.isPresent()) {
            return fromName;
        }
        for (Spec s : specs) {
            String key = s.name() == null ? "" : s.name().toLowerCase(Locale.ROOT);
            if (key.contains("sức chứa") || key.contains("số người") || key.contains("capacity")) {
                OptionalInt v = fromText(s.value());
                if (v.isPresent()) {
                    return v;
                }
                Matcher m = NUMBER.matcher(s.value() == null ? "" : s.value());
                if (m.find()) {
                    return OptionalInt.of(Integer.parseInt(m.group(1)));
                }
            }
        }
        return OptionalInt.empty();
    }

    static OptionalInt fromText(String text) {
        if (text == null) {
            return OptionalInt.empty();
        }
        Matcher m = PEOPLE.matcher(text);
        if (!m.find()) {
            return OptionalInt.empty();
        }
        int value = Integer.parseInt(m.group(2) != null ? m.group(2) : m.group(1));
        return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
    }
}
