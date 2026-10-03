package vn.campvivo.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Entity mẫu cho module catalog — theo đúng cột trong docs/02-ky-thuat/database.md.
 * Entity KHÔNG trả thẳng ra API (xem ProductResponse DTO) — theo .agents/rules/backend.md.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    // BR-1 (docs/02-ky-thuat/database.md): availableQuantity = stockQuantity - reservedQuantity
    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected Product() {
        // JPA
    }

    public Product(Category category, String name, String slug, BigDecimal price, int stockQuantity) {
        this.category = category;
        this.name = name;
        this.slug = slug;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    /** BR-1: số lượng thực sự có thể bán/thêm vào giỏ. Không expose trực tiếp stock/reserved ra API. */
    public int getAvailableQuantity() {
        return stockQuantity - reservedQuantity;
    }

    /**
     * Dùng khi tạo đơn hàng (giữ chỗ tồn kho). PHẢI gọi trong transaction đã lock dòng này
     * bằng SELECT ... FOR UPDATE (xem OrderService.createOrder) — entity không tự lock được.
     */
    public void reserve(int quantity) {
        if (getAvailableQuantity() < quantity) {
            throw new IllegalStateException("Không đủ tồn kho khả dụng cho sản phẩm " + name);
        }
        this.reservedQuantity += quantity;
    }

    /** Dùng khi đơn CANCELLED/PAYMENT_FAILED (BR-2) — chỉ giải phóng reserved, không đụng stock. */
    public void release(int quantity) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    /** Dùng khi đơn chuyển CONFIRMED (BR-2) — trừ thật cả stock và reserved. */
    public void commit(int quantity) {
        this.stockQuantity -= quantity;
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    // Getters (không thêm setter bừa bãi — đổi state qua các method nghiệp vụ ở trên)
    public Long getId() { return id; }
    public Category getCategory() { return category; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public int getStockQuantity() { return stockQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public boolean isActive() { return active; }

    public void setDescription(String description) { this.description = description; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setActive(boolean active) { this.active = active; }
}
