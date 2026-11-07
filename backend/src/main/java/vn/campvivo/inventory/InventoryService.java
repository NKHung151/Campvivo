package vn.campvivo.inventory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.Product;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Giữ chỗ / trừ / trả tồn kho theo vòng đời đơn hàng (BR-1, BR-2) cho cả sản phẩm và combo.
 * Module inventory là nơi DUY NHẤT đổi `stock_quantity`/`reserved_quantity` theo đơn hàng.
 * Mọi hàm chạy trong transaction của bên gọi (MANDATORY) và khoá dòng `SELECT … FOR UPDATE` theo thứ tự toàn hệ thống
 * (database.md mục 6): products id tăng dần → product_combos id tăng dần. Bên gọi phải khoá `orders` TRƯỚC khi gọi.
 */
@Service
public class InventoryService {

    private final EntityManager em;

    public InventoryService(EntityManager em) {
        this.em = em;
    }

    /** Sản phẩm/combo theo id (id không tồn tại thì không có trong map). */
    public record StockItems(Map<Long, Product> products, Map<Long, ProductCombo> combos) {}

    /** Khoá `SELECT … FOR UPDATE` theo thứ tự toàn hệ thống: sản phẩm id tăng dần rồi combo id tăng dần. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockItems lock(Collection<Long> productIds, Collection<Long> comboIds) {
        StockItems items = fetch(productIds, comboIds, LockModeType.PESSIMISTIC_WRITE);
        // Entity đã nằm sẵn trong persistence context (đọc trước khi khoá) KHÔNG được query khoá cập nhật lại —
        // đọc lại sau khi đã giữ khoá để kiểm tồn kho trên số liệu mới nhất (chống bán vượt).
        items.products().values().forEach(em::refresh);
        items.combos().values().forEach(em::refresh);
        return items;
    }

    /** Đọc không khoá (xem trước đơn hàng) — số liệu có thể đổi trước khi đặt; tạo đơn luôn kiểm lại bằng {@link #lock}. */
    @Transactional(readOnly = true)
    public StockItems load(Collection<Long> productIds, Collection<Long> comboIds) {
        return fetch(productIds, comboIds, LockModeType.NONE);
    }

    private StockItems fetch(Collection<Long> productIds, Collection<Long> comboIds, LockModeType lockMode) {
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : em.createQuery("select p from Product p where p.id in :ids order by p.id", Product.class)
                        .setParameter("ids", productIds)
                        .setLockMode(lockMode)
                        .getResultList().stream()
                        .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductCombo> combos = comboIds.isEmpty() ? Map.of()
                : em.createQuery("select c from ProductCombo c where c.id in :ids order by c.id", ProductCombo.class)
                        .setParameter("ids", comboIds)
                        .setLockMode(lockMode)
                        .getResultList().stream()
                        .collect(Collectors.toMap(ProductCombo::getId, Function.identity()));
        return new StockItems(products, combos);
    }

    /**
     * Tạo đơn: reserved += qty. Thiếu hàng ở BẤT KỲ dòng nào → OUT_OF_STOCK (details.items liệt kê mọi dòng thiếu),
     * không giữ chỗ dòng nào (transaction của bên gọi rollback).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(List<StockLine> lines) {
        Map<Long, Integer> productQty = sum(lines, true);
        Map<Long, Integer> comboQty = sum(lines, false);
        StockItems locked = lock(productQty.keySet(), comboQty.keySet());

        List<Map<String, Object>> shortages = new ArrayList<>();
        productQty.forEach((id, qty) -> {
            Product p = locked.products().get(id);
            int available = p == null ? 0 : p.getAvailableQuantity();
            if (available < qty) {
                shortages.add(shortage("PRODUCT", id, p == null ? null : p.getName(), available));
            }
        });
        comboQty.forEach((id, qty) -> {
            ProductCombo c = locked.combos().get(id);
            int available = c == null ? 0 : c.getAvailableQuantity();
            if (available < qty) {
                shortages.add(shortage("COMBO", id, c == null ? null : c.getName(), available));
            }
        });
        if (!shortages.isEmpty()) {
            throw outOfStock(shortages);
        }
        productQty.forEach((id, qty) -> locked.products().get(id).reserve(qty));
        comboQty.forEach((id, qty) -> locked.combos().get(id).reserve(qty));
    }

    /** PENDING → CONFIRMED: stock −= qty, reserved −= qty. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(List<StockLine> lines) {
        apply(lines, Product::commit, ProductCombo::commit);
    }

    /** PENDING → CANCELLED / PAYMENT_FAILED: reserved −= qty. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(List<StockLine> lines) {
        apply(lines, Product::release, ProductCombo::release);
    }

    private interface StockChange<T> {
        void apply(T item, int quantity);
    }

    private void apply(List<StockLine> lines, StockChange<Product> onProduct, StockChange<ProductCombo> onCombo) {
        Map<Long, Integer> productQty = sum(lines, true);
        Map<Long, Integer> comboQty = sum(lines, false);
        StockItems locked = lock(productQty.keySet(), comboQty.keySet());
        productQty.forEach((id, qty) -> {
            Product p = locked.products().get(id);
            if (p != null) {
                onProduct.apply(p, qty);
            }
        });
        comboQty.forEach((id, qty) -> {
            ProductCombo c = locked.combos().get(id);
            if (c != null) {
                onCombo.apply(c, qty);
            }
        });
    }

    /** Gộp số lượng theo id, TreeMap để duyệt theo id tăng dần. */
    private static Map<Long, Integer> sum(List<StockLine> lines, boolean products) {
        Map<Long, Integer> out = new TreeMap<>();
        for (StockLine l : lines) {
            if (l.isProduct() == products) {
                out.merge(products ? l.productId() : l.comboId(), l.quantity(), Integer::sum);
            }
        }
        return out;
    }

    /** Một dòng thiếu hàng trong `details.items` của OUT_OF_STOCK. */
    public static Map<String, Object> shortage(String type, Long id, String name, int available) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("id", id);
        m.put("name", name);
        m.put("availableQuantity", Math.max(available, 0));
        return m;
    }

    /** OUT_OF_STOCK kèm `details.items = [{type, id, name, availableQuantity}]` (api.md mục 2). */
    public static BusinessException outOfStock(List<Map<String, Object>> items) {
        String message = items.size() == 1 && items.get(0).get("name") != null
                ? "\"" + items.get(0).get("name") + "\" chỉ còn " + items.get(0).get("availableQuantity") + " sản phẩm khả dụng."
                : ErrorCode.OUT_OF_STOCK.getMessage();
        return new BusinessException(ErrorCode.OUT_OF_STOCK, message, Map.of("items", items));
    }
}
