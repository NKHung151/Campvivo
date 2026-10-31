package vn.campvivo.catalog;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sản phẩm — cột theo docs/02-ky-thuat/database.md mục 3.3. Entity KHÔNG trả thẳng ra API (qua DTO).
 * Không có biến thể (D-24): mỗi màu/size cần bán riêng là một sản phẩm riêng. Tồn kho/giữ chỗ (BR-1, BR-2)
 * chỉ đổi qua method nghiệp vụ, gọi trong transaction đã khoá dòng. Thời gian do service gán từ bean Clock.
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

    @Column(name = "short_description")
    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String brand;

    /** Thông số theo thứ tự hiển thị (JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<ProductSpec> specs = new ArrayList<>();

    /** NUMERIC(19,0) — VND, không có phần lẻ. */
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal price;

    // BR-1 (docs/02-ky-thuat/database.md): availableQuantity = stockQuantity - reservedQuantity
    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Ảnh theo thứ tự hiển thị. Thay danh sách qua AdminProductService (xoá trước, thêm sau). */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    private List<ProductImage> images = new ArrayList<>();

    protected Product() {
        // JPA
    }

    public Product(Category category, String name, String slug, BigDecimal price, OffsetDateTime now) {
        this.category = category;
        this.name = name;
        this.slug = slug;
        this.price = price;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Cập nhật thông tin hiển thị từ form admin (không đụng tồn kho, ảnh). */
    public void updateDetails(Category category, String name, String slug, String shortDescription,
                              String description, String brand, BigDecimal price,
                              List<ProductSpec> specs, boolean active, OffsetDateTime now) {
        this.category = category;
        this.name = name;
        this.slug = slug;
        this.shortDescription = shortDescription;
        this.description = description;
        this.brand = brand;
        this.price = price;
        this.specs = new ArrayList<>(specs);
        this.active = active;
        this.updatedAt = now;
    }

    /** BR-1: số lượng thực sự có thể bán/thêm vào giỏ. */
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

    /** Đóng gói combo (D-22): lấy hàng lẻ ra khỏi kho. Gọi khi đã khoá dòng; phải đủ hàng khả dụng. */
    public void takeForCombo(int quantity, OffsetDateTime now) {
        if (quantity <= 0 || getAvailableQuantity() < quantity) {
            throw new IllegalStateException("Không đủ tồn kho khả dụng cho sản phẩm " + name);
        }
        this.stockQuantity -= quantity;
        this.updatedAt = now;
    }

    /** Tháo combo (D-22): trả hàng lẻ về kho. Gọi khi đã khoá dòng. */
    public void returnFromCombo(int quantity, OffsetDateTime now) {
        this.stockQuantity += quantity;
        this.updatedAt = now;
    }

    /**
     * Nhập/xuất/điều chỉnh tay (FR-08, BR-8): stock += change. Gọi khi đã khoá dòng; không được làm tồn kho thấp hơn
     * số đang giữ chỗ cho đơn hàng. Trả về tồn kho sau thay đổi (ghi vào inventory_logs).
     */
    public int adjustStock(int change, OffsetDateTime now) {
        if (change == 0 || stockQuantity + change < reservedQuantity) {
            throw new IllegalStateException("Điều chỉnh tồn kho không hợp lệ cho sản phẩm " + name);
        }
        this.stockQuantity += change;
        this.updatedAt = now;
        return stockQuantity;
    }

    public void changeActive(boolean active, OffsetDateTime now) {
        this.active = active;
        this.updatedAt = now;
    }

    public void addImage(String url, boolean primary, int displayOrder, OffsetDateTime now) {
        images.add(new ProductImage(this, url, primary, displayOrder, now));
    }

    public Long getId() { return id; }
    public Category getCategory() { return category; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getShortDescription() { return shortDescription; }
    public String getDescription() { return description; }
    public String getBrand() { return brand; }
    public List<ProductSpec> getSpecs() { return specs; }
    public BigDecimal getPrice() { return price; }
    public int getStockQuantity() { return stockQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<ProductImage> getImages() { return images; }
}
