package vn.campvivo.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import vn.campvivo.catalog.Product;
import vn.campvivo.combo.ProductCombo;

import java.time.OffsetDateTime;

/** Dòng giỏ — database.md mục 3.8: sản phẩm HOẶC combo, không lưu giá (D-04). */
@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private ProductCombo combo;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CartItem() {
        // JPA
    }

    CartItem(Cart cart, Product product, ProductCombo combo, int quantity, OffsetDateTime now) {
        this.cart = cart;
        this.product = product;
        this.combo = combo;
        this.quantity = quantity;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void changeQuantity(int quantity, OffsetDateTime now) {
        this.quantity = quantity;
        this.updatedAt = now;
    }

    public boolean isProduct() {
        return product != null;
    }

    public Long getId() { return id; }
    public Cart getCart() { return cart; }
    public Product getProduct() { return product; }
    public ProductCombo getCombo() { return combo; }
    public int getQuantity() { return quantity; }
}
