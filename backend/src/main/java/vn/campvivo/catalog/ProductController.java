package vn.campvivo.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.catalog.dto.CategoryBestSellers;
import vn.campvivo.catalog.dto.ItemSummary;
import vn.campvivo.catalog.dto.ProductDetailResponse;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller mỏng: chỉ nhận request, gọi Service, trả ApiResponse — không chứa nghiệp vụ.
 * Endpoint khớp docs/02-ky-thuat/api.md mục 4.3.
 */
@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PageResponse<ItemSummary> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @Size(max = 100, message = "Tối đa 100 slug.") List<String> slugs,
            @RequestParam(required = false) @Min(value = 0, message = "Giá không hợp lệ.") BigDecimal minPrice,
            @RequestParam(required = false) @Min(value = 0, message = "Giá không hợp lệ.") BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean onSale,
            @RequestParam(defaultValue = "newest") ProductQuery.SortBy sortBy,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Số sản phẩm mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số sản phẩm mỗi trang từ 1 đến 100.") int limit) {
        return productService.list(new ProductQuery(category, brand, q, slugs, minPrice, maxPrice, onSale, sortBy, page, limit));
    }

    /** Vài sản phẩm bán chạy của mỗi danh mục cấp 2 — menu cửa hàng hiện thay danh mục cấp 3 (D-38). */
    @GetMapping("/best-sellers")
    public ApiResponse<List<CategoryBestSellers>> bestSellers(
            @RequestParam(defaultValue = "3") @Min(value = 1, message = "Số sản phẩm mỗi danh mục từ 1 đến 6.")
            @Max(value = 6, message = "Số sản phẩm mỗi danh mục từ 1 đến 6.") int limit) {
        return ApiResponse.of(productService.bestSellersBySubcategory(limit));
    }

    @GetMapping("/{slug}")
    public ApiResponse<ProductDetailResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.of(productService.getBySlug(slug));
    }
}
