package vn.campvivo.inventory;

import jakarta.persistence.*;
import vn.campvivo.catalog.Product;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.user.User;

import java.time.OffsetDateTime;

/**
 * Một dòng nhật ký tồn kho (database.md mục 3.22, BR-8): thuộc đúng một sản phẩm hoặc một combo. Chỉ thêm, không sửa/xoá.
 * Tạo qua {@link InventoryLogService#record}.
 */
@Entity
@Table(name = "inventory_logs")
public class InventoryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private ProductCombo combo;

    /** Người thực hiện. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Dương = nhập, âm = xuất. */
    @Column(name = "change_quantity", nullable = false)
    private int changeQuantity;

    /** stock_quantity sau thay đổi. */
    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryReason reason;

    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected InventoryLog() {
        // JPA
    }

    InventoryLog(Product product, ProductCombo combo, User user, int changeQuantity, int stockAfter,
                 InventoryReason reason, String note, OffsetDateTime createdAt) {
        this.product = product;
        this.combo = combo;
        this.user = user;
        this.changeQuantity = changeQuantity;
        this.stockAfter = stockAfter;
        this.reason = reason;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public ProductCombo getCombo() { return combo; }
    public User getUser() { return user; }
    public int getChangeQuantity() { return changeQuantity; }
    public int getStockAfter() { return stockAfter; }
    public InventoryReason getReason() { return reason; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
