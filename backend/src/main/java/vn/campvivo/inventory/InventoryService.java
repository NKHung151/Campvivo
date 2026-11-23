package vn.campvivo.inventory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductVariant;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Giữ chỗ / trừ / trả tồn kho theo vòng đời đơn hàng (BR-1, BR-2) cho sản phẩm, biến thể (D-40) và combo.
 * Module inventory là nơi DUY NHẤT đổi `stock_quantity`/`reserved_quantity` theo đơn hàng. Dòng có biến thể đổi cả
 * tổng sản phẩm và biến thể; dòng đơn cũ chưa lưu biến thể chỉ đổi tổng.
 * Mọi hàm chạy trong transaction của bên gọi (MANDATORY) và khoá dòng `SELECT … FOR UPDATE` theo thứ tự toàn hệ thống
 * (database.md mục 6): products id tăng dần → product_combos id tăng dần → product_variants id tăng dần. Bên gọi phải
 * khoá `orders` TRƯỚC khi gọi.
 */
@Service
public class InventoryService {

    private final EntityManager em;
    private final InventoryLogService logService;
    private final Clock clock;

    public InventoryService(EntityManager em, InventoryLogService logService, Clock clock) {
        this.em = em;
        this.logService = logService;
        this.clock = clock;
    }

    /** Sản phẩm/combo/biến thể theo id (id không tồn tại thì không có trong map). */
    public record StockItems(Map<Long, Product> products, Map<Long, ProductCombo> combos,
                             Map<Long, ProductVariant> variants) {}

    /**
     * Khoá `SELECT … FOR UPDATE` theo thứ tự toàn hệ thống: sản phẩm id tăng dần, combo id tăng dần rồi biến thể id
     * tăng dần.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockItems lock(Collection<Long> productIds, Collection<Long> comboIds, Collection<Long> variantIds) {
        StockItems items = fetch(productIds, comboIds, variantIds, LockModeType.PESSIMISTIC_WRITE);
        // Entity đã nằm sẵn trong persistence context (đọc trước khi khoá) KHÔNG được query khoá cập nhật lại —
        // đọc lại sau khi đã giữ khoá để kiểm tồn kho trên số liệu mới nhất (chống bán vượt).
        items.products().values().forEach(em::refresh);
        items.combos().values().forEach(em::refresh);
        items.variants().values().forEach(em::refresh);
        return items;
    }

    /** Đọc không khoá (xem trước đơn hàng) — số liệu có thể đổi trước khi đặt; tạo đơn luôn kiểm lại bằng {@link #lock}. */
    @Transactional(readOnly = true)
    public StockItems load(Collection<Long> productIds, Collection<Long> comboIds, Collection<Long> variantIds) {
        return fetch(productIds, comboIds, variantIds, LockModeType.NONE);
    }

    private StockItems fetch(Collection<Long> productIds, Collection<Long> comboIds, Collection<Long> variantIds,
                             LockModeType lockMode) {
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
        Map<Long, ProductVariant> variants = variantIds.isEmpty() ? Map.of()
                : em.createQuery("select v from ProductVariant v where v.id in :ids order by v.id", ProductVariant.class)
                        .setParameter("ids", variantIds)
                        .setLockMode(lockMode)
                        .getResultList().stream()
                        .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        return new StockItems(products, combos, variants);
    }

