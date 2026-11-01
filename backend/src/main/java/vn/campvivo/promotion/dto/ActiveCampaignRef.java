package vn.campvivo.promotion.dto;

import java.time.OffsetDateTime;

/** Trường `campaign` của `ProductDetail` (api.md mục 4.3): chiến dịch đang áp cho sản phẩm (mức giảm tốt nhất). */
public record ActiveCampaignRef(String name, String slug, OffsetDateTime endDate) {}
