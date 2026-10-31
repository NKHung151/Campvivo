package vn.campvivo.combo;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import vn.campvivo.catalog.Category;
import vn.campvivo.catalog.Product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Combo — bộ sản phẩm đóng gói sẵn, giá riêng, tồn kho riêng theo bộ (D-03; database.md mục 3.5).
 * Tồn kho combo tăng khi đóng gói (trừ kho sản phẩm của từng dòng `items`) và giảm khi tháo (trả kho) — D-22.
 * Khách mua combo chỉ giữ chỗ/trừ kho combo.
 */
@Entity
@Table(name = "product_combos")
public class ProductCombo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "combo_price", nullable = false, precision = 19, scale = 0)
    private BigDecimal comboPrice;

    /** Tồn kho theo bộ (D-03) — đổi bằng đóng gói/tháo combo (D-22), không sửa ở form combo. */
    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 100)
    private List<ComboItem> items = new ArrayList<>();

    /** Ảnh tự tải lên (có thể rỗng); ảnh hiển thị còn gồm ảnh chính của các sản phẩm thành phần (D-26). */
    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<ComboImage> images = new ArrayList<>();

    protected ProductCombo() {
    }

    public ProductCombo(Category category, String name, String slug, BigDecimal comboPrice, OffsetDateTime now) {
        this.category = category;
        this.name = name;
        this.slug = slug;
        this.comboPrice = comboPrice;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(Category category, String name, String slug, String description, BigDecimal comboPrice,
                              boolean active, OffsetDateTime now) {
        this.category = category;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.comboPrice = comboPrice;
        this.active = active;
        this.updatedAt = now;
    }

    public void changeActive(boolean active, OffsetDateTime now) {
        this.active = active;
        this.updatedAt = now;
    }

    public void addImage(String url, boolean primary, int displayOrder, OffsetDateTime now) {
        images.add(new ComboImage(this, url, primary, displayOrder, now));
    }

    public void addItem(Product product, int quantity) {
        items.add(new ComboItem(this, product, quantity));
    }

    /** Đóng gói thêm `sets` bộ (kho sản phẩm thành phần đã được trừ ở service). */
    public void addPackedSets(int sets, OffsetDateTime now) {
        this.stockQuantity += sets;
        this.updatedAt = now;
    }

    /** Tháo `sets` bộ — chỉ tháo được số bộ chưa bị giữ chỗ. */
    public void removePackedSets(int sets, OffsetDateTime now) {
        if (sets <= 0 || getAvailableQuantity() < sets) {
            throw new IllegalStateException("Không đủ bộ combo khả dụng để tháo");
        }
        this.stockQuantity -= sets;
        this.updatedAt = now;
    }

    /**
     * Xuất tay bộ combo hỏng/mất (FR-08). Chỉ được giảm — muốn tăng thì đóng gói để trừ hàng lẻ (D-27); không được làm
     * tồn kho thấp hơn số đang giữ chỗ. Gọi khi đã khoá dòng. Trả về tồn kho sau thay đổi.
     */
    public int writeOff(int sets, OffsetDateTime now) {
        if (sets <= 0 || getAvailableQuantity() < sets) {
            throw new IllegalStateException("Không đủ bộ combo khả dụng để xuất");
        }
        this.stockQuantity -= sets;
        this.updatedAt = now;
        return stockQuantity;
    }

    /** Khả dụng = tồn − giữ chỗ (BR-1). */
    public int getAvailableQuantity() {
        return stockQuantity - reservedQuantity;
    }

    /** Tổng giá lẻ của các sản phẩm thành phần (giá hiện tại × số lượng) — để so với giá combo. */
    public BigDecimal getItemsTotal() {
        return items.stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() { return id; }
    public Category getCategory() { return category; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public BigDecimal getComboPrice() { return comboPrice; }
    public int getStockQuantity() { return stockQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<ComboItem> getItems() { return items; }
    public List<ComboImage> getImages() { return images; }
}
