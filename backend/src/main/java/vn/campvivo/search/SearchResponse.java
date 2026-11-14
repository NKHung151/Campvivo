package vn.campvivo.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.campvivo.article.dto.ArticleResponses;
import vn.campvivo.catalog.dto.ItemSummary;
import vn.campvivo.combo.dto.ComboSummary;
import vn.campvivo.common.dto.PageResponse;

import java.util.List;

/**
 * Phản hồi `GET /search` (api.md mục 4.3). `data` luôn có đủ ba mảng (loại không được hỏi thì rỗng); `totals` là TỔNG số kết quả
 * của từng loại (để tab hiện "(n)") và luôn có đủ ba số; `pagination` chỉ có khi hỏi một `type` cụ thể và mô tả loại đó.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResponse(boolean success, Results data, Totals totals, PageResponse.Pagination pagination) {

    public record Results(List<ItemSummary> products, List<ComboSummary> combos, List<ArticleResponses.Summary> articles) {
    }

    public record Totals(long products, long combos, long articles) {
    }
}
