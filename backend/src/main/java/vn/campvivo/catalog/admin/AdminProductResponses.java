package vn.campvivo.catalog.admin;

import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductImage;
import vn.campvivo.catalog.ProductOption;
import vn.campvivo.catalog.ProductOptionValue;
import vn.campvivo.catalog.ProductVariant;
import vn.campvivo.catalog.ProductSpec;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
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
     * @param variants         tồn kho từng biến thể (rỗng khi sản phẩm không có tuỳ chọn — D-37)
     * @param unallocatedSold  hàng đã bán theo đơn chưa trừ vào biến thể nào (D-37)
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
            int variantCount,
            List<VariantStock> variants,
            int unallocatedSold,
            boolean isActive,
            String primaryImageUrl,
            OffsetDateTime updatedAt
    ) {
        /** Gọi trong transaction (biến thể + giá trị tuỳ chọn nạp lazy theo lô). */
        public static Summary from(Product p, String primaryImageUrl, List<String> categoryPath) {
            List<VariantStock> variants = p.getVariants().size() <= 1 ? List.of()
                    : p.getVariants().stream().sorted(Comparator.comparingInt(ProductVariant::getPosition))
                            .map(VariantStock::from).toList();
            return new Summary(p.getId(), p.getName(), p.getSlug(), p.getBrand(), p.getCategory().getId(),
                    p.getCategory().getName(), categoryPath, p.getPrice(), p.getStockQuantity(),
                    p.getReservedQuantity(), p.getAvailableQuantity(), p.getVariants().size(), variants,
                    p.getUnallocatedSold(), p.isActive(), primaryImageUrl, p.getUpdatedAt());
        }
    }

    /** Tồn kho một biến thể ở danh sách sản phẩm; title vd. "Tím / S/M". */
    public record VariantStock(Long id, String title, int stockQuantity, int availableQuantity, boolean isActive) {
        static VariantStock from(ProductVariant v) {
            return new VariantStock(v.getId(), v.getTitle(), v.getStockQuantity(), v.getAvailableQuantity(),
                    v.isActive());
        }
    }

    public record Image(String url, boolean isPrimary, int displayOrder) {
        static Image from(ProductImage i) {
            return new Image(i.getImageUrl(), i.isPrimary(), i.getDisplayOrder());
        }
    }

    public record OptionValue(Long id, String value) {}

    public record Option(Long id, String name, int position, List<OptionValue> values) {
        static Option from(ProductOption o) {
            return new Option(o.getId(), o.getName(), o.getPosition(),
                    o.getValues().stream().sorted(Comparator.comparingInt(ProductOptionValue::getPosition))
                            .map(v -> new OptionValue(v.getId(), v.getValue())).toList());
        }
    }

    /** @param values giá trị theo thứ tự tuỳ chọn (rỗng với biến thể mặc định); title vd. "Tím / S/M" */
    public record Variant(Long id, String title, List<String> values, String sku, int stockQuantity,
                          int reservedQuantity, int availableQuantity, boolean isActive) {
        public static Variant from(ProductVariant v) {
            return new Variant(v.getId(), v.getTitle(),
                    v.getOptionValues().stream().map(ProductOptionValue::getValue).toList(), v.getSku(),
                    v.getStockQuantity(), v.getReservedQuantity(), v.getAvailableQuantity(), v.isActive());
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
            List<Option> options,
            List<Variant> variants,
            OffsetDateTime createdAt
    ) {
        /** Gọi trong transaction (ảnh, tuỳ chọn, biến thể nạp lazy). */
        public static Detail from(Product p) {
            List<Image> images = p.getImages().stream().map(Image::from).toList();
            String primary = images.stream().filter(Image::isPrimary).map(Image::url).findFirst().orElse(null);
            return new Detail(p.getId(), p.getName(), p.getSlug(), p.getBrand(), p.getCategory().getId(),
                    p.getCategory().getName(), p.getPrice(), p.getStockQuantity(), p.getReservedQuantity(),
                    p.getAvailableQuantity(), p.isActive(), primary, p.getUpdatedAt(), p.getShortDescription(),
                    p.getDescription(), List.copyOf(p.getSpecs()), images,
                    p.getOptions().stream().sorted(Comparator.comparingInt(ProductOption::getPosition))
                            .map(Option::from).toList(),
                    p.getVariants().stream().sorted(Comparator.comparingInt(ProductVariant::getPosition))
                            .map(Variant::from).toList(), p.getCreatedAt());
        }
    }
}
