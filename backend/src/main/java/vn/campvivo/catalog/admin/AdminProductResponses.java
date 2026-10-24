package vn.campvivo.catalog.admin;

import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductImage;
import vn.campvivo.catalog.ProductSpec;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response admin sản phẩm — khớp docs/02-ky-thuat/api.md mục 4.11 và frontend/src/types/api/catalog.ts
 * (`AdminProductSummary`, `AdminProductDetail`).
 */
public final class AdminProductResponses {

    private AdminProductResponses() {}

    /**
     * @param categoryPath     tên danh mục từ gốc tới danh mục của sản phẩm, vd. ["Đạp Xe", "Xe đạp"]
     * @param availableQuantity stock − reserved (BR-1)
     */
    public record Summary(
            Long id,
            String name,
            String slug,
            String brand,
            Long categoryId,
            String categoryName,
            List<String> categoryPath,
            BigDecimal price,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity,
            boolean isActive,
            String primaryImageUrl,
            OffsetDateTime updatedAt
    ) {
        public static Summary from(Product p, String primaryImageUrl, List<String> categoryPath) {
            return new Summary(p.getId(), p.getName(), p.getSlug(), p.getBrand(), p.getCategory().getId(),
                    p.getCategory().getName(), categoryPath, p.getPrice(), p.getStockQuantity(),
                    p.getReservedQuantity(), p.getAvailableQuantity(), p.isActive(), primaryImageUrl, p.getUpdatedAt());
        }
    }

    public record Image(String url, boolean isPrimary, int displayOrder) {
        static Image from(ProductImage i) {
            return new Image(i.getImageUrl(), i.isPrimary(), i.getDisplayOrder());
        }
    }

    public record Detail(
            Long id,
            String name,
            String slug,
            String brand,
            Long categoryId,
            String categoryName,
            BigDecimal price,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity,
            boolean isActive,
            String primaryImageUrl,
            OffsetDateTime updatedAt,
            String shortDescription,
            String description,
            List<ProductSpec> specs,
            List<Image> images,
            OffsetDateTime createdAt
    ) {
        /** Gọi trong transaction (ảnh nạp lazy). */
        public static Detail from(Product p) {
            List<Image> images = p.getImages().stream().map(Image::from).toList();
            String primary = images.stream().filter(Image::isPrimary).map(Image::url).findFirst().orElse(null);
            return new Detail(p.getId(), p.getName(), p.getSlug(), p.getBrand(), p.getCategory().getId(),
                    p.getCategory().getName(), p.getPrice(), p.getStockQuantity(), p.getReservedQuantity(),
                    p.getAvailableQuantity(), p.isActive(), primary, p.getUpdatedAt(), p.getShortDescription(),
                    p.getDescription(), List.copyOf(p.getSpecs()), images, p.getCreatedAt());
        }
    }
}
