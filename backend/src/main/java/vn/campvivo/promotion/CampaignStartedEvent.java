package vn.campvivo.promotion;

import java.util.List;

/**
 * Chiến dịch giảm giá vừa bắt đầu, được {@link PromotionAnnouncementJob} công bố đúng một lần (D-45). Dùng cho
 * `FAVORITE_ON_SALE` (Phần 7 — chưa có listener).
 */
public record CampaignStartedEvent(long campaignId, String name, String slug, List<Long> productIds) {

    public CampaignStartedEvent {
        productIds = List.copyOf(productIds);
    }
}
