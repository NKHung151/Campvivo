package vn.campvivo.promotion.dto;

import vn.campvivo.promotion.DiscountCampaign;
import vn.campvivo.promotion.DiscountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Một phần tử của GET /campaigns (api.md mục 4.3) — chiến dịch đang hiệu lực. `productCount`: sản phẩm đang bán. */
public record CampaignSummaryResponse(
        Long id,
        String name,
        String slug,
        String bannerUrl,
        DiscountType discountType,
        BigDecimal discountValue,
        OffsetDateTime endDate,
        long productCount
) {
    public static CampaignSummaryResponse from(DiscountCampaign c, long productCount) {
        return new CampaignSummaryResponse(c.getId(), c.getName(), c.getSlug(), c.getBannerUrl(), c.getDiscountType(),
                c.getDiscountValue(), c.getEndDate(), productCount);
    }
}
