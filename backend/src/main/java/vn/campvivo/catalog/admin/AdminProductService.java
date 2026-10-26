package vn.campvivo.catalog.admin;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.Category;
import vn.campvivo.catalog.CategoryRepository;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductImage;
import vn.campvivo.catalog.ProductImageRepository;
import vn.campvivo.catalog.ProductRepository;
import vn.campvivo.catalog.ProductSpec;
import vn.campvivo.catalog.admin.AdminProductResponses.Detail;
import vn.campvivo.catalog.admin.AdminProductResponses.Summary;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.util.HtmlSanitizer;
import vn.campvivo.common.util.SlugUtil;

import static vn.campvivo.catalog.ProductSpecifications.available;
import static vn.campvivo.catalog.ProductSpecifications.hasBrand;
import static vn.campvivo.catalog.ProductSpecifications.inCategoryPath;
import static vn.campvivo.catalog.ProductSpecifications.isCountQuery;
import static vn.campvivo.catalog.ProductSpecifications.matchesKeyword;
import static vn.campvivo.catalog.ProductSpecifications.relevanceRank;
import static vn.campvivo.catalog.ProductSpecifications.toOrders;
import static vn.campvivo.catalog.ProductSpecifications.withCategoryFetched;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quản lý sản phẩm cho admin — FR-06, docs/02-ky-thuat/api.md mục 4.10–4.11.
 * <ul>
 *   <li>Không xoá cứng (BR-14): "xoá" ở giao diện = ẩn bằng {@code isActive=false}.</li>
 *   <li>Không sửa tồn kho ở đây (FR-06): sản phẩm mới có stock 0, nhập kho qua module inventory.</li>
 *   <li>Slug bỏ trống: tạo mới thì sinh từ tên (thêm hậu tố -2, -3 nếu trùng); sửa thì giữ slug cũ.
 *       Slug người dùng tự nhập mà trùng → 409 SLUG_ALREADY_EXISTS.</li>
 *   <li>Mô tả làm sạch HTML trước khi lưu; ảnh: tối đa 10, có ảnh thì đúng 1 ảnh chính.</li>
 * </ul>
 */
@Service
public class AdminProductService {

    private static final String DEFAULT_SLUG = "san-pham";

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;
    private final Clock clock;
    private final int lowStockThreshold;

