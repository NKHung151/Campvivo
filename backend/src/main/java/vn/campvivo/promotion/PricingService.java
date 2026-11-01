package vn.campvivo.promotion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.Product;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.promotion.dto.ActiveCampaignRef;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Hàm tính giá DUY NHẤT cho hiển thị, giỏ hàng và tạo đơn (BR-4, BR-6). Giá sản phẩm = `Product.salePrice` (biểu thức SQL
 * mức giảm tốt nhất trên các chiến dịch đang hiệu lực, D-30) — danh sách/lọc/sắp xếp/giỏ/đặt hàng dùng chung một công thức.
 */
@Service
public class PricingService {

    private final DiscountCampaignRepository campaignRepository;

    public PricingService(DiscountCampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    /** Giá bán một sản phẩm tại thời điểm hiện tại (sau chiến dịch — BR-6). */
    public BigDecimal unitPrice(Product product) {
        return product.getSalePrice();
    }

    /** Combo không áp chiến dịch (BR-4): giá bán = combo_price. */
    public BigDecimal unitPrice(ProductCombo combo) {
        return combo.getComboPrice();
    }

    /** Chiến dịch đang áp (mức giảm tốt nhất) cho từng sản phẩm; sản phẩm không có chiến dịch không có trong map. */
    @Transactional(readOnly = true)
    public Map<Long, ActiveCampaignRef> activeCampaigns(Collection<Long> productIds) {
        Map<Long, ActiveCampaignRef> out = new HashMap<>();
        if (productIds.isEmpty()) {
            return out;
        }
        for (Object[] r : campaignRepository.findBestActiveForProducts(productIds)) {
            out.put(((Number) r[0]).longValue(), new ActiveCampaignRef((String) r[1], (String) r[2], toOffset(r[3])));
        }
        return out;
    }

    /** Cột timestamptz qua native query: driver trả Instant/OffsetDateTime/Timestamp tuỳ phiên bản. */
    private static OffsetDateTime toOffset(Object value) {
        if (value instanceof OffsetDateTime o) {
            return o;
        }
        if (value instanceof Instant i) {
            return i.atOffset(ZoneOffset.UTC);
        }
        return ((Timestamp) value).toInstant().atOffset(ZoneOffset.UTC);
    }
}
