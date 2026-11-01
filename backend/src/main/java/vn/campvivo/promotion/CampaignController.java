package vn.campvivo.promotion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.promotion.dto.CampaignDetailResponse;
import vn.campvivo.promotion.dto.CampaignSummaryResponse;

import java.util.List;

/** Trang chiến dịch công khai — api.md mục 4.3 (GET công khai, SecurityConfig). */
@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    public ApiResponse<List<CampaignSummaryResponse>> list() {
        return ApiResponse.of(campaignService.listActive());
    }

    @GetMapping("/{slug}")
    public ApiResponse<CampaignDetailResponse> get(@PathVariable String slug) {
        return ApiResponse.of(campaignService.getActiveBySlug(slug));
    }
}
