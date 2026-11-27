package vn.campvivo.inventory;

import org.springframework.context.ApplicationEventPublisher;
import vn.campvivo.catalog.Product;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.inventory.StockLevelsChangedEvent.ItemType;
import vn.campvivo.inventory.StockLevelsChangedEvent.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chụp khả dụng của sản phẩm / combo ĐÃ KHOÁ trước khi đổi kho, rồi phát {@link StockLevelsChangedEvent} cho những mặt
 * hàng có khả dụng thay đổi (D-44). Dùng chính entity đã khoá — không thêm truy vấn, không đổi thứ tự khoá.
 * Không phải bean: tạo một cái cho mỗi lần đổi kho.
 */
public final class StockLevelSnapshot {

    private final Map<Product, Integer> products = new LinkedHashMap<>();
    private final Map<ProductCombo, Integer> combos = new LinkedHashMap<>();

    public StockLevelSnapshot products(Collection<Product> items) {
        items.forEach(p -> products.putIfAbsent(p, p.getAvailableQuantity()));
        return this;
    }

    public StockLevelSnapshot combos(Collection<ProductCombo> items) {
        items.forEach(c -> combos.putIfAbsent(c, c.getAvailableQuantity()));
        return this;
    }

    /** Mặt hàng có khả dụng sau ≠ trước. */
    public List<Level> changed() {
        List<Level> out = new ArrayList<>();
        products.forEach((p, before) -> {
            if (p.getAvailableQuantity() != before) {
                out.add(new Level(ItemType.PRODUCT, p.getId(), p.getName(), p.getSlug(), p.isActive(), before,
                        p.getAvailableQuantity()));
            }
        });
        combos.forEach((c, before) -> {
            if (c.getAvailableQuantity() != before) {
                out.add(new Level(ItemType.COMBO, c.getId(), c.getName(), c.getSlug(), c.isActive(), before,
                        c.getAvailableQuantity()));
            }
        });
        return out;
    }

    /** Phát sự kiện nếu có mặt hàng đổi khả dụng. Gọi trong transaction đổi kho, sau khi đã đổi. */
    public void publish(ApplicationEventPublisher events, Long actorId) {
        List<Level> levels = changed();
        if (!levels.isEmpty()) {
            events.publishEvent(new StockLevelsChangedEvent(levels, actorId));
        }
    }
}
