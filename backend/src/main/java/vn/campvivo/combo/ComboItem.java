package vn.campvivo.combo;

import jakarta.persistence.*;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductVariant;

/**
 * Một dòng thành phần combo (database.md mục 3.6): biến thể của sản phẩm + số lượng trong 1 bộ.
 * Mỗi sản phẩm một dòng; đóng gói combo trừ kho đúng biến thể này (D-22, D-37).
 */
@Entity
@Table(name = "combo_items")
public class ComboItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_id", nullable = false)
    private ProductCombo combo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private int quantity;

    protected ComboItem() {
    }

    ComboItem(ProductCombo combo, ProductVariant variant, int quantity) {
        this.combo = combo;
        this.product = variant.getProduct();
        this.variant = variant;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public ProductCombo getCombo() { return combo; }
    public Product getProduct() { return product; }
    public ProductVariant getVariant() { return variant; }
    public int getQuantity() { return quantity; }
}
