package vn.campvivo.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.campvivo.inventory.StockLine;

import java.math.BigDecimal;

/**
 * Dòng đơn — database.md mục 3.10: snapshot tên/ảnh/giá lúc đặt, không bao giờ sửa (BR-4).
 * Chỉ giữ id mặt hàng (không nạp entity) vì sản phẩm có thể đã đổi tên/giá/ẩn. Dòng sản phẩm có biến thể khách chọn
 * (D-40): `variantId` + `variantTitle` ("Tím / M", null với biến thể mặc định); đơn cũ trước V15 không có biến thể.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "variant_title")
    private String variantTitle;

    @Column(name = "combo_id")
    private Long comboId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "item_image_url")
    private String itemImageUrl;

    @Column(name = "original_price", nullable = false, precision = 19, scale = 0)
    private BigDecimal originalPrice;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 0)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 0)
    private BigDecimal lineTotal;

    protected OrderItem() {
        // JPA
    }

    OrderItem(Order order, Long productId, Long variantId, String variantTitle, Long comboId, String itemName,
              String itemImageUrl, BigDecimal originalPrice, BigDecimal unitPrice, int quantity) {
        this.order = order;
        this.productId = productId;
        this.variantId = variantId;
        this.variantTitle = variantTitle;
        this.comboId = comboId;
        this.itemName = itemName;
        this.itemImageUrl = itemImageUrl;
        this.originalPrice = originalPrice;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public StockLine toStockLine() {
        return productId != null ? StockLine.product(productId, variantId, quantity) : StockLine.combo(comboId, quantity);
    }

    public boolean isProduct() {
        return productId != null;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getVariantId() { return variantId; }
    public String getVariantTitle() { return variantTitle; }
    public Long getComboId() { return comboId; }
    public String getItemName() { return itemName; }
    public String getItemImageUrl() { return itemImageUrl; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