    /**
     * Tạo đơn: reserved += qty. Thiếu hàng ở BẤT KỲ dòng nào → OUT_OF_STOCK (details.items liệt kê mọi dòng thiếu),
     * không giữ chỗ dòng nào (transaction của bên gọi rollback).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(List<StockLine> lines) {
        Map<Long, Integer> productQty = sum(lines, StockLine::productId);
        Map<Long, Integer> comboQty = sum(lines, StockLine::comboId);
        Map<Long, Integer> variantQty = sum(lines, StockLine::variantId);
        StockItems locked = lock(productQty.keySet(), comboQty.keySet(), variantQty.keySet());

        List<Map<String, Object>> shortages = new ArrayList<>();
        // Thiếu ở biến thể báo theo biến thể; tổng sản phẩm chỉ báo khi chưa có biến thể nào của nó bị báo thiếu
        Set<Long> shortProducts = new HashSet<>();
        variantQty.forEach((id, qty) -> {
            ProductVariant v = locked.variants().get(id);
            int available = v == null ? 0 : v.getAvailableQuantity();
            if (available < qty) {
                Long productId = productOf(lines, id);
                shortages.add(shortage("PRODUCT", productId, id, v == null ? null : v.displayName(), available));
                shortProducts.add(productId);
            }
        });
        productQty.forEach((id, qty) -> {
            Product p = locked.products().get(id);
            int available = p == null ? 0 : p.getAvailableQuantity();
            if (available < qty && !shortProducts.contains(id)) {
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
        // Tổng sản phẩm trước rồi mới tới biến thể (biến thể duy nhất dùng giữ chỗ của tổng)
        productQty.forEach((id, qty) -> locked.products().get(id).reserve(qty));
        comboQty.forEach((id, qty) -> locked.combos().get(id).reserve(qty));
        variantQty.forEach((id, qty) -> locked.variants().get(id).reserve(qty));
    }

    /** PENDING → CONFIRMED: stock −= qty, reserved −= qty. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(List<StockLine> lines) {
        apply(lines, Product::commit, ProductCombo::commit, ProductVariant::commit);
    }

    /** PENDING → CANCELLED / PAYMENT_FAILED: reserved −= qty. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(List<StockLine> lines) {
        apply(lines, Product::release, ProductCombo::release, ProductVariant::release);
    }

    /**
     * Trả hàng được duyệt (BR-9, D-41): cộng lại tồn kho từng dòng của đơn (đơn đã CONFIRMED nên tồn đã bị trừ) và ghi
     * nhật ký RETURN (BR-8) do nhân viên `staffId`. Dòng có biến thể cộng vào biến thể + tổng sản phẩm; dòng đơn cũ chưa
     * lưu biến thể cộng vào biến thể duy nhất, hoặc chỉ vào tổng khi sản phẩm nhiều biến thể (không biết màu / size —
     * thành "hàng chưa phân bổ", nhân viên chỉnh ở trang Tồn kho; không ghi nhật ký vì nhật ký sản phẩm theo biến thể).
     * Combo: cộng lại số bộ.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restockReturn(List<StockLine> lines, long staffId, String note) {
        Map<Long, Integer> productQty = sum(lines, StockLine::productId);
        Map<Long, Integer> comboQty = sum(lines, StockLine::comboId);
        StockItems locked = lock(productQty.keySet(), comboQty.keySet(), Set.of());

        Map<Long, Integer> variantQty = sum(lines, StockLine::variantId);
        Map<Long, Integer> productOnly = new TreeMap<>();
        for (StockLine l : lines) {
            Product p = l.isProduct() && l.variantId() == null ? locked.products().get(l.productId()) : null;
            if (p == null) {
                continue;
            }
            if (p.getVariants().size() == 1) {
                variantQty.merge(p.getVariants().get(0).getId(), l.quantity(), Integer::sum);
            } else {
                productOnly.merge(p.getId(), l.quantity(), Integer::sum);
            }
        }
        // Biến thể khoá sau sản phẩm và combo (database.md mục 6)
        Map<Long, ProductVariant> variants = lock(Set.of(), Set.of(), variantQty.keySet()).variants();
        OffsetDateTime now = OffsetDateTime.now(clock);
        variantQty.forEach((id, qty) -> {
            ProductVariant v = variants.get(id);
            if (v != null) {
                v.adjustStock(qty, now);
                logService.record(v, staffId, qty, InventoryReason.RETURN, note);
            }
        });
        productOnly.forEach((id, qty) -> locked.products().get(id).adjustStock(qty, now));
        comboQty.forEach((id, qty) -> {
            ProductCombo c = locked.combos().get(id);
            if (c != null) {
                c.addPackedSets(qty, now);
                logService.record(c, staffId, qty, InventoryReason.RETURN, note);
            }
        });
    }

    private interface StockChange<T> {
        void apply(T item, int quantity);
    }

    /** Sản phẩm đổi trước biến thể: biến thể duy nhất lấy lại tồn từ sản phẩm sau khi tổng đã trừ. */
    private void apply(List<StockLine> lines, StockChange<Product> onProduct, StockChange<ProductCombo> onCombo,
                       StockChange<ProductVariant> onVariant) {
        Map<Long, Integer> productQty = sum(lines, StockLine::productId);
        Map<Long, Integer> comboQty = sum(lines, StockLine::comboId);
        Map<Long, Integer> variantQty = sum(lines, StockLine::variantId);
        StockItems locked = lock(productQty.keySet(), comboQty.keySet(), variantQty.keySet());
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
        variantQty.forEach((id, qty) -> {
            ProductVariant v = locked.variants().get(id);
            if (v != null) {
                onVariant.apply(v, qty);
            }
        });
    }

    /** Gộp số lượng theo id (bỏ dòng không có id đó), TreeMap để duyệt theo id tăng dần. */
    private static Map<Long, Integer> sum(List<StockLine> lines, Function<StockLine, Long> idOf) {
        Map<Long, Integer> out = new TreeMap<>();
        for (StockLine l : lines) {
            Long id = idOf.apply(l);
            if (id != null) {
                out.merge(id, l.quantity(), Integer::sum);
            }
        }
        return out;
    }

    private static Long productOf(List<StockLine> lines, Long variantId) {
        return lines.stream().filter(l -> Objects.equals(l.variantId(), variantId)).map(StockLine::productId)
                .findFirst().orElse(null);
    }

    /** Một dòng thiếu hàng trong `details.items` của OUT_OF_STOCK. */
    public static Map<String, Object> shortage(String type, Long id, String name, int available) {
        return shortage(type, id, null, name, available);
    }

    /** Như trên, kèm `variantId` khi thiếu ở một biến thể (D-40). */
    public static Map<String, Object> shortage(String type, Long id, Long variantId, String name, int available) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("id", id);
        if (variantId != null) {
            m.put("variantId", variantId);
        }
        m.put("name", name);
        m.put("availableQuantity", Math.max(available, 0));
        return m;
    }

    /** OUT_OF_STOCK kèm `details.items = [{type, id, variantId?, name, availableQuantity}]` (api.md mục 2). */
    public static BusinessException outOfStock(List<Map<String, Object>> items) {
        String message = items.size() == 1 && items.get(0).get("name") != null
                ? "\"" + items.get(0).get("name") + "\" chỉ còn " + items.get(0).get("availableQuantity") + " sản phẩm khả dụng."
                : ErrorCode.OUT_OF_STOCK.getMessage();
        return new BusinessException(ErrorCode.OUT_OF_STOCK, message, Map.of("items", items));
    }
}
