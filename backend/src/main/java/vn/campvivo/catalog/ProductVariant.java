package vn.campvivo.catalog;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Biến thể = một tổ hợp giá trị tuỳ chọn (vd. Tím / S/M), cùng giá với sản phẩm (D-37).
 * Sản phẩm không có tuỳ chọn có đúng 1 biến thể mặc định (option1/option2 null).
 * <p>Tồn kho chi tiết theo biến thể. Nhập/xuất/đóng gói/tháo đổi CẢ biến thể và tổng ở {@link Product} (giỏ hàng / đơn
 * hàng hiện giữ chỗ và trừ kho theo tổng sản phẩm). `reserved_quantity` của biến thể chưa dùng (= 0) cho tới khi giỏ /
 * đơn lưu biến thể. Gọi trong transaction đã khoá dòng sản phẩm và biến thể.
 */
@Entity
@Table(name = "product_variants")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option1_value_id")
    private ProductOptionValue option1Value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option2_value_id")
    private ProductOptionValue option2Value;

    /** Mã hàng nội bộ, không bắt buộc, không trùng. */
    @Column(length = 64)
    private String sku;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ProductVariant() {
        // JPA
    }

    ProductVariant(Product product, ProductOptionValue option1Value, ProductOptionValue option2Value, OffsetDateTime now) {
        this.product = product;
        this.option1Value = option1Value;
        this.option2Value = option2Value;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Đổi tổ hợp giá trị (khi thêm/bớt tuỳ chọn) — tồn kho giữ nguyên. */
    void assign(ProductOptionValue option1Value, ProductOptionValue option2Value, OffsetDateTime now) {
        this.option1Value = option1Value;
        this.option2Value = option2Value;
        this.updatedAt = now;
    }

    void update(String sku, boolean active, int position, OffsetDateTime now) {
        this.sku = sku;
        this.active = active;
        this.position = position;
        this.updatedAt = now;
    }

    /** Biến thể mặc định của sản phẩm không có tuỳ chọn. */
    public boolean isDefault() {
        return option1Value == null;
    }

    /** Giá trị theo thứ tự tuỳ chọn (rỗng với biến thể mặc định). */
    public List<ProductOptionValue> getOptionValues() {
        List<ProductOptionValue> out = new ArrayList<>(2);
        if (option1Value != null) out.add(option1Value);
        if (option2Value != null) out.add(option2Value);
        return out;
    }

    /** "Tím / S/M"; null với biến thể mặc định. */
    public String getTitle() {
        if (isDefault()) {
            return null;
        }
        return String.join(" / ", getOptionValues().stream().map(ProductOptionValue::getValue).toList());
    }

    /**
     * Biến thể duy nhất (sản phẩm không có tuỳ chọn): tồn của nó LUÔN bằng tồn sản phẩm — đơn hàng trừ thẳng tổng nên
     * không cần phân bổ.
     */
    private boolean isOnly() {
        return product.getVariants().size() == 1;
    }

    /** Biến thể duy nhất: lấy lại tồn từ sản phẩm (đơn hàng có thể đã trừ tổng). */
    void syncFromProduct() {
        if (isOnly()) {
            this.stockQuantity = product.getStockQuantity();
        }
    }

    /** Tồn của biến thể (biến thể duy nhất = tồn sản phẩm). */
    public int getStockQuantity() {
        return isOnly() ? product.getStockQuantity() : stockQuantity;
    }

    /**
     * Số lấy ra được ngay: không vượt tồn của biến thể và không vượt phần khả dụng của sản phẩm (hàng đang giữ chỗ cho
     * đơn tính ở tổng sản phẩm).
     */
    public int getAvailableQuantity() {
        return isOnly() ? product.getAvailableQuantity()
                : Math.min(stockQuantity - reservedQuantity, product.getAvailableQuantity());
    }

    /** Đóng gói combo (D-22): lấy hàng ra khỏi biến thể và tổng sản phẩm. */
    public void takeForCombo(int quantity, OffsetDateTime now) {
        if (quantity <= 0 || getAvailableQuantity() < quantity) {
            throw new IllegalStateException("Không đủ tồn kho cho " + displayName());
        }
        product.takeForCombo(quantity, now);
        this.stockQuantity = isOnly() ? product.getStockQuantity() : stockQuantity - quantity;
        this.updatedAt = now;
    }

    /** Tháo combo (D-22): trả hàng về biến thể và tổng sản phẩm. */
    public void returnFromCombo(int quantity, OffsetDateTime now) {
        product.returnFromCombo(quantity, now);
        this.stockQuantity = isOnly() ? product.getStockQuantity() : stockQuantity + quantity;
        this.updatedAt = now;
    }

    /**
     * Nhập/xuất/kiểm kê tay (FR-08, BR-8): stock += change ở biến thể và tổng sản phẩm. Biến thể không được âm; tổng không
     * được thấp hơn số đang giữ chỗ cho đơn. Trả về tồn của biến thể sau thay đổi.
     */
    public int adjustStock(int change, OffsetDateTime now) {
        if (change == 0 || getStockQuantity() + change < 0) {
            throw new IllegalStateException("Điều chỉnh tồn kho không hợp lệ cho " + displayName());
        }
        product.adjustStock(change, now);
        this.stockQuantity = isOnly() ? product.getStockQuantity() : stockQuantity + change;
        this.updatedAt = now;
        return getStockQuantity();
    }

    /**
     * Phân bổ hàng đã bán (D-37): đơn hàng chỉ trừ tổng sản phẩm, nhân viên trừ tiếp ở đúng biến thể khách mua. Chỉ trừ
     * biến thể (tổng đã trừ khi đơn xác nhận); tối đa phần chênh {@link Product#getUnallocatedSold()}.
     */
    public int allocateSold(int quantity, OffsetDateTime now) {
        if (isOnly() || quantity <= 0 || quantity > stockQuantity || quantity > product.getUnallocatedSold()) {
            throw new IllegalStateException("Phân bổ hàng đã bán không hợp lệ cho " + displayName());
        }
        this.stockQuantity -= quantity;
        this.updatedAt = now;
        return stockQuantity;
    }

    /** Còn tồn kho hoặc giữ chỗ → không xoá được, chỉ tắt. */
    public boolean hasStock() {
        return getStockQuantity() > 0 || reservedQuantity > 0;
    }

    /** "Áo mưa Coleman — Tím / S/M" (hoặc chỉ tên sản phẩm với biến thể mặc định). */
    public String displayName() {
        String title = getTitle();
        return title == null ? product.getName() : product.getName() + " — " + title;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public ProductOptionValue getOption1Value() { return option1Value; }
    public ProductOptionValue getOption2Value() { return option2Value; }
    public String getSku() { return sku; }
    public int getReservedQuantity() { return reservedQuantity; }
    public boolean isActive() { return active; }
    public int getPosition() { return position; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
