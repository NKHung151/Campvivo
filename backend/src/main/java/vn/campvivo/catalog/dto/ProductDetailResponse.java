package vn.campvivo.catalog.dto;

import vn.campvivo.catalog.Category;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductImage;
import vn.campvivo.catalog.ProductOption;
import vn.campvivo.catalog.ProductOptionValue;
import vn.campvivo.catalog.ProductSpec;
import vn.campvivo.promotion.dto.ActiveCampaignRef;
import vn.campvivo.catalog.ProductVariant;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Chi tiết sản phẩm public — GET /products/{slug} (docs/02-ky-thuat/api.md mục 4.3, `ProductDetail`).
 * Biến thể (D-37): `options` rỗng = sản phẩm không có tuỳ chọn, `variants` có đúng 1 biến thể mặc định.
 * Chỉ gửi biến thể đang bán; mọi biến thể cùng giá với sản phẩm.
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
        List<Option> options,
        List<Variant> variants,
        ActiveCampaignRef campaign
) {
    public record Option(String name, List<String> values) {}

    /** @param values giá trị theo thứ tự `options` (rỗng với biến thể mặc định) */
    public record Variant(Long id, String title, List<String> values, int availableQuantity) {}

    public record PathItem(String name, String slug) {}

    public record CategoryRef(Long id, String name, String slug, List<PathItem> path) {}

    public record Image(String url, boolean isPrimary) {}

    /**
     * `path` = các danh mục từ gốc đến danh mục của sản phẩm (gồm chính nó). `campaign` = chiến dịch đang áp (null nếu không có).
     * `price` = giá sau chiến dịch (BR-6, D-30).
     */
    public static ProductDetailResponse from(Product p, List<Category> path, ActiveCampaignRef campaign) {
        List<Image> images = p.getImages().stream().map(i -> new Image(i.getImageUrl(), i.isPrimary())).toList();
        String primary = p.getImages().stream().filter(ProductImage::isPrimary)
                .map(ProductImage::getImageUrl).findFirst().orElse(null);
        Category c = p.getCategory();
        CategoryRef category = new CategoryRef(c.getId(), c.getName(), c.getSlug(),
                path.stream().map(x -> new PathItem(x.getName(), x.getSlug())).toList());
        return new ProductDetailResponse("PRODUCT", p.getId(), p.getName(), p.getSlug(), primary,
                p.getPrice(), p.getSalePrice(), Math.max(0, p.getAvailableQuantity()), p.getAverageRating(),
                p.getReviewCount(), p.getBrand(),
                p.getShortDescription(), p.getDescription(), p.getSpecs(), category, images,
                p.getOptions().stream().sorted(Comparator.comparingInt(ProductOption::getPosition))
                        .map(o -> new Option(o.getName(), o.getValues().stream().map(ProductOptionValue::getValue).toList()))
                        .toList(),
                p.getVariants().stream().filter(ProductVariant::isActive)
                        .sorted(Comparator.comparingInt(ProductVariant::getPosition))
                        .map(v -> new Variant(v.getId(), v.getTitle(),
                                v.getOptionValues().stream().map(ProductOptionValue::getValue).toList(),
                                Math.max(0, v.getAvailableQuantity())))
                        .toList(),
                campaign);
    }
}
