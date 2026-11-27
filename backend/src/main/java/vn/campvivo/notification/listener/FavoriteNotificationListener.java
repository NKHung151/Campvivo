package vn.campvivo.notification.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.campvivo.catalog.ProductService;
import vn.campvivo.catalog.dto.ItemSummary;
import vn.campvivo.favorite.FavoriteService;
import vn.campvivo.favorite.FavoriteType;
import vn.campvivo.inventory.InventoryService;
import vn.campvivo.inventory.InventoryService.StockItems;
import vn.campvivo.inventory.StockLevelsChangedEvent;
import vn.campvivo.inventory.StockLevelsChangedEvent.ItemType;
import vn.campvivo.inventory.StockLevelsChangedEvent.Level;
import vn.campvivo.notification.NotificationDispatcher;
import vn.campvivo.notification.NotificationMessage;
import vn.campvivo.notification.NotificationMessages;
import vn.campvivo.notification.NotificationRefType;
import vn.campvivo.notification.NotificationService;
import vn.campvivo.promotion.CampaignStartedEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Thông báo cho khách yêu thích mặt hàng (D-42, D-47), chạy SAU commit trên {@link NotificationDispatcher}. Người nhận = khách
 * đang hoạt động có dòng yêu thích ({@link FavoriteService#customersFavoriting}).
 * <ul>
 *   <li>`FAVORITE_ON_SALE` — chiến dịch vừa bắt đầu (D-45): mỗi khách MỘT thông báo cho chiến dịch đó — chỉ một sản phẩm yêu thích
 *       được giảm thì báo chi tiết giá của sản phẩm đó, nhiều sản phẩm thì gộp "N sản phẩm bạn yêu thích đang giảm giá". Chỉ tính
 *       sản phẩm đang bán và thật sự rẻ hơn giá gốc lúc xử lý.</li>
 *   <li>`FAVORITE_BACK_IN_STOCK` — một lần đổi kho làm khả dụng từ ≤ 0 lên &gt; 0 (D-44) VÀ lúc xử lý mặt hàng vẫn đang bán, vẫn
 *       còn hàng (đọc lại — sự kiện có thể tới muộn). Gộp theo mặt hàng tới khi khách đọc.</li>
 * </ul>
 */
@Component
public class FavoriteNotificationListener {

    private final NotificationService notifications;
    private final NotificationDispatcher dispatcher;
    private final FavoriteService favorites;
    private final ProductService products;
    private final InventoryService inventory;

    public FavoriteNotificationListener(NotificationService notifications, NotificationDispatcher dispatcher,
                                        FavoriteService favorites, ProductService products, InventoryService inventory) {
        this.notifications = notifications;
        this.dispatcher = dispatcher;
        this.favorites = favorites;
        this.products = products;
        this.inventory = inventory;
    }

    // ------------------------------------------------------------------ FAVORITE_ON_SALE

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCampaignStarted(CampaignStartedEvent e) {
        if (e.productIds().isEmpty()) {
            return;
        }
        dispatcher.submit("FAVORITE_ON_SALE chiến dịch id=" + e.campaignId(), () -> notifyOnSale(e));
    }

    private void notifyOnSale(CampaignStartedEvent e) {
        Map<Long, List<Long>> fansByProduct = favorites.customersFavoriting(FavoriteType.PRODUCT, e.productIds());
        if (fansByProduct.isEmpty()) {
            return;
        }
        Map<Long, ItemSummary> onSale = new LinkedHashMap<>();
        products.activeSummaries(fansByProduct.keySet()).forEach((id, p) -> {
            if (p.price().compareTo(p.originalPrice()) < 0) {
                onSale.put(id, p);
            }
        });
        // khách → các sản phẩm yêu thích đang giảm (theo id tăng dần để kết quả ổn định)
        Map<Long, List<ItemSummary>> byUser = new TreeMap<>();
        new TreeMap<>(fansByProduct).forEach((productId, userIds) -> {
            ItemSummary p = onSale.get(productId);
            if (p != null) {
                userIds.forEach(u -> byUser.computeIfAbsent(u, k -> new ArrayList<>()).add(p));
            }
        });
        // Gom người nhận cùng một nội dung → mỗi nhóm một lần ghi theo lô
        Map<NotificationMessage, List<Long>> recipients = new LinkedHashMap<>();
        byUser.forEach((userId, items) -> {
            NotificationMessage message = items.size() == 1
                    ? NotificationMessages.favoriteOnSale(items.get(0).id(), items.get(0).slug(), items.get(0).name(),
                            items.get(0).originalPrice(), items.get(0).price())
                    : NotificationMessages.favoritesOnSale(e.campaignId(), e.slug(), e.name(), items.size());
            recipients.computeIfAbsent(message, k -> new ArrayList<>()).add(userId);
        });
        recipients.forEach((message, userIds) -> notifications.notifyUsers(userIds, message));
    }

    // ------------------------------------------------------------------ FAVORITE_BACK_IN_STOCK

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockLevelsChanged(StockLevelsChangedEvent event) {
        List<Level> restocked = event.levels().stream()
                .filter(l -> l.availableBefore() <= 0 && l.availableAfter() > 0).toList();
        if (restocked.isEmpty()) {
            return;
        }
        dispatcher.submit("FAVORITE_BACK_IN_STOCK " + restocked.size() + " mặt hàng", () -> notifyBackInStock(restocked));
    }

    private void notifyBackInStock(List<Level> restocked) {
        List<Long> productIds = idsOf(restocked, ItemType.PRODUCT);
        List<Long> comboIds = idsOf(restocked, ItemType.COMBO);
        Map<Long, List<Long>> productFans = favorites.customersFavoriting(FavoriteType.PRODUCT, productIds);
        Map<Long, List<Long>> comboFans = favorites.customersFavoriting(FavoriteType.COMBO, comboIds);
        if (productFans.isEmpty() && comboFans.isEmpty()) {
            return;
        }
        StockItems current = inventory.load(List.copyOf(productFans.keySet()), List.copyOf(comboFans.keySet()), Set.of());
        for (Level level : restocked) {
            boolean combo = level.itemType() == ItemType.COMBO;
            List<Long> fans = (combo ? comboFans : productFans).get(level.itemId());
            if (fans == null) {
                continue;
            }
            NotificationMessage message = combo ? comboMessage(current, level.itemId())
                    : productMessage(current, level.itemId());
            if (message != null) {
                notifications.notifyUsers(fans, message);
            }
        }
    }

    /** Null = mặt hàng đã tắt / lại hết hàng / không còn tồn tại lúc xử lý → không báo. */
    private static NotificationMessage productMessage(StockItems current, long id) {
        var p = current.products().get(id);
        return p == null || !p.isActive() || p.getAvailableQuantity() <= 0 ? null
                : NotificationMessages.backInStock(NotificationRefType.PRODUCT, id, p.getSlug(), p.getName());
    }

    private static NotificationMessage comboMessage(StockItems current, long id) {
        var c = current.combos().get(id);
        return c == null || !c.isActive() || c.getAvailableQuantity() <= 0 ? null
                : NotificationMessages.backInStock(NotificationRefType.COMBO, id, c.getSlug(), c.getName());
    }

    private static List<Long> idsOf(List<Level> levels, ItemType type) {
        return levels.stream().filter(l -> l.itemType() == type).map(Level::itemId).distinct().toList();
    }
}
