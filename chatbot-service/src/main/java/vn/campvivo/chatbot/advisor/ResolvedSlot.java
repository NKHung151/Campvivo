package vn.campvivo.chatbot.advisor;

import vn.campvivo.chatbot.advisor.TripTemplateProperties.Slot;
import vn.campvivo.chatbot.catalog.CatalogModels.CategoryNode;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Slot của mẫu nhu cầu đã đổi slug danh mục → tiền tố `categories.path` theo DB hiện tại. Slug không còn trong DB
 * thì bị bỏ qua (admin đổi danh mục) — slot không còn danh mục nào thì coi như không có hàng.
 */
public record ResolvedSlot(Slot slot, List<String> paths, List<String> excludePaths, List<String> winterPaths) {

    public static ResolvedSlot of(Slot slot, Map<String, CategoryNode> bySlug) {
        return new ResolvedSlot(slot, toPaths(slot.categories(), bySlug), toPaths(slot.exclude(), bySlug),
                toPaths(slot.winterCategories(), bySlug));
    }

    public String key() {
        return slot.key();
    }

    public String label() {
        return slot.label();
    }

    public boolean matches(String categoryPath) {
        return startsWithAny(categoryPath, paths) && !startsWithAny(categoryPath, excludePaths);
    }

    public boolean isWinter(String categoryPath) {
        return startsWithAny(categoryPath, winterPaths);
    }

    /** "đã có lều rồi" → true với slot có alias "lều". So không dấu, không phân biệt hoa thường. */
    public boolean mentionedIn(String text) {
        String t = normalize(text);
        return t.contains(normalize(slot.label()))
                || slot.aliases().stream().map(ResolvedSlot::normalize).anyMatch(a -> !a.isBlank() && t.contains(a));
    }

    /** Tên sản phẩm lẻ có hợp với slot không (theo `nameKeywords`; không khai báo thì luôn hợp). */
    public boolean acceptsName(String productName) {
        if (slot.nameKeywords().isEmpty()) {
            return true;
        }
        String name = normalize(productName);
        return slot.nameKeywords().stream().map(ResolvedSlot::normalize).anyMatch(name::contains);
    }

    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String noMarks = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noMarks.replace('đ', 'd').replaceAll("\\s+", " ").trim();
    }

    private static boolean startsWithAny(String path, List<String> prefixes) {
        return path != null && prefixes.stream().anyMatch(path::startsWith);
    }

    private static List<String> toPaths(List<String> slugs, Map<String, CategoryNode> bySlug) {
        return slugs.stream().map(bySlug::get).filter(Objects::nonNull).map(CategoryNode::path).toList();
    }
}
