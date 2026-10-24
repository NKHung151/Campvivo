package vn.campvivo.catalog;

import jakarta.persistence.criteria.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.dto.BrandResponse;
import vn.campvivo.catalog.dto.ItemSummary;
import vn.campvivo.catalog.dto.ProductDetailResponse;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static vn.campvivo.catalog.ProductSpecifications.hasBrand;
import static vn.campvivo.catalog.ProductSpecifications.inCategoryPath;
import static vn.campvivo.catalog.ProductSpecifications.matchesKeyword;

/** Catalog public — FR-07, docs/02-ky-thuat/api.md mục 4.3. Chỉ sản phẩm đang bán (is_active). */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, ProductImageRepository imageRepository,
                          CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.categoryRepository = categoryRepository;
    }

    /** Danh sách có lọc + phân trang (page bắt đầu từ 1). Danh mục không tồn tại → danh sách rỗng. */
    @Transactional(readOnly = true)
    public PageResponse<ItemSummary> list(ProductQuery q) {
        Specification<Product> spec = Specification.<Product>where((root, query, cb) -> cb.isTrue(root.get("active")))
                .and(q.category() == null || q.category().isBlank() ? null
                        : inCategoryPath(categoryRepository.findBySlug(q.category().trim()).map(Category::getPath).orElse(null)))
                .and(hasBrand(q.brand()))
                .and(matchesKeyword(q.q()))
                .and(q.slugs() == null || q.slugs().isEmpty() ? null : (root, query, cb) -> root.get("slug").in(q.slugs()))
                .and(q.minPrice() == null ? null : (root, query, cb) -> cb.ge(root.get("price"), q.minPrice()))
                .and(q.maxPrice() == null ? null : (root, query, cb) -> cb.le(root.get("price"), q.maxPrice()));

        // Có từ khoá: xếp theo mức liên quan trước rồi theo sortBy (đặt trong Specification, Pageable không kèm sort)
        boolean byRelevance = q.q() != null && !q.q().isBlank();
        spec = spec.and(byRelevance ? (root, query, cb) -> {
            if (!ProductSpecifications.isCountQuery(query)) {
                List<Order> orders = new ArrayList<>();
                orders.add(cb.asc(ProductSpecifications.relevanceRank(root, cb, q.q())));
                orders.addAll(ProductSpecifications.toOrders(q.sortBy().toSort(), root, cb));
                query.orderBy(orders);
            }
            return null;
        } : null);
        Page<Product> result = productRepository.findAll(spec, byRelevance
                ? PageRequest.of(q.page() - 1, q.limit())
                : PageRequest.of(q.page() - 1, q.limit(), q.sortBy().toSort()));
        List<Long> ids = result.getContent().stream().map(Product::getId).toList();
        Map<Long, String> primaryUrls = ids.isEmpty() ? Map.of()
                : imageRepository.findPrimaryByProductIds(ids).stream()
                        .collect(Collectors.toMap(i -> i.getProduct().getId(), ProductImage::getImageUrl, (a, b) -> a));
        return PageResponse.of(result.map(p -> ItemSummary.from(p, primaryUrls.get(p.getId()))));
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getBySlug(String slug) {
        Product product = productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm.", null));
        List<Long> pathIds = Arrays.stream(product.getCategory().getPath().split("/"))
                .filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
        List<Category> path = categoryRepository.findAllById(pathIds).stream()
                .sorted(Comparator.comparingInt(c -> pathIds.indexOf(c.getId())))
                .toList();
        return ProductDetailResponse.from(product, path);
    }

    /** Thương hiệu đang có sản phẩm bán (GET /brands). */
    @Transactional(readOnly = true)
    public List<BrandResponse> brands() {
        return productRepository.findActiveBrands();
    }
}
