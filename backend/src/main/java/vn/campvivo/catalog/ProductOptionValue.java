package vn.campvivo.catalog;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

/** Một giá trị của tuỳ chọn ("Tím", "S/M"); không trùng (không phân biệt hoa thường) trong cùng tuỳ chọn. */
@Entity
@BatchSize(size = 100) // tên biến thể ở danh sách sản phẩm admin: nạp theo lô, không N+1

@Table(name = "product_option_values")
public class ProductOptionValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private ProductOption option;

    @Column(nullable = false, length = 100)
    private String value;

    @Column(nullable = false)
    private short position;

    protected ProductOptionValue() {
        // JPA
    }

    ProductOptionValue(ProductOption option, String value, int position) {
        this.option = option;
        this.value = value;
        this.position = (short) position;
    }

    void update(String value, int position) {
        this.value = value;
        this.position = (short) position;
    }

    public Long getId() { return id; }
    public ProductOption getOption() { return option; }
    public String getValue() { return value; }
    public int getPosition() { return position; }
}
