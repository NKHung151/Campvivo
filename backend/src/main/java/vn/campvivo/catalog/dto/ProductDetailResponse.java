package vn.campvivo.catalog.dto;

import vn.campvivo.catalog.Category;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductImage;
import vn.campvivo.catalog.ProductSpec;

import java.math.BigDecimal;
import java.util.List;

/**
 * Chi tiết sản phẩm public — GET /products/{slug} (docs/02-ky-thuat/api.md mục 4.3, `ProductDetail`).
 */
public record ProductDetailResponse(
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
        String shortDescription,
        String description,
        List<ProductSpec> specs,
        CategoryRef category,
        List<Image> images,
        Object campaign
) {
    public record PathItem(String name, String slug) {}

    public record CategoryRef(Long id, String name, String slug, List<PathItem> path) {}

    public record Image(String url, boolean isPrimary) {}

    /** `path` = các danh mục từ gốc đến danh mục của sản phẩm (gồm chính nó). */
    public static ProductDetailResponse from(Product p, List<Category> path) {
        List<Image> images = p.getImages().stream().map(i -> new Image(i.getImageUrl(), i.isPrimary())).toList();
        String primary = p.getImages().stream().filter(ProductImage::isPrimary)
                .map(ProductImage::getImageUrl).findFirst().orElse(null);
        Category c = p.getCategory();
        CategoryRef category = new CategoryRef(c.getId(), c.getName(), c.getSlug(),
                path.stream().map(x -> new PathItem(x.getName(), x.getSlug())).toList());
        return new ProductDetailResponse("PRODUCT", p.getId(), p.getName(), p.getSlug(), primary,
                p.getPrice(), p.getPrice(), Math.max(0, p.getAvailableQuantity()), null, 0, p.getBrand(),
                p.getShortDescription(), p.getDescription(), p.getSpecs(), category, images, null);
    }
}