    public AdminProductService(ProductRepository productRepository, ProductImageRepository imageRepository,
                               CategoryRepository categoryRepository,
                               EntityManager entityManager, Clock clock, AppProperties appProperties) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
        this.clock = clock;
        this.lowStockThreshold = appProperties.inventory().lowStockThreshold();
    }

    /** Danh sách có lọc + phân trang (page bắt đầu từ 1). Lọc danh mục gồm cả danh mục con. */
    @Transactional(readOnly = true)
    public PageResponse<Summary> list(AdminProductQuery q) {
        Specification<Product> spec = Specification.where(withCategoryFetched())
                .and(matchesKeyword(q.q()))
                .and(inCategoryTree(q.categoryId()))
                .and(hasBrand(q.brand()))
                .and(q.isActive() == null ? null : (root, query, cb) -> cb.equal(root.get("active"), q.isActive()))
                .and(stockIn(q.stock()))
                .and(ordering(q));

        // Thứ tự đặt trong Specification (mức liên quan / tồn kho) → Pageable không kèm sort
        Page<Product> result = productRepository.findAll(spec, PageRequest.of(q.page() - 1, q.limit()));

        List<Long> ids = result.getContent().stream().map(Product::getId).toList();
        Map<Long, String> primaryUrls = ids.isEmpty() ? Map.of()
                : imageRepository.findPrimaryByProductIds(ids).stream()
                        .collect(Collectors.toMap(i -> i.getProduct().getId(), ProductImage::getImageUrl, (a, b) -> a));

        Map<Long, String> categoryNames = categoryNamesOnPaths(result.getContent().stream()
                .map(p -> p.getCategory().getPath()).toList());

        return PageResponse.of(result.map(p -> Summary.from(p, primaryUrls.get(p.getId()),
                pathNames(p.getCategory().getPath(), categoryNames))));
    }

    /** Tên mọi danh mục xuất hiện trên các path ("/1/5/12/") — 1 truy vấn cho cả trang. */
    private Map<Long, String> categoryNamesOnPaths(Collection<String> paths) {
        Set<Long> ids = new HashSet<>();
        paths.forEach(path -> ids.addAll(pathIds(path)));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private static List<String> pathNames(String path, Map<Long, String> names) {
        return pathIds(path).stream().map(names::get).filter(n -> n != null).toList();
    }

    private static List<Long> pathIds(String path) {
        return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
    }

    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return Detail.from(findProduct(id));
    }

    @Transactional
    public Detail create(ProductRequest req) {
        Category category = findCategory(req.categoryId());
        validateImages(req.images(), -1L);
        String slug = isBlank(req.slug()) ? uniqueSlugFrom(req.name()) : requireFreeSlug(req.slug(), null);

        OffsetDateTime now = OffsetDateTime.now(clock);
        Product product = new Product(category, req.name().trim(), slug, req.price(), now);
        applyDetails(product, category, slug, req, now);
        addImages(product, req.images(), now);
        return Detail.from(productRepository.save(product));
    }

    @Transactional
    public Detail update(Long id, ProductRequest req) {
        Product product = findProduct(id);
        Category category = findCategory(req.categoryId());
        validateImages(req.images(), id);
        String slug = isBlank(req.slug()) ? product.getSlug() : requireFreeSlug(req.slug(), id);

        OffsetDateTime now = OffsetDateTime.now(clock);
        applyDetails(product, category, slug, req, now);

        // `image_url` unique: phải xoá ảnh cũ xuống DB trước khi thêm lại (Hibernate mặc định insert
        // trước delete khi flush), nếu không giữ lại cùng một ảnh sẽ vi phạm unique.
        product.getImages().clear();
        entityManager.flush();
        addImages(product, req.images(), now);
        return Detail.from(product);
    }

    /** Ẩn/hiện nhanh (BR-14). */
    @Transactional
    public Summary changeStatus(Long id, boolean active) {
        Product product = findProduct(id);
        product.changeActive(active, OffsetDateTime.now(clock));
        String primary = product.getImages().stream().filter(ProductImage::isPrimary)
                .map(ProductImage::getImageUrl).findFirst().orElse(null);
        String path = product.getCategory().getPath();
        return Summary.from(product, primary, pathNames(path, categoryNamesOnPaths(List.of(path))));
    }

    // ---------------------------------------------------------------------------------------------

    private Product findProduct(Long id) {
        return productRepository.findWithCategoryById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm.", null));
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> fieldError("categoryId", "Danh mục không tồn tại."));
    }

    private void applyDetails(Product product, Category category, String slug, ProductRequest req,
                              OffsetDateTime now) {
        product.updateDetails(category, req.name().trim(), slug, blankToNull(req.shortDescription()),
                cleanDescription(req.description()), blankToNull(req.brand()),
                req.price(), normalizeSpecs(req.specs()), req.isActive(), now);
    }

    /** Thông số: bỏ khoảng trắng thừa, không trùng tên (không phân biệt hoa thường), giữ thứ tự. */
    private static List<ProductSpec> normalizeSpecs(List<ProductRequest.SpecRequest> specs) {
        if (specs == null) {
            return List.of();
        }
        Set<String> names = new HashSet<>();
        List<ProductSpec> out = new ArrayList<>();
        for (ProductRequest.SpecRequest s : specs) {
            String name = s.name().trim();
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw fieldError("specs", "Thông số \"" + name + "\" bị trùng.");
            }
            out.add(new ProductSpec(name, s.value().trim()));
        }
        return out;
    }

    private void addImages(Product product, List<ProductRequest.ImageRequest> images, OffsetDateTime now) {
        images.stream()
                .sorted((a, b) -> Integer.compare(a.displayOrder(), b.displayOrder()))
                .forEach(i -> product.addImage(i.url().trim(), i.isPrimary(), i.displayOrder(), now));
    }

    /** Có ảnh thì đúng 1 ảnh chính; không trùng URL trong danh sách và không dùng URL của mặt hàng khác. */
    private void validateImages(List<ProductRequest.ImageRequest> images, long productId) {
        if (images.isEmpty()) {
            return;
        }
        long primaryCount = images.stream().filter(ProductRequest.ImageRequest::isPrimary).count();
        if (primaryCount != 1) {
            throw fieldError("images", "Cần chọn đúng 1 ảnh chính.");
        }
        Set<String> seen = new HashSet<>();
        for (ProductRequest.ImageRequest i : images) {
            if (!seen.add(i.url().trim())) {
                throw fieldError("images", "Có ảnh bị trùng trong danh sách.");
            }
        }
        List<String> usedElsewhere = imageRepository.findUrlsUsedElsewhere(seen, productId);
        if (!usedElsewhere.isEmpty()) {
            throw fieldError("images", "Ảnh đã được dùng cho sản phẩm hoặc combo khác: " + usedElsewhere.get(0));
        }
    }

    private String requireFreeSlug(String slug, Long excludeId) {
        boolean taken = excludeId == null ? productRepository.existsBySlug(slug)
                : productRepository.existsBySlugAndIdNot(slug, excludeId);
        if (taken) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS);
        }
        return slug;
    }

    /** Slug từ tên; trùng thì thêm -2, -3, ... */
    private String uniqueSlugFrom(String name) {
        String base = SlugUtil.toSlug(name);
        if (base.isEmpty()) {
            base = DEFAULT_SLUG;
        }
        String candidate = base;
        for (int n = 2; productRepository.existsBySlug(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    private static String cleanDescription(String html) {
        String clean = HtmlSanitizer.sanitize(html).trim();
        return clean.isEmpty() ? null : clean;
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static BusinessException fieldError(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                Map.of("fieldErrors", Map.of(field, message)));
    }

    // --- Specification ---------------------------------------------------------------------------

    /** Lọc theo tồn kho khả dụng = stock - reserved (BR-1). */
    private Specification<Product> stockIn(AdminProductQuery.StockFilter stock) {
        if (stock == null) {
            return null;
        }
        return (root, query, cb) -> {
            Expression<Integer> available = available(root, cb);
            return switch (stock) {
                case OUT_OF_STOCK -> cb.le(available, 0);
                case LOW_STOCK -> cb.between(available, 1, lowStockThreshold);
                case IN_STOCK -> cb.gt(available, 0);
            };
        };
    }

    /** Danh mục và mọi danh mục con (BR-13). */
    private Specification<Product> inCategoryTree(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return inCategoryPath(categoryRepository.findById(categoryId).map(Category::getPath).orElse(null));
    }

    /**
     * Thứ tự kết quả (không áp cho câu đếm): có từ khoá thì xếp theo mức liên quan trước; sau đó theo `sort`
     * (STOCK_ASC = số lượng khả dụng tăng dần).
     */
    private static Specification<Product> ordering(AdminProductQuery q) {
        return (root, query, cb) -> {
            if (isCountQuery(query)) {
                return null;
            }
            List<Order> orders = new ArrayList<>();
            if (!isBlank(q.q())) {
                orders.add(cb.asc(relevanceRank(root, cb, q.q())));
            }
            if (q.sort() == AdminProductQuery.SortOption.STOCK_ASC) {
                orders.add(cb.asc(available(root, cb)));
            } else {
                orders.addAll(toOrders(q.sort().toSort(), root, cb));
            }
            orders.add(cb.asc(root.get("id")));
            query.orderBy(orders);
            return null;
        };
    }
}
