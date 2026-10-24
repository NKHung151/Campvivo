package vn.campvivo.catalog.dto;

import vn.campvivo.catalog.Product;

import java.math.BigDecimal;

/**
 * Sản phẩm/combo trong danh sách public — kiểu `ItemSummary` (docs/02-ky-thuat/api.md mục 3).
 * `price` = giá sau chiến dịch (BR-6); chưa có module khuyến mãi nên bằng `originalPrice`.
 * Đánh giá chưa có (module review) → averageRating null, reviewCount 0.
 */
public record ItemSummary(
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
        String brand
) {
    public static ItemSummary from(Product p, String primaryImageUrl) {
        return new ItemSummary("PRODUCT", p.getId(), p.getName(), p.getSlug(), primaryImageUrl,
                p.getPrice(), p.getPrice(), p.getAvailableQuantity(), null, 0, p.getBrand());
    }
}
