package vn.campvivo.promotion;

import org.springframework.stereotype.Service;
import vn.campvivo.catalog.Product;
import vn.campvivo.combo.ProductCombo;

import java.math.BigDecimal;

/**
 * Hàm tính giá DUY NHẤT cho giỏ hàng và tạo đơn (BR-4, BR-6). Hiện chưa có chiến dịch giảm giá (FR-17, module
 * promotion) nên giá bán = giá gốc; khi làm chiến dịch chỉ cần sửa {@link #unitPrice(Product)} — giỏ, xem trước và
 * tạo đơn tự áp dụng theo.
 */
@Service
public class PricingService {

    /** Giá bán một sản phẩm tại thời điểm hiện tại (sau chiến dịch — BR-6). */
    public BigDecimal unitPrice(Product product) {
        return product.getPrice();
    }

    /** Combo không áp chiến dịch (BR-4): giá bán = combo_price. */
    public BigDecimal unitPrice(ProductCombo combo) {
        return combo.getComboPrice();
    }
}
