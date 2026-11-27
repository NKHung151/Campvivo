package vn.campvivo.notification.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.inventory.InventoryService;
import vn.campvivo.inventory.InventoryService.StockItems;
import vn.campvivo.inventory.StockLevelsChangedEvent;
import vn.campvivo.inventory.StockLevelsChangedEvent.ItemType;
import vn.campvivo.inventory.StockLevelsChangedEvent.Level;
import vn.campvivo.notification.NotificationDispatcher;
import vn.campvivo.notification.NotificationMessages;
import vn.campvivo.notification.NotificationRefType;
import vn.campvivo.notification.NotificationService;
import vn.campvivo.notification.NotificationType;

import java.util.List;
import java.util.Set;

/**
 * `LOW_STOCK` (D-44), chạy SAU commit trên {@link NotificationDispatcher}. Với mỗi mặt hàng trong sự kiện, quyết định theo
 * khả dụng HIỆN TẠI (đọc lại — hai lần đổi kho cùng mặt hàng có thể tới dispatcher ngược thứ tự commit, đọc lại thì lần xử lý
 * cuối luôn đúng với DB):
 * <ul>
 *   <li>&gt; ngưỡng → đánh dấu `LOW_STOCK` đang mở đã xử lý (người xử lý = nhân viên gây ra thay đổi; null = Hệ thống, vd.
 *       huỷ đơn trả lại giữ chỗ);</li>
 *   <li>≤ ngưỡng, mặt hàng đang bán và lần đổi này làm GIẢM khả dụng → báo / gộp vào dòng theo mặt hàng ("Còn N", chưa đọc
 *       lại);</li>
 *   <li>≤ ngưỡng nhưng khả dụng TĂNG (vd. huỷ đơn trả giữ chỗ) hoặc mặt hàng đã tắt → chỉ sửa số trên dòng đang mở, không báo
 *       lại.</li>
 * </ul>
 * Người gây ra thay đổi không nhận thông báo của chính mình (D-42).
 */
@Component
public class StockNotificationListener {

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;
    private final InventoryService inventory;
    private final int threshold;

    public StockNotificationListener(NotificationService notifications, NotificationDispatcher dispatcher,
                                     InventoryService inventory, AppProperties appProperties) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
        this.inventory = inventory;
        this.threshold = appProperties.inventory().lowStockThreshold();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockLevelsChanged(StockLevelsChangedEvent event) {
        dispatcher.submit("LOW_STOCK " + event.levels().size() + " mặt hàng", () -> handle(event));
    }

    private void handle(StockLevelsChangedEvent event) {
        List<Long> productIds = idsOf(event, ItemType.PRODUCT);
        List<Long> comboIds = idsOf(event, ItemType.COMBO);
        StockItems current = inventory.load(productIds, comboIds, Set.of());
        for (Level level : event.levels()) {
            Current now = current(level, current);
            if (now == null) {
                continue;
            }
            NotificationRefType refType = level.itemType() == ItemType.COMBO
                    ? NotificationRefType.COMBO : NotificationRefType.PRODUCT;
            if (now.available() > threshold) {
                notifications.resolve(NotificationType.LOW_STOCK, refType, level.itemId(), event.actorId());
                continue;
            }
            var message = NotificationMessages.lowStock(refType, level.itemId(), now.name(), now.available(), threshold);
            if (now.active() && level.availableAfter() < level.availableBefore()) {
                notifications.notifyStaff(message, event.actorId());
            } else {
                notifications.refreshGroup(message);
            }
        }
    }

    private record Current(String name, boolean active, int available) {}

    private static Current current(Level level, StockItems items) {
        if (level.itemType() == ItemType.COMBO) {
            var c = items.combos().get(level.itemId());
            return c == null ? null : new Current(c.getName(), c.isActive(), c.getAvailableQuantity());
        }
        var p = items.products().get(level.itemId());
        return p == null ? null : new Current(p.getName(), p.isActive(), p.getAvailableQuantity());
    }

    private static List<Long> idsOf(StockLevelsChangedEvent event, ItemType type) {
        return event.levels().stream().filter(l -> l.itemType() == type).map(Level::itemId).distinct().toList();
    }
}
