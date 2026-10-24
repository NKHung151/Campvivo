package vn.campvivo.combo;

import jakarta.persistence.*;
import vn.campvivo.catalog.Product;

/**
 * Một dòng thành phần combo (database.md mục 3.6): sản phẩm + số lượng trong 1 bộ.
 * Đóng gói combo trừ kho sản phẩm này (D-22).
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

    @Column(nullable = false)
    private int quantity;

    protected ComboItem() {
    }

    ComboItem(ProductCombo combo, Product product, int quantity) {
        this.combo = combo;
        this.product = product;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public ProductCombo getCombo() { return combo; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
}
