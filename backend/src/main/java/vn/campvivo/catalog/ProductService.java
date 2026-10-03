package vn.campvivo.catalog;

import org.springframework.stereotype.Service;
import vn.campvivo.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductResponse getBySlug(String slug) {
        Product product = productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new BusinessException(
                        "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm.", HttpStatus.NOT_FOUND));
        // TODO: lấy primaryImageUrl thật từ ProductImage khi module ảnh được code.
        return ProductResponse.from(product, null);
    }

    // TODO: search/filter/phân trang theo GET /products trong docs/02-ky-thuat/api.md
    // (dùng Specification để kết hợp nhiều điều kiện, theo .agents/rules/frontend.md
    // tương ứng phía FE và .agents/skills/backend-development/SKILL.md phía BE).
}
