package vn.campvivo.catalog;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/** Tuỳ chọn của sản phẩm ("Màu sắc", "Size") — tối đa 2, `position` 1 hoặc 2 (D-37). */
@Entity
@Table(name = "product_options")
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private short position;

    @OneToMany(mappedBy = "option", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<ProductOptionValue> values = new ArrayList<>();

    protected ProductOption() {
        // JPA
    }

    ProductOption(Product product, String name, int position) {
        this.product = product;
        this.name = name;
        this.position = (short) position;
    }

    void update(String name, int position) {
        this.name = name;
        this.position = (short) position;
    }

    ProductOptionValue addValue(String value, int position) {
        ProductOptionValue v = new ProductOptionValue(this, value, position);
        values.add(v);
        return v;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public String getName() { return name; }
    public int getPosition() { return position; }
    public List<ProductOptionValue> getValues() { return values; }
}
