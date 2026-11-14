package vn.campvivo.search;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import vn.campvivo.article.dto.ArticleResponses;
import vn.campvivo.catalog.ProductQuery;
import vn.campvivo.catalog.ProductService;
import vn.campvivo.catalog.dto.ItemSummary;
import vn.campvivo.combo.ComboService;
import vn.campvivo.combo.dto.ComboSummary;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.util.SlugUtil;

import java.util.List;
import java.util.Map;

/**
 * Tìm kiếm gộp sản phẩm, combo, bài viết (FR-07, `GET /search`). Gọi lại dịch vụ tìm kiếm sản phẩm và combo có sẵn nên cùng quy tắc
 * (chỉ hàng đang bán, gõ không dấu được, giá sau chiến dịch); bài viết do {@link ArticleSearch}. Không đụng cơ sở dữ liệu trực tiếp.
 */
@Service
public class SearchService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 100;

    private final ProductService productService;
    private final ComboService comboService;
    private final ArticleSearch articleSearch;

    public SearchService(ProductService productService, ComboService comboService, ArticleSearch articleSearch) {
        this.productService = productService;
        this.comboService = comboService;
        this.articleSearch = articleSearch;
    }

    /**
     * @param type null = cả ba loại, mỗi loại tối đa `limit` kết quả đầu tiên (`page` bị bỏ qua); khác null = chỉ loại đó, có phân trang.
     *             `totals` luôn đủ ba số.
     */
    public SearchResponse search(String rawQuery, SearchType type, int page, int limit) {
        String q = normalize(rawQuery);

        if (type == null) {
            PageResponse<ItemSummary> products = products(q, 1, limit);
            PageResponse<ComboSummary> combos = combos(q, 1, limit);
            Page<ArticleResponses.Summary> articles = articleSearch.search(q, 1, limit);
            return new SearchResponse(true,
                    new SearchResponse.Results(products.data(), combos.data(), articles.getContent()),
                    new SearchResponse.Totals(products.pagination().totalItems(), combos.pagination().totalItems(),
                            articles.getTotalElements()),
                    null);
        }

        // Hỏi một loại: loại đó lấy đúng trang yêu cầu, hai loại còn lại chỉ để đếm tổng (một dòng).
        PageResponse<ItemSummary> products = products(q, type == SearchType.PRODUCT ? page : 1, type == SearchType.PRODUCT ? limit : 1);
        PageResponse<ComboSummary> combos = combos(q, type == SearchType.COMBO ? page : 1, type == SearchType.COMBO ? limit : 1);
        Page<ArticleResponses.Summary> articles = articleSearch.search(q, type == SearchType.ARTICLE ? page : 1,
                type == SearchType.ARTICLE ? limit : 1);
        var totals = new SearchResponse.Totals(products.pagination().totalItems(), combos.pagination().totalItems(),
                articles.getTotalElements());
        return switch (type) {
            case PRODUCT -> new SearchResponse(true, new SearchResponse.Results(products.data(), List.of(), List.of()), totals,
                    products.pagination());
            case COMBO -> new SearchResponse(true, new SearchResponse.Results(List.of(), combos.data(), List.of()), totals,
                    combos.pagination());
            case ARTICLE -> new SearchResponse(true, new SearchResponse.Results(List.of(), List.of(), articles.getContent()), totals,
                    PageResponse.of(articles).pagination());
        };
    }

    private PageResponse<ItemSummary> products(String q, int page, int limit) {
        return productService.list(new ProductQuery(null, null, q, null, null, null, false, ProductQuery.SortBy.newest, page, limit));
    }

    private PageResponse<ComboSummary> combos(String q, int page, int limit) {
        return comboService.list(null, q, ComboService.SortBy.newest, page, limit);
    }

    /**
     * Bỏ khoảng trắng hai đầu; phải còn 2–100 ký tự và có ít nhất một chữ hoặc số. Từ khoá chỉ gồm ký hiệu ("!!") bị từ chối vì tìm
     * combo theo từ khoá không có từ nào sẽ khớp MỌI combo.
     */
    private static String normalize(String raw) {
        String q = raw == null ? "" : raw.trim();
        String message = null;
        if (q.length() < MIN_QUERY_LENGTH) {
            message = "Từ khoá phải có ít nhất " + MIN_QUERY_LENGTH + " ký tự.";
        } else if (q.length() > MAX_QUERY_LENGTH) {
            message = "Từ khoá tối đa " + MAX_QUERY_LENGTH + " ký tự.";
        } else if (SlugUtil.toSlug(q).isEmpty()) {
            message = "Từ khoá phải có ít nhất một chữ hoặc số.";
        }
        if (message != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                    Map.of("fieldErrors", Map.of("q", message)));
        }
        return q;
    }
}
