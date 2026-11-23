package vn.campvivo.order.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * `OrderPreview` (api.md 4.5). `finalAmount` là giá trị gửi lại làm `expectedTotal`. `shippingCalculated` = false khi
 * chưa có địa chỉ GHN (phí ship tạm = 0); `freeShipping` theo ngưỡng app.shipping.free-threshold (D-27).
 */
public record OrderPreviewResponse(
        List<Item> items,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal finalAmount,
        String voucherCode,
        boolean shippingCalculated,
        boolean freeShipping,
        BigDecimal freeShippingThreshold
) {
    /** `variantId` / `variantTitle`: biến thể khách chọn (D-40), null với combo; title null với biến thể mặc định. */
    public record Item(Long cartItemId, String type, Long id, Long variantId, String variantTitle, String name,
                       int quantity, BigDecimal originalPrice, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
