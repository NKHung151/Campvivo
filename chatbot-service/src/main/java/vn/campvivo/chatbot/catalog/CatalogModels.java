package vn.campvivo.chatbot.catalog;

import java.util.List;

/**
 * Dữ liệu catalog chatbot đọc từ DB (chỉ đọc). Tiền là số nguyên VND. `availableQuantity` = stock − reserved (BR-1).
 */
public final class CatalogModels {

    private CatalogModels() {
    }

    public record CategoryNode(long id, Long parentId, String name, String slug, String path) {}

    public record Spec(String name, String value) {}

    /** Sản phẩm dạng gọn — dùng cho danh sách / thẻ kết quả. */
    public record ProductCard(long id, String slug, String name, String brand, long price, String categoryName,
                              String categorySlug, String categoryPath, int availableQuantity,
                              String primaryImageUrl, String shortDescription) {}

    /** Sản phẩm đầy đủ — thêm thông số và mô tả đã bỏ HTML. */
    public record ProductDetail(ProductCard card, List<Spec> specs, String descriptionText) {}

    public record ComboItem(long productId, String productSlug, String productName, String categorySlug,
                            String categoryPath, long productPrice, int productAvailableQuantity, int quantity,
                            List<Spec> specs) {}

    public record ComboDetail(long id, String slug, String name, long price, String categoryName,
                              String categorySlug, int availableQuantity, String primaryImageUrl,
                              String descriptionText, List<ComboItem> items) {

        /** Tổng giá nếu mua lẻ từng thành phần (để nói "tiết kiệm bao nhiêu"). */
        public long itemsTotal() {
            return items.stream().mapToLong(i -> i.productPrice() * i.quantity()).sum();
        }
    }

    public enum ProductSort { RELEVANCE, PRICE_ASC, PRICE_DESC, NEWEST }

    /**
     * Bộ lọc tìm sản phẩm. Trường null = không lọc. `categoryPaths`: các tiền tố `categories.path` (lấy cả danh mục
     * con); `excludePaths`: tiền tố bị loại.
     */
    public record ProductFilter(String text, float[] queryEmbedding, List<String> categoryPaths,
                                List<String> excludePaths, String brand, Long minPrice, Long maxPrice,
                                boolean inStockOnly, ProductSort sort, int limit) {}
}
