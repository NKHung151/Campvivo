package vn.campvivo.inventory.admin;

import vn.campvivo.catalog.Product;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.inventory.InventoryLog;
import vn.campvivo.inventory.InventoryReason;

import java.time.OffsetDateTime;

/** DTO trang Tồn kho — docs/02-ky-thuat/api.md mục 4.10–4.11 (`InventoryItem`, `InventoryLogEntry`). */
public final class AdminInventoryResponses {

    private AdminInventoryResponses() {
    }

    public enum ItemType { PRODUCT, COMBO }

    /**
     * Một dòng tồn kho. `isLowStock`: khả dụng ≤ `app.inventory.low-stock-threshold` (gồm cả hết hàng).
     * `maxPackable` (chỉ combo): số bộ đóng gói được ngay với hàng lẻ khả dụng hiện tại.
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
            Integer maxPackable
    ) {
        static Item of(Product p, String imageUrl, int threshold) {
            return new Item(ItemType.PRODUCT, p.getId(), p.getName(), p.getSlug(), imageUrl, p.getStockQuantity(),
                    p.getReservedQuantity(), p.getAvailableQuantity(), p.getAvailableQuantity() <= threshold, p.isActive(), null);
        }

        /** Gọi trong transaction (thành phần, sản phẩm nạp lazy). */
        static Item of(ProductCombo c, String imageUrl, int threshold) {
            int packable = c.getItems().stream()
                    .mapToInt(i -> Math.max(0, i.getProduct().getAvailableQuantity()) / i.getQuantity())
                    .min().orElse(0);
            return new Item(ItemType.COMBO, c.getId(), c.getName(), c.getSlug(), imageUrl, c.getStockQuantity(),
                    c.getReservedQuantity(), c.getAvailableQuantity(), c.getAvailableQuantity() <= threshold, c.isActive(),
                    packable);
        }
    }

    public record UserRef(Long id, String fullName) {}

    /** Một dòng nhật ký: mặt hàng, thay đổi (dương nhập / âm xuất), tồn sau thay đổi, lý do, người làm. */
    public record LogEntry(
            Long id,
            ItemType type,
            Long itemId,
            String itemName,
            int changeQuantity,
            int stockAfter,
            InventoryReason reason,
            String note,
            UserRef user,
            OffsetDateTime createdAt
    ) {
        /** Gọi trong transaction (sản phẩm/combo/người dùng nạp lazy). */
        static LogEntry from(InventoryLog l) {
            boolean product = l.getProduct() != null;
            return new LogEntry(l.getId(), product ? ItemType.PRODUCT : ItemType.COMBO,
                    product ? l.getProduct().getId() : l.getCombo().getId(),
                    product ? l.getProduct().getName() : l.getCombo().getName(),
                    l.getChangeQuantity(), l.getStockAfter(), l.getReason(), l.getNote(),
                    new UserRef(l.getUser().getId(), l.getUser().getFullName()), l.getCreatedAt());
        }
    }
}
