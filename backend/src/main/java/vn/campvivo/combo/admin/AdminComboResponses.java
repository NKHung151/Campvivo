package vn.campvivo.combo.admin;

import vn.campvivo.catalog.Product;
import vn.campvivo.combo.ComboImage;
import vn.campvivo.combo.ComboItem;
import vn.campvivo.combo.ProductCombo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Response admin combo — khớp frontend/src/types/api/catalog.ts (`AdminComboSummary`, `AdminComboDetail`). */
public final class AdminComboResponses {

    private AdminComboResponses() {
    }

    /**
     * @param itemsTotal tổng giá lẻ hiện tại của các sản phẩm thành phần (giá × số lượng) — để thấy combo rẻ hơn bao nhiêu
     * @param itemCount  số dòng sản phẩm trong combo
     */
    public record Summary(
            Long id,
            String name,
            String slug,
            Long categoryId,
            String categoryName,
            List<String> categoryPath,
            BigDecimal comboPrice,
            BigDecimal itemsTotal,
            int itemCount,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity,
            boolean isActive,
            String primaryImageUrl,
            OffsetDateTime updatedAt
    ) {
        /** Gọi trong transaction (thành phần, sản phẩm nạp lazy). */
        public static Summary from(ProductCombo c, String primaryImageUrl, List<String> categoryPath) {
            return new Summary(c.getId(), c.getName(), c.getSlug(), c.getCategory().getId(), c.getCategory().getName(),
                    categoryPath, c.getComboPrice(), c.getItemsTotal(), c.getItems().size(), c.getStockQuantity(),
                    c.getReservedQuantity(), c.getAvailableQuantity(), c.isActive(), primaryImageUrl, c.getUpdatedAt());
        }
    }

    /** Sản phẩm thành phần kèm thông tin hiển thị; `productAvailableQuantity` để biết đóng gói được bao nhiêu bộ. */
    public record Image(String url, boolean isPrimary, int displayOrder) {
        static Image from(ComboImage i) {
            return new Image(i.getImageUrl(), i.isPrimary(), i.getDisplayOrder());
        }
    }

    public record Item(Long productId, String productName, String productSlug, String productImageUrl,
                       BigDecimal productPrice, boolean productActive, int productAvailableQuantity, int quantity) {
        static Item from(ComboItem i, String imageUrl) {
            Product p = i.getProduct();
            return new Item(p.getId(), p.getName(), p.getSlug(), imageUrl, p.getPrice(), p.isActive(),
                    p.getAvailableQuantity(), i.getQuantity());
        }
    }

    /** Số bộ đóng gói được với tồn kho khả dụng hiện tại của các sản phẩm thành phần. */
    static int packableSets(List<ComboItem> items) {
        return items.stream()
                .mapToInt(i -> Math.max(0, i.getProduct().getAvailableQuantity()) / i.getQuantity())
                .min().orElse(0);
    }

    public record Detail(
            Long id,
            String name,
            String slug,
            Long categoryId,
            String categoryName,
            BigDecimal comboPrice,
            BigDecimal itemsTotal,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity,
            /* Số bộ còn đóng gói được ngay = min(khả dụng sản phẩm ÷ số lượng trong bộ) */
            int maxPackable,
            boolean isActive,
            String primaryImageUrl,
            OffsetDateTime updatedAt,
            String description,
            List<Item> items,
            /* Ảnh tự tải lên (sửa được trong form); ảnh sản phẩm thành phần nằm ở items[].productImageUrl */
            List<Image> images,
            OffsetDateTime createdAt
    ) {
        /**
         * `itemImages`: ảnh chính của từng sản phẩm thành phần, theo thứ tự `items`. Ảnh đại diện (D-26): ảnh chính tự tải
         * lên, chưa có thì ảnh sản phẩm thành phần đầu tiên có ảnh.
         */
        public static Detail from(ProductCombo c, List<String> itemImages) {
            List<Image> images = c.getImages().stream().map(Image::from).toList();
            String primary = images.stream().filter(Image::isPrimary).map(Image::url).findFirst()
                    .orElse(itemImages.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null));
            List<ComboItem> items = c.getItems();
            List<Item> itemDtos = new java.util.ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                itemDtos.add(Item.from(items.get(i), itemImages.get(i)));
            }
            return new Detail(c.getId(), c.getName(), c.getSlug(), c.getCategory().getId(), c.getCategory().getName(),
                    c.getComboPrice(), c.getItemsTotal(), c.getStockQuantity(), c.getReservedQuantity(),
                    c.getAvailableQuantity(), packableSets(items), c.isActive(), primary, c.getUpdatedAt(), c.getDescription(), itemDtos,
                    images, c.getCreatedAt());
        }
    }
}
