package vn.campvivo.inventory.admin;

import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductVariant;
import vn.campvivo.combo.admin.AdminComboResponses;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.inventory.InventoryLog;
import vn.campvivo.inventory.InventoryReason;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/** DTO trang Tồn kho — docs/02-ky-thuat/api.md mục 4.10–4.11 (`InventoryItem`, `InventoryLogEntry`). */
public final class AdminInventoryResponses {

    private AdminInventoryResponses() {
    }

    public enum ItemType { PRODUCT, COMBO }

    /** Thứ tự danh sách tồn kho: khả dụng ít/nhiều trước, tên A→Z, mới cập nhật trước. */
    public enum ItemSort { AVAILABLE_ASC, AVAILABLE_DESC, NAME_ASC, UPDATED_DESC }

    /** Tồn kho một biến thể; `title` null = biến thể mặc định (sản phẩm không có tuỳ chọn). */
    public record VariantRow(
            Long id,
            String title,
            String sku,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity,
            boolean isLowStock,
            boolean isActive
    ) {
        static VariantRow of(ProductVariant v, int threshold) {
            return new VariantRow(v.getId(), v.getTitle(), v.getSku(), v.getStockQuantity(), v.getReservedQuantity(),
                    v.getAvailableQuantity(), v.isActive() && v.getAvailableQuantity() <= threshold, v.isActive());
        }
    }

    /**
     * Một dòng tồn kho. Sản phẩm: số = tồn của sản phẩm (giỏ hàng / đơn hàng dùng số này — D-37), `variants` = từng biến
     * thể, `unallocatedSold` = hàng đã bán qua đơn chưa trừ theo biến thể; `isLowStock` = khả dụng ≤
     * `app.inventory.low-stock-threshold` (gồm hết hàng) hoặc có biến thể đang bán ≤ ngưỡng. Combo: `variants` null,
     * `maxPackable` = số bộ đóng gói được ngay với hàng lẻ khả dụng hiện tại.
     */
    public record Item(
            ItemType type,
            Long id,
            String name,
            String slug,
            String imageUrl,
            int stockQuantity,
            int reservedQuantity,
            int availableQuantity,
            boolean isLowStock,
            boolean isActive,
            Integer maxPackable,
            List<VariantRow> variants,
            int unallocatedSold
    ) {
        /** Gọi trong transaction (biến thể nạp lazy). */
        static Item of(Product p, String imageUrl, int threshold) {
            List<VariantRow> variants = p.getVariants().stream()
                    .sorted(Comparator.comparingInt(ProductVariant::getPosition))
                    .map(v -> VariantRow.of(v, threshold)).toList();
            boolean low = p.getAvailableQuantity() <= threshold
                    || variants.size() > 1 && variants.stream().anyMatch(VariantRow::isLowStock);
            return new Item(ItemType.PRODUCT, p.getId(), p.getName(), p.getSlug(), imageUrl, p.getStockQuantity(),
                    p.getReservedQuantity(), p.getAvailableQuantity(), low, p.isActive(), null, variants,
                    p.getUnallocatedSold());
        }

        /** Gọi trong transaction (thành phần, biến thể nạp lazy). */
        static Item of(ProductCombo c, String imageUrl, int threshold) {
            return new Item(ItemType.COMBO, c.getId(), c.getName(), c.getSlug(), imageUrl, c.getStockQuantity(),
                    c.getReservedQuantity(), c.getAvailableQuantity(), c.getAvailableQuantity() <= threshold, c.isActive(),
                    AdminComboResponses.packableSets(c.getItems()), null, 0);
        }
    }

    public record UserRef(Long id, String fullName) {}

    /**
     * Một dòng nhật ký: mặt hàng, thay đổi (dương nhập / âm xuất), tồn sau thay đổi (của biến thể / combo), lý do,
     * người làm. `variantTitle` null với combo và biến thể mặc định.
     */
    public record LogEntry(
            Long id,
            ItemType type,
            Long itemId,
            String itemName,
            Long variantId,
            String variantTitle,
            int changeQuantity,
            int stockAfter,
            InventoryReason reason,
            String note,
            UserRef user,
            OffsetDateTime createdAt
    ) {
        /** Gọi trong transaction (sản phẩm/biến thể/combo/người dùng nạp lazy). */
        static LogEntry from(InventoryLog l) {
            boolean product = l.getProduct() != null;
            return new LogEntry(l.getId(), product ? ItemType.PRODUCT : ItemType.COMBO,
                    product ? l.getProduct().getId() : l.getCombo().getId(),
                    product ? l.getProduct().getName() : l.getCombo().getName(),
                    product ? l.getVariant().getId() : null,
                    product ? l.getVariant().getTitle() : null,
                    l.getChangeQuantity(), l.getStockAfter(), l.getReason(), l.getNote(),
                    new UserRef(l.getUser().getId(), l.getUser().getFullName()), l.getCreatedAt());
        }
    }
}
