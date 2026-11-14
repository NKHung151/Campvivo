package vn.campvivo.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.common.dto.ApiResponse;

import java.util.List;

/**
 * Tìm kiếm công khai sản phẩm, combo, bài viết — docs/02-ky-thuat/api.md mục 4.3 (FR-07). Công khai (SecurityConfig đã mở
 * `/api/search`); kiểm tra từ khoá 2–100 ký tự nằm ở {@link SearchService}.
 */
@RestController
@RequestMapping("/api/search")
@Validated
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** Gợi ý khi gõ ở ô tìm kiếm: tối đa `limit` (mặc định 6, tối đa 10) sản phẩm / combo đang bán; `q` kiểm như `GET /search`. */
    @GetMapping("/suggestions")
    public ApiResponse<List<SearchResponse.Item>> suggestions(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "6") @Min(value = 1, message = "Số gợi ý từ 1 đến 10.")
            @Max(value = 10, message = "Số gợi ý từ 1 đến 10.") int limit) {
        return ApiResponse.of(searchService.suggest(q, limit));
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SearchType type,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Số kết quả mỗi trang từ 1 đến 50.")
            @Max(value = 50, message = "Số kết quả mỗi trang từ 1 đến 50.") int limit) {
        return searchService.search(q, type, page, limit);
    }
}
