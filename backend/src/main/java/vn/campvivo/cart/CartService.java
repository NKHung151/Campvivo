package vn.campvivo.cart;

/**
 * Giao diện giỏ hàng cho các module khác (architecture.md: gọi Service công khai, không gọi Repository của module khác).
 * Người 1 (module cart) hiện thực thật; trong lúc đó {@link StubCartService} không làm gì (F-11).
 */
public interface CartService {

    /** Tạo giỏ hàng rỗng cho người dùng mới đăng ký (FR-01). Phải idempotent: gọi lại không tạo giỏ thứ hai. */
    void createForUser(Long userId);
}
