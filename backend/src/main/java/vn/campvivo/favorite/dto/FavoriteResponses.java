package vn.campvivo.favorite.dto;

import vn.campvivo.favorite.FavoriteType;

import java.time.OffsetDateTime;
import java.util.List;

/** Response module yêu thích — api.md mục 4.2, 4.6 (FR-20, D-47). */
public final class FavoriteResponses {

    private FavoriteResponses() {
    }

    public record Created(Long id) {}

    /** `item`: `ItemSummary` (PRODUCT), `ComboSummary` (COMBO) hoặc `ArticleSummary` (ARTICLE). */
    public record Item(Long id, FavoriteType type, Object item, OffsetDateTime createdAt) {}

    /** Mọi mục của khách (kể cả mặt hàng đang ẩn), mỗi mảng theo id tăng dần. */
    public record Ids(List<Long> productIds, List<Long> comboIds, List<Long> articleIds) {}
}
