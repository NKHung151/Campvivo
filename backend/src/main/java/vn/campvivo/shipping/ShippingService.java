package vn.campvivo.shipping;

import org.springframework.stereotype.Service;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.shipping.dto.ShippingFeeResponse;

/**
 * Quy tắc phí vận chuyển (D-27), dùng chung cho trang thanh toán và tạo đơn:
 * - cân nặng kiện = tổng số món × app.ghn.default-weight (sản phẩm chưa có cân nặng riêng);
 * - phí = phí GHN; miễn phí khi giá trị đơn (tạm tính − giảm giá) ≥ app.shipping.free-threshold — shop vẫn trả GHN.
 */
@Service
public class ShippingService {

    private final GhnService ghnService;
    private final AppProperties.Ghn ghn;
    private final AppProperties.Shipping shipping;

    public ShippingService(GhnService ghnService, AppProperties appProperties) {
        this.ghnService = ghnService;
        this.ghn = appProperties.ghn();
        this.shipping = appProperties.shipping();
    }

    public ShippingFeeResponse quote(int toDistrictId, String toWardCode, int quantity, long orderValue) {
        // Vẫn hỏi GHN khi được miễn phí: vừa biết phí shop phải trả, vừa kiểm tra địa chỉ giao được
        long carrierFee = ghnService.calculateFee(new GhnFeeRequest(toDistrictId, toWardCode, weightOf(quantity), 0));
        return new ShippingFeeResponse(customerFee(carrierFee, orderValue), carrierFee, isFree(orderValue),
                shipping.freeThreshold());
    }

    /** Phí khách trả cho phí GHN `carrierFee` khi giá trị đơn là `orderValue` (0 nếu đạt ngưỡng miễn phí). */
    public long customerFee(long carrierFee, long orderValue) {
        return isFree(orderValue) ? 0 : carrierFee;
    }

    public boolean isFree(long orderValue) {
        return orderValue >= shipping.freeThreshold();
    }

    public long freeThreshold() {
        return shipping.freeThreshold();
    }

    /** Cân nặng (gram) của kiện hàng gồm `quantity` món. */
    public int weightOf(int quantity) {
        return Math.multiplyExact(Math.max(quantity, 1), ghn.defaultWeight());
    }
}
