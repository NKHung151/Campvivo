package vn.campvivo.combo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.combo.dto.ComboDetailResponse;
import vn.campvivo.combo.dto.ComboSummary;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;

/** API combo public — docs/02-ky-thuat/api.md mục 4.3. */
@RestController
@RequestMapping("/api/combos")
@Validated
public class ComboController {

    private final ComboService comboService;

    public ComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    @GetMapping
    public PageResponse<ComboSummary> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "newest") ComboService.SortBy sortBy,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Số combo mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số combo mỗi trang từ 1 đến 100.") int limit) {
        return comboService.list(category, q, sortBy, page, limit);
    }

    @GetMapping("/{slug}")
    public ApiResponse<ComboDetailResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.of(comboService.getBySlug(slug));
    }
}
