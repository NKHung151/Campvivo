package vn.campvivo.catalog.dto;

import vn.campvivo.catalog.Product;

import java.math.BigDecimal;

/**
 * Sản phẩm/combo trong danh sách public — kiểu `ItemSummary` (docs/02-ky-thuat/api.md mục 3).
 * `price` = giá sau chiến dịch (BR-6, `Product.salePrice` — D-30), `originalPrice` = giá gốc.
 * `averageRating`/`reviewCount` từ `Product` (@Formula, D-35); chưa có đánh giá → null / 0.
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
                p.getPrice(), p.getSalePrice(), p.getAvailableQuantity(), p.getAverageRating(), p.getReviewCount(),
                p.getBrand());
    }
}
