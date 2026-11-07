package vn.campvivo.promotion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductService;
import vn.campvivo.catalog.dto.ItemSummary;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.promotion.dto.CampaignDetailResponse;
import vn.campvivo.promotion.dto.CampaignSummaryResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chiến dịch phía khách — FR-17, BR-6. Chỉ chiến dịch đang hiệu lực; ngược lại 404. */
@Service
public class CampaignService {

    private final DiscountCampaignRepository campaignRepository;
    private final ProductService productService;

    public CampaignService(DiscountCampaignRepository campaignRepository, ProductService productService) {
        this.campaignRepository = campaignRepository;
        this.productService = productService;
    }

    /** Chiến dịch đang hiệu lực, sắp kết thúc trước (PromoBar, trang /promotions). */
    @Transactional(readOnly = true)
    public List<CampaignSummaryResponse> listActive() {
        List<DiscountCampaign> campaigns = campaignRepository.findAllActive();
        Map<Long, Long> counts = new HashMap<>();
        if (!campaigns.isEmpty()) {
            campaignRepository.countActiveProducts(campaigns.stream().map(DiscountCampaign::getId).toList())
                    .forEach(r -> counts.put((Long) r[0], (Long) r[1]));
        }
        return campaigns.stream().map(c -> CampaignSummaryResponse.from(c, counts.getOrDefault(c.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public CampaignDetailResponse getActiveBySlug(String slug) {
        DiscountCampaign campaign = campaignRepository.findActiveBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Chương trình khuyến mãi không tồn tại hoặc đã kết thúc.", null));
        List<Product> products = campaign.getProducts().stream().filter(Product::isActive).toList();
        Map<Long, String> images = productService.primaryImageUrls(products.stream().map(Product::getId).toList());
        List<ItemSummary> items = products.stream().map(p -> ItemSummary.from(p, images.get(p.getId()))).toList();
        return CampaignDetailResponse.from(campaign, items);
    }
}
