package vn.campvivo.cart;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Stub tối giản để OrderService (module order) có thể biên dịch và minh hoạ luồng tạo đơn.
 * Người 1 hoàn thiện module này (thêm/sửa/xoá item, tính tổng...) theo FR-08 trong
 * docs/03-san-pham/features.md — xem use case UC-3.1 trong SRS gốc.
 */
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true)
    private Long userId;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public List<CartItem> getItems() { return items; }
}
