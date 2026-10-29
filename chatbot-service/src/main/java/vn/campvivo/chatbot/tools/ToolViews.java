package vn.campvivo.chatbot.tools;

import vn.campvivo.chatbot.catalog.CatalogModels.ComboDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.Spec;
import vn.campvivo.chatbot.common.util.TextUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dạng JSON gọn trả cho model từ tool (không HTML, tiền là số nguyên VND + chuỗi đã định dạng để model đọc đúng).
 */
public final class ToolViews {

    private static final int SHORT_MAX = 220;

    private ToolViews() {
    }

    public record ProductView(String slug, String name, String brand, long price, String priceText, String category,
                              int availableQuantity, String summary) {

        static ProductView of(ProductDetail p) {
            var c = p.card();
            return new ProductView(c.slug(), c.name(), c.brand(), c.price(), TextUtil.formatVnd(c.price()),
                    c.categoryName(), c.availableQuantity(),
                    TextUtil.truncate(c.shortDescription() != null ? c.shortDescription() : p.descriptionText(),
                            SHORT_MAX));
        }
    }

    public record ProductDetailView(String slug, String name, String brand, long price, String priceText,
                                    String category, int availableQuantity, String description,
                                    Map<String, String> specs) {

        static ProductDetailView of(ProductDetail p) {
            var c = p.card();
            return new ProductDetailView(c.slug(), c.name(), c.brand(), c.price(), TextUtil.formatVnd(c.price()),
                    c.categoryName(), c.availableQuantity(), p.descriptionText(), specMap(p.specs(), 20));
        }
    }

    public record ComboItemView(String name, int quantity, long unitPrice) {}

    public record ComboView(String slug, String name, long price, String priceText, String category,
                            int availableSets, long itemsTotal, String description, List<ComboItemView> items) {

        static ComboView of(ComboDetail c) {
            return new ComboView(c.slug(), c.name(), c.price(), TextUtil.formatVnd(c.price()), c.categoryName(),
                    c.availableQuantity(), c.itemsTotal(), TextUtil.truncate(c.descriptionText(), 400),
                    c.items().stream().map(i -> new ComboItemView(i.productName(), i.quantity(), i.productPrice()))
                            .toList());
        }
    }

    static Map<String, String> specMap(List<Spec> specs, int max) {
        Map<String, String> m = new LinkedHashMap<>();
        specs.stream().limit(max).forEach(s -> m.putIfAbsent(s.name(), s.value()));
        return m;
    }

    /** Kết quả lỗi/thông báo cho model (không ném exception ra model). */
    public record Notice(String notice) {}

    public static Notice tooManyCalls() {
        return new Notice("Đã tra cứu quá nhiều lần cho câu hỏi này. Hãy trả lời khách bằng thông tin đã có.");
    }
}
