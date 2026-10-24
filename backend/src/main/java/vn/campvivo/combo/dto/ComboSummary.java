package vn.campvivo.combo.dto;

import vn.campvivo.combo.ProductCombo;

import java.math.BigDecimal;

/**
 * Combo trong danh sách public — kiểu `ItemSummary` (docs/02-ky-thuat/api.md mục 3, type = COMBO) cộng thêm
 * `itemsTotal` (tổng giá lẻ hiện tại), `itemCount` và danh mục cấp 1 để hiện "rẻ hơn mua lẻ". Chưa có khuyến mãi/đánh giá.
 */
public record ComboSummary(
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
        BigDecimal itemsTotal,
        int itemCount,
        ComboDetailResponse.CategoryRef category
) {
    public static ComboSummary from(ProductCombo c, String primaryImageUrl) {
        return new ComboSummary("COMBO", c.getId(), c.getName(), c.getSlug(), primaryImageUrl, c.getComboPrice(),
                c.getComboPrice(), Math.max(0, c.getAvailableQuantity()), null, 0, c.getItemsTotal(), c.getItems().size(),
                new ComboDetailResponse.CategoryRef(c.getCategory().getId(), c.getCategory().getName(), c.getCategory().getSlug()));
    }
}
