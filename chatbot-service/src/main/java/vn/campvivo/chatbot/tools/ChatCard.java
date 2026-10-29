package vn.campvivo.chatbot.tools;

import vn.campvivo.chatbot.catalog.CatalogModels.ComboDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductCard;

/**
 * Thẻ sản phẩm/combo gửi kèm câu trả lời (api.md 4.12 `ChatCard`) — dữ liệu lấy từ DB, không từ chữ của model.
 */
public record ChatCard(String type, long id, String slug, String name, long price, String primaryImageUrl,
                       int availableQuantity, Integer suggestedQuantity, String note) {

    public static ChatCard product(ProductCard p, Integer suggestedQuantity, String note) {
        return new ChatCard("PRODUCT", p.id(), p.slug(), p.name(), p.price(), p.primaryImageUrl(),
                p.availableQuantity(), suggestedQuantity, note);
    }

    public static ChatCard combo(ComboDetail c, Integer suggestedQuantity, String note) {
        return new ChatCard("COMBO", c.id(), c.slug(), c.name(), c.price(), c.primaryImageUrl(),
                c.availableQuantity(), suggestedQuantity, note);
    }

    String key() {
        return type + ":" + id;
    }
}
