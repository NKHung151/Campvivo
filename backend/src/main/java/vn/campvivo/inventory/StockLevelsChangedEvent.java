package vn.campvivo.inventory;

import java.util.List;

/**
 * Khả dụng (`stock − reserved`, BR-1) của sản phẩm (tổng mọi biến thể) / combo đã đổi trong một transaction (D-44).
 * Chỉ chứa mặt hàng có khả dụng trước ≠ sau. Phát trong transaction đổi kho — listener nghe AFTER_COMMIT.
 *
 * @param actorId nhân viên gây ra thay đổi (nhập/xuất tay, đóng gói, duyệt trả hàng); null = theo vòng đời đơn hàng
 */
public record StockLevelsChangedEvent(List<Level> levels, Long actorId) {

    public enum ItemType { PRODUCT, COMBO }

    public record Level(ItemType itemType, long itemId, String name, String slug, boolean active,
                        int availableBefore, int availableAfter) {}

    public StockLevelsChangedEvent {
        levels = List.copyOf(levels);
    }
}
