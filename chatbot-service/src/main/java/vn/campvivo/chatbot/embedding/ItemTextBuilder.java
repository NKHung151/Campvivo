package vn.campvivo.chatbot.embedding;

import vn.campvivo.chatbot.catalog.CatalogModels.ComboDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.Spec;
import vn.campvivo.chatbot.common.util.TextUtil;

import java.util.stream.Collectors;

/**
 * Văn bản đại diện một sản phẩm/combo để embed (tìm kiếm ngữ nghĩa). Không gồm giá/tồn kho — các giá trị này lọc
 * bằng SQL lúc tìm nên đổi giá không phải embed lại.
 */
public final class ItemTextBuilder {

    private static final int MAX_SPECS = 10;
    private static final int MAX_CHARS = 2000;

    private ItemTextBuilder() {
    }

    public static String product(ProductDetail p) {
        var c = p.card();
        String specs = p.specs().stream().limit(MAX_SPECS)
                .map(ItemTextBuilder::spec).collect(Collectors.joining("; "));
        String text = String.join("\n",
                c.name(),
                "Thương hiệu: " + nullToEmpty(c.brand()),
                "Danh mục: " + c.categoryName(),
                nullToEmpty(c.shortDescription()),
                specs.isEmpty() ? "" : "Thông số: " + specs);
        return TextUtil.truncate(text.strip(), MAX_CHARS);
    }

    public static String combo(ComboDetail c) {
        String items = c.items().stream()
                .map(i -> i.quantity() + " x " + i.productName())
                .collect(Collectors.joining("; "));
        String text = String.join("\n",
                "Combo: " + c.name(),
                "Danh mục: " + c.categoryName(),
                c.descriptionText(),
                "Gồm: " + items);
        return TextUtil.truncate(text.strip(), MAX_CHARS);
    }

    private static String spec(Spec s) {
        return s.name() + ": " + s.value();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
