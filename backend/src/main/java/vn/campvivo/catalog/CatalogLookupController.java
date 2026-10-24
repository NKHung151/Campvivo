package vn.campvivo.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.catalog.dto.BrandResponse;
import vn.campvivo.catalog.dto.CategoryNodeResponse;
import vn.campvivo.common.dto.ApiResponse;

import java.util.List;

/** API public tra cứu catalog — docs/02-ky-thuat/api.md mục 4.3: cây danh mục, thương hiệu. */
@RestController
@RequestMapping("/api")
public class CatalogLookupController {

    private final CategoryService categoryService;
    private final ProductService productService;

    public CatalogLookupController(CategoryService categoryService, ProductService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryNodeResponse>> categories() {
        return ApiResponse.of(categoryService.tree());
    }

    @GetMapping("/brands")
    public ApiResponse<List<BrandResponse>> brands() {
        return ApiResponse.of(productService.brands());
    }
}
