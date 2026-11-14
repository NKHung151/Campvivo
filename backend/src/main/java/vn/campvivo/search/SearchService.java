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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tìm kiếm gộp sản phẩm, combo, bài viết (FR-07, `GET /search`) và gợi ý khi gõ (`GET /search/suggestions`). Kết quả chính gọi lại dịch vụ
 * tìm kiếm sản phẩm và combo có sẵn nên cùng quy tắc (chỉ hàng đang bán, gõ không dấu được, giá sau chiến dịch); bài viết do
 * {@link ArticleSearch}. Chịu lỗi gõ sai bằng PostgreSQL `pg_trgm` ở HAI chỗ, không trộn kết quả mờ vào danh sách: dòng "Có phải bạn muốn
 * tìm…?" khi không có kết quả, và danh sách gợi ý khi gõ.
 */
@Service
public class SearchService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_QUERY_LENGTH = 100;
    /** Gợi ý khi gõ: từ khoá từ ngần này ký tự mới bật khớp gần giống (ngắn hơn thì trigram quá nhiễu: chỉ khớp đầu / chứa). */
    static final int MIN_FUZZY_LENGTH = 4;
    /** `word_similarity` tối thiểu để một tên được coi là "gần giống" từ khoá khi gợi ý (đo thử: "leeu" ≈ 0,5, từ không liên quan ≈ 0). */
    static final double SUGGEST_SIMILARITY = 0.4;
    /** `similarity` tối thiểu để sửa một từ gõ sai thành từ trong kho. */
    static final double WORD_FIX_SIMILARITY = 0.4;
    /** Chỉ sửa tối đa chừng này từ trong một từ khoá (tránh tốn truy vấn với từ khoá rất dài). */
    private static final int MAX_FIXED_WORDS = 6;

    private final ProductService productService;
    private final ComboService comboService;
    private final ArticleSearch articleSearch;
    private final SearchSuggestionRepository suggestionRepository;

    public SearchService(ProductService productService, ComboService comboService, ArticleSearch articleSearch,
                         SearchSuggestionRepository suggestionRepository) {
        this.productService = productService;
        this.comboService = comboService;
        this.articleSearch = articleSearch;
        this.suggestionRepository = suggestionRepository;
    }

    /**
     * @param type null = cả ba loại, mỗi loại tối đa `limit` kết quả đầu tiên (`page` bị bỏ qua); khác null = chỉ loại đó, có phân trang.
     *             `totals` luôn đủ ba số. Không `type` mà không có kết quả nào thì thử sửa lỗi gõ ({@link SearchResponse#suggestion()}).
     */
    public SearchResponse search(String rawQuery, SearchType type, int page, int limit) {
        String q = normalize(rawQuery);

        if (type == null) {
            PageResponse<ItemSummary> products = products(q, 1, limit);
            PageResponse<ComboSummary> combos = combos(q, 1, limit);
            Page<ArticleResponses.Summary> articles = articleSearch.search(q, 1, limit);
            var totals = new SearchResponse.Totals(products.pagination().totalItems(), combos.pagination().totalItems(),
                    articles.getTotalElements());
            SearchResponse.Suggestion suggestion = totals.products() + totals.combos() + totals.articles() == 0
                    ? didYouMean(q).orElse(null) : null;
            return new SearchResponse(true,
                    new SearchResponse.Results(products.data(), combos.data(), articles.getContent()), totals, null, suggestion);
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
                    products.pagination(), null);
            case COMBO -> new SearchResponse(true, new SearchResponse.Results(List.of(), combos.data(), List.of()), totals,
                    combos.pagination(), null);
            case ARTICLE -> new SearchResponse(true, new SearchResponse.Results(List.of(), List.of(), articles.getContent()), totals,
                    PageResponse.of(articles).pagination(), null);
        };
    }

    /**
     * Gợi ý khi gõ: tối đa `limit` sản phẩm / combo đang bán. Thứ tự: tên bắt đầu bằng từ khoá, tên chứa từ khoá, thương hiệu chứa từ khoá,
     * rồi tên gần giống (chỉ khi từ khoá đủ dài). Bài viết chưa có trong gợi ý (trang chi tiết bài viết chưa nối API).
     */
    public List<SearchResponse.Item> suggest(String rawQuery, int limit) {
        String folded = SearchText.fold(normalize(rawQuery));
        return suggestionRepository.suggest(folded, folded.length() >= MIN_FUZZY_LENGTH, SUGGEST_SIMILARITY, limit).stream()
                .map(h -> new SearchResponse.Item(h.type(), h.name(), h.slug(), h.imageUrl()))
                .toList();
    }

    /**
     * Sửa từng từ gõ sai thành từ giống nhất trong kho từ (xem {@link SearchSuggestionRepository#closestWord}); chỉ trả khi từ khoá sau sửa
     * KHÁC từ khoá gốc và THẬT SỰ có kết quả (không gợi ý một từ khoá cũng trống).
     */
    private Optional<SearchResponse.Suggestion> didYouMean(String q) {
        List<String> tokens = new ArrayList<>(List.of(SearchText.fold(q).split("[\\s_.,:;()/\"'!?+&|-]+")));
        tokens.removeIf(String::isEmpty);
        if (tokens.isEmpty() || tokens.size() > MAX_FIXED_WORDS) {
            return Optional.empty();
        }
        List<String> fixed = new ArrayList<>();
        boolean changed = false;
        for (String token : tokens) {
            var closest = token.length() < 3 ? Optional.<SearchSuggestionRepository.ClosestWord>empty()
                    : suggestionRepository.closestWord(token, WORD_FIX_SIMILARITY);
            if (closest.isPresent() && !closest.get().folded().equals(token)) {
                fixed.add(closest.get().original());
                changed = true;
            } else {
                fixed.add(token);
            }
        }
        if (!changed) {
            return Optional.empty();
        }
        String corrected = String.join(" ", fixed);
        var totals = new SearchResponse.Totals(products(corrected, 1, 1).pagination().totalItems(),
                combos(corrected, 1, 1).pagination().totalItems(), articleSearch.search(corrected, 1, 1).getTotalElements());
        return totals.products() + totals.combos() + totals.articles() > 0
                ? Optional.of(new SearchResponse.Suggestion(corrected, totals)) : Optional.empty();
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
    static String normalize(String raw) {
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
