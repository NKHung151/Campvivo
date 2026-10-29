package vn.campvivo.chatbot.catalog;

import vn.campvivo.chatbot.catalog.CatalogModels.CategoryNode;
import vn.campvivo.chatbot.catalog.CatalogModels.ComboDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductDetail;
import vn.campvivo.chatbot.catalog.CatalogModels.ProductFilter;

import java.util.List;
import java.util.Optional;

/**
 * Cổng đọc catalog của chatbot (chỉ đọc). Hiện cài bằng JDBC trên DB dùng chung ({@code JdbcCatalogReader});
 * khi gộp vào backend (D-28) thay bằng adapter gọi service public của module catalog/combo. Chỉ trả mặt hàng
 * đang bán (`is_active`).
 */
public interface CatalogReadPort {

    List<CategoryNode> categories();

    /** Kết quả gồm thông số (dùng cho tư vấn: sức chứa lều, mùa túi ngủ...). */
    List<ProductDetail> searchProducts(ProductFilter filter);

    Optional<ProductDetail> findProduct(String slug);

    List<ProductDetail> findProducts(List<String> slugs);

    /** Combo đang bán; `text` / `queryEmbedding` / `maxPrice` null = không lọc. */
    List<ComboDetail> searchCombos(String text, float[] queryEmbedding, Long maxPrice, boolean inStockOnly, int limit);

    Optional<ComboDetail> findCombo(String slug);

    /** Toàn bộ sản phẩm đang bán (job embedding). */
    List<ProductDetail> allActiveProducts();

    /** Toàn bộ combo đang bán, kể cả hết bộ (job embedding). */
    List<ComboDetail> allActiveCombos();
}
