package vn.campvivo.cart;

import org.springframework.stereotype.Service;

/**
 * TẠM THỜI (F-11): chưa có bảng `carts` (migration đơn hàng của Người 1 chưa vào dev) nên chưa tạo giỏ thật.
 * TODO(Người 1, N1-01): hiện thực CartService thật (tạo dòng `carts` cho user) rồi XOÁ lớp stub này.
 */
@Service
public class StubCartService implements CartService {

    @Override
    public void createForUser(Long userId) {
        // Chủ ý để trống: xem Javadoc.
    }
}
