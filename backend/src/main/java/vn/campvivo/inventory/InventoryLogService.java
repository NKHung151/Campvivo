package vn.campvivo.inventory;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.Product;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.user.User;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Ghi nhật ký tồn kho (BR-8). Gọi SAU khi đã đổi tồn kho, trong cùng transaction với thay đổi đó (MANDATORY):
 * điều chỉnh tay, đóng gói/tháo combo (D-27), trả hàng hoàn tất (module order, reason RETURN).
 */
@Service
public class InventoryLogService {

    private final InventoryLogRepository repository;
    private final EntityManager entityManager;
    private final Clock clock;

    public InventoryLogService(InventoryLogRepository repository, EntityManager entityManager, Clock clock) {
        this.repository = repository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryLog record(Product product, long userId, int change, InventoryReason reason, String note) {
        return save(product, null, userId, change, product.getStockQuantity(), reason, note);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InventoryLog record(ProductCombo combo, long userId, int change, InventoryReason reason, String note) {
        return save(null, combo, userId, change, combo.getStockQuantity(), reason, note);
    }

    private InventoryLog save(Product product, ProductCombo combo, long userId, int change, int stockAfter,
                              InventoryReason reason, String note) {
        User user = entityManager.getReference(User.class, userId);
        String text = note == null || note.isBlank() ? null : note.trim();
        return repository.save(new InventoryLog(product, combo, user, change, stockAfter, reason, text,
                OffsetDateTime.now(clock)));
    }
}
