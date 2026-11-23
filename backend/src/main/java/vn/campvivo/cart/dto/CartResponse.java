package vn.campvivo.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Giỏ hàng — kiểu `Cart` (api.md mục 4.4). Giá luôn là giá hiện hành (D-04).
 * `hasProblems` = có dòng ngừng bán hoặc vượt số lượng khả dụng — phải xử lý trước khi đặt hàng.
 */
public record CartResponse(List<Line> items, BigDecimal subtotal, int itemCount, boolean hasProblems) {

    /**
     * `variant` = biến thể khách chọn (D-40), null với combo. Với dòng sản phẩm, `item.availableQuantity` và
     * `item.isActive` tính theo biến thể đó.
     */
    public record Line(Long id, Item item, Variant variant, int quantity, BigDecimal lineTotal) {}

    /** `title` = "Tím / M", null với biến thể mặc định (sản phẩm không có tuỳ chọn). */
    public record Variant(Long id, String title) {}

    /** `ItemSummary` + `isActive` (api.md mục 3). */
    public record Item(
            String type,
            Long id,
            String name,
            String slug,
            String primaryImageUrl,
            BigDecimal originalPrice,
            BigDecimal price,
            int availableQuantity,
            Double averageRating,
            long reviewCount,
            String brand,
            boolean isActive
    ) {}
}
