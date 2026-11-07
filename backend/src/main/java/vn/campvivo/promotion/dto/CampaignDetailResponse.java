package vn.campvivo.promotion.dto;

import vn.campvivo.catalog.dto.ItemSummary;
import vn.campvivo.promotion.DiscountCampaign;
import vn.campvivo.promotion.DiscountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /campaigns/{slug} (api.md mục 4.3) — trang /promotions/[slug]. `products`: sản phẩm đang bán, giá sau chiến dịch. */
public record CampaignDetailResponse(
        Long id,
        String name,
        String slug,
        String description,
        String bannerUrl,
        DiscountType discountType,
        BigDecimal discountValue,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        List<ItemSummary> products
) {
    public static CampaignDetailResponse from(DiscountCampaign c, List<ItemSummary> products) {
        return new CampaignDetailResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getBannerUrl(),
                c.getDiscountType(), c.getDiscountValue(), c.getStartDate(), c.getEndDate(), products);
    }
}
