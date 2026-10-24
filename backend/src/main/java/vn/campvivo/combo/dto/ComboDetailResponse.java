package vn.campvivo.combo.dto;

import vn.campvivo.catalog.dto.ItemSummary;
import vn.campvivo.combo.ComboImage;
import vn.campvivo.combo.ProductCombo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Chi tiết combo public — GET /combos/{slug} (docs/02-ky-thuat/api.md mục 4.3, `ComboDetail`).
 * `items`: sản phẩm thành phần (dạng ItemSummary) + số lượng trong một bộ. `images` (D-26): ảnh tự tải lên của combo
 * (ảnh chính trước) rồi ảnh chính của các sản phẩm thành phần theo thứ tự dòng, bỏ trùng; ảnh đầu là ảnh đại diện.
 */
public record ComboDetailResponse(
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
        String description,
        CategoryRef category,
        List<Image> images,
        List<Item> items
) {
    public record CategoryRef(Long id, String name, String slug) {}

    public record Image(String url, boolean isPrimary) {}

    public record Item(ItemSummary product, int quantity) {}

    public static ComboDetailResponse from(ProductCombo c, List<Item> items) {
        List<String> urls = Stream.concat(
                        Stream.concat(c.getImages().stream().filter(ComboImage::isPrimary), c.getImages().stream().filter(i -> !i.isPrimary()))
                                .map(ComboImage::getImageUrl),
                        items.stream().map(i -> i.product().primaryImageUrl()))
                .filter(Objects::nonNull).distinct().toList();
        List<Image> images = IntStream.range(0, urls.size()).mapToObj(i -> new Image(urls.get(i), i == 0)).toList();
        String primary = urls.isEmpty() ? null : urls.get(0);
        return new ComboDetailResponse("COMBO", c.getId(), c.getName(), c.getSlug(), primary, c.getComboPrice(), c.getComboPrice(),
                Math.max(0, c.getAvailableQuantity()), null, 0, c.getItemsTotal(), c.getDescription(),
                new CategoryRef(c.getCategory().getId(), c.getCategory().getName(), c.getCategory().getSlug()), images, items);
    }
}
