package vn.campvivo.promotion.admin;

import vn.campvivo.catalog.Product;
import vn.campvivo.promotion.CampaignStatus;
import vn.campvivo.promotion.DiscountCampaign;
import vn.campvivo.promotion.DiscountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Kiểu `AdminCampaignSummary`, `AdminCampaignDetail` (api.md mục 4.10). `status` tính tại thời điểm trả về. */
public final class AdminCampaignResponses {

    private AdminCampaignResponses() {
    }

    public record Summary(Long id, String name, String slug, String bannerUrl, DiscountType discountType,
                          BigDecimal discountValue, OffsetDateTime startDate, OffsetDateTime endDate, boolean isActive,
                          CampaignStatus status, long productCount, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        public static Summary from(DiscountCampaign c, long productCount, OffsetDateTime now) {
            return new Summary(c.getId(), c.getName(), c.getSlug(), c.getBannerUrl(), c.getDiscountType(),
                    c.getDiscountValue(), c.getStartDate(), c.getEndDate(), c.isActive(), c.statusAt(now), productCount,
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    /** `campaignPrice`: giá nếu chỉ áp chiến dịch này (giá bán thật có thể thấp hơn nếu chồng chiến dịch khác — BR-6). */
    public record Item(Long id, String name, String slug, String primaryImageUrl, boolean isActive, BigDecimal price,
                       BigDecimal campaignPrice) {}

    public record Detail(Long id, String name, String slug, String bannerUrl, DiscountType discountType,
                         BigDecimal discountValue, OffsetDateTime startDate, OffsetDateTime endDate, boolean isActive,
                         CampaignStatus status, long productCount, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                         String description, List<Item> products) {
        public static Detail from(DiscountCampaign c, Map<Long, String> images, OffsetDateTime now) {
            List<Item> items = c.getProducts().stream()
                    .map((Product p) -> new Item(p.getId(), p.getName(), p.getSlug(), images.get(p.getId()), p.isActive(),
                            p.getPrice(), c.priceFor(p.getPrice())))
                    .toList();
            return new Detail(c.getId(), c.getName(), c.getSlug(), c.getBannerUrl(), c.getDiscountType(),
                    c.getDiscountValue(), c.getStartDate(), c.getEndDate(), c.isActive(), c.statusAt(now), items.size(),
                    c.getCreatedAt(), c.getUpdatedAt(), c.getDescription(), items);
        }
    }

    public record Overlap(Long productId, Long campaignId, String campaignName) {}
}
