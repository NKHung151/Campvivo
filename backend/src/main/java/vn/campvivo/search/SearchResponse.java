package vn.campvivo.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.campvivo.article.dto.ArticleResponses;
import vn.campvivo.catalog.dto.ItemSummary;
import vn.campvivo.combo.dto.ComboSummary;
import vn.campvivo.common.dto.PageResponse;

import java.util.List;

/**
 * Phản hồi `GET /search` (api.md mục 4.3). `data` luôn có đủ ba mảng (loại không được hỏi thì rỗng); `totals` là TỔNG số kết quả
 * của từng loại (để tab hiện "(n)") và luôn có đủ ba số; `pagination` chỉ có khi hỏi một `type` cụ thể và mô tả loại đó;
 * `suggestion` ("Có phải bạn muốn tìm…?") chỉ có khi không hỏi `type`, KHÔNG có kết quả nào và từ khoá đã sửa lỗi gõ có kết quả.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResponse(boolean success, Results data, Totals totals, PageResponse.Pagination pagination, Suggestion suggestion) {

    /** Từ khoá đã sửa lỗi gõ và tổng số kết quả của nó (luôn có ít nhất một kết quả). */
    public record Suggestion(String q, Totals totals) {
    }

    /** Một dòng gợi ý khi gõ (`GET /search/suggestions`): chỉ sản phẩm và combo đang bán. `type` là `PRODUCT` hoặc `COMBO`. */
    public record Item(String type, String name, String slug, String imageUrl) {
    }

    public record Results(List<ItemSummary> products, List<ComboSummary> combos, List<ArticleResponses.Summary> articles) {
    }

    public record Totals(long products, long combos, long articles) {
    }
}
