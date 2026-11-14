package vn.campvivo.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
