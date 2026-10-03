package vn.campvivo.catalog;

import java.math.BigDecimal;

/**
 * DTO trả ra API — khớp field với docs/02-ky-thuat/api.md (GET /products).
 * Không expose reservedQuantity ra ngoài, chỉ expose stockQuantity như contract đã định.
 */
public record ProductResponse(
        Long id,
        String name,
        String slug,
        BigDecimal price,
        int stockQuantity,
        String primaryImageUrl
) {
    public static ProductResponse from(Product product, String primaryImageUrl) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getPrice(),
                product.getStockQuantity(),
                primaryImageUrl
        );
    }
}
