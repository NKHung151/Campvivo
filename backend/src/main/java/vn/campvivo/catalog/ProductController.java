package vn.campvivo.catalog;

import org.springframework.web.bind.annotation.*;
import vn.campvivo.common.dto.ApiResponse;

/**
 * Controller mỏng: chỉ nhận request, gọi Service, trả ApiResponse — không chứa nghiệp vụ.
 * Endpoint khớp docs/02-ky-thuat/api.md.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{slug}")
    public ApiResponse<ProductResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.of(productService.getBySlug(slug));
    }
}
