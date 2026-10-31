package vn.campvivo.combo.admin;

import jakarta.persistence.EntityManager;
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
import vn.campvivo.combo.ComboItem;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.combo.ProductComboRepository;
import vn.campvivo.combo.admin.AdminComboResponses.Detail;
import vn.campvivo.combo.admin.AdminComboResponses.Summary;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.util.HtmlSanitizer;
import vn.campvivo.common.util.SlugUtil;
import vn.campvivo.inventory.InventoryLogService;
import vn.campvivo.inventory.InventoryReason;

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
import java.util.function.Function;
import java.util.stream.Collectors;

import static vn.campvivo.catalog.ProductSpecifications.escapeLike;
import static vn.campvivo.catalog.ProductSpecifications.isCountQuery;

/**
 * Quản lý combo — FR-15, D-03.
 * <ul>
 *   <li>Combo gồm ≥ 2 sản phẩm khác nhau, mỗi sản phẩm 1..99 cái trong một bộ.</li>
 *   <li>Tồn kho combo theo bộ (D-03): combo mới = 0; "đóng gói" N bộ trừ kho từng sản phẩm × số lượng (D-22) và cộng N vào
 *       combo; "tháo" làm ngược lại (chỉ số bộ chưa bị giữ chỗ). Khách mua combo chỉ trừ kho combo.</li>
 *   <li>Đóng gói/tháo ghi nhật ký kho cho combo và từng sản phẩm thành phần (D-27).</li>
 *   <li>Combo còn bộ đã đóng gói thì không đổi được thành phần (phải tháo hết trước) — để hàng tháo ra đúng hàng đã lấy.</li>
 *   <li>Không xoá cứng (BR-14): "xoá" = ẩn bằng isActive=false.</li>
 *   <li>Slug/ảnh/mô tả: cùng quy tắc với sản phẩm (slug trống → sinh từ tên; đúng 1 ảnh chính; HTML được làm sạch).</li>
 * </ul>
 */
@Service
public class AdminComboService {

    private static final String DEFAULT_SLUG = "combo";

    private final ProductComboRepository comboRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryLogService logService;
    private final EntityManager entityManager;
    private final Clock clock;

    public AdminComboService(ProductComboRepository comboRepository, ProductRepository productRepository,
                             ProductImageRepository productImageRepository, CategoryRepository categoryRepository,
                             InventoryLogService logService, EntityManager entityManager, Clock clock) {
        this.comboRepository = comboRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.logService = logService;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Danh sách có lọc + phân trang (page bắt đầu từ 1). Lọc danh mục gồm cả danh mục con. */
    @Transactional(readOnly = true)
    public PageResponse<Summary> list(AdminComboQuery q) {
        Specification<ProductCombo> spec = Specification.<ProductCombo>where(withCategoryFetched())
                .and(matchesKeyword(q.q()))
                .and(inCategoryTree(q.categoryId()))
                .and(q.isActive() == null ? null : (root, query, cb) -> cb.equal(root.get("active"), q.isActive()));
        Page<ProductCombo> result = comboRepository.findAll(spec, PageRequest.of(q.page() - 1, q.limit(), q.sort().toSort()));

        List<Long> ids = result.getContent().stream().map(ProductCombo::getId).toList();
        Map<Long, String> primary = comboRepository.findCoverImageUrls(ids);
        Map<Long, String> names = categoryNames(result.getContent().stream().map(c -> c.getCategory().getPath()).toList());
        return PageResponse.of(result.map(c -> Summary.from(c, primary.get(c.getId()), pathNames(c.getCategory().getPath(), names))));
    }

    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return detail(find(id));
    }

    @Transactional
    public Detail create(ComboRequest req) {
        Category category = findCategory(req.categoryId());
        List<ResolvedItem> items = resolveItems(req.items());
        List<ComboRequest.ImageRequest> images = req.images() == null ? List.of() : req.images();
        validateImages(images, -1L);
        String slug = isBlank(req.slug()) ? uniqueSlugFrom(req.name()) : requireFreeSlug(req.slug(), null);

        OffsetDateTime now = OffsetDateTime.now(clock);
        ProductCombo combo = new ProductCombo(category, req.name().trim(), slug, req.comboPrice(), now);
        combo.updateDetails(category, req.name().trim(), slug, cleanDescription(req.description()), req.comboPrice(),
                req.isActive(), now);
        items.forEach(i -> combo.addItem(i.product(), i.quantity()));
        addImages(combo, images, now);
        return detail(comboRepository.save(combo));
    }

    @Transactional
    public Detail update(Long id, ComboRequest req) {
        ProductCombo combo = find(id);
        Category category = findCategory(req.categoryId());
        List<ResolvedItem> items = resolveItems(req.items());
        if (combo.getStockQuantity() > 0 && !sameItems(combo, items)) {
            throw fieldError("items", "Combo còn " + combo.getStockQuantity() + " bộ đã đóng gói — hãy tháo hết trước khi đổi"
                    + " sản phẩm trong combo (để hàng tháo ra đúng hàng đã lấy).");
        }
        List<ComboRequest.ImageRequest> images = req.images() == null ? List.of() : req.images();
        validateImages(images, id);
        String slug = isBlank(req.slug()) ? combo.getSlug() : requireFreeSlug(req.slug(), id);

        OffsetDateTime now = OffsetDateTime.now(clock);
        combo.updateDetails(category, req.name().trim(), slug, cleanDescription(req.description()), req.comboPrice(),
                req.isActive(), now);
        // Unique (combo_id, product_id) và image_url: xoá dòng cũ xuống DB trước khi thêm lại
        combo.getItems().clear();
        combo.getImages().clear();
        entityManager.flush();
        items.forEach(i -> combo.addItem(i.product(), i.quantity()));
        addImages(combo, images, now);
        return detail(combo);
    }

    /** Ẩn/hiện (BR-14). */
    @Transactional
    public Summary changeStatus(Long id, boolean active) {
        ProductCombo combo = find(id);
        combo.changeActive(active, OffsetDateTime.now(clock));
        String primary = comboRepository.findCoverImageUrls(List.of(id)).get(id);
        String path = combo.getCategory().getPath();
        return Summary.from(combo, primary, pathNames(path, categoryNames(List.of(path))));
    }

    // ---------------------------------------------------------------------------------------------

    private Detail detail(ProductCombo combo) {
        List<Long> productIds = combo.getItems().stream().map(i -> i.getProduct().getId()).toList();
        Map<Long, String> images = productIds.isEmpty() ? Map.of()
                : productImageRepository.findPrimaryByProductIds(productIds).stream()
                        .collect(Collectors.toMap(i -> i.getProduct().getId(), ProductImage::getImageUrl, (a, b) -> a));
        return Detail.from(combo, productIds.stream().map(images::get).toList());
    }

    private record ResolvedItem(Product product, int quantity) {}

    /** ≥ 2 sản phẩm khác nhau, sản phẩm phải tồn tại. Giữ đúng thứ tự dòng. */
    private List<ResolvedItem> resolveItems(List<ComboRequest.ItemRequest> items) {
        Set<Long> ids = new HashSet<>();
        for (ComboRequest.ItemRequest i : items) {
            if (!ids.add(i.productId())) {
                throw fieldError("items", "Một sản phẩm chỉ được thêm một lần — hãy tăng số lượng thay vì thêm dòng mới.");
            }
        }
        Map<Long, Product> found = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<ResolvedItem> out = new ArrayList<>();
        for (ComboRequest.ItemRequest i : items) {
            Product p = found.get(i.productId());
            if (p == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Sản phẩm #" + i.productId() + " trong combo không tồn tại.", null);
            }
            out.add(new ResolvedItem(p, i.quantity()));
        }
        return out;
    }

    /** Thành phần không đổi (cùng sản phẩm, cùng số lượng; không tính thứ tự). */
    private static boolean sameItems(ProductCombo combo, List<ResolvedItem> items) {
        Map<Long, Integer> current = combo.getItems().stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), ComboItem::getQuantity, Integer::sum));
        Map<Long, Integer> wanted = items.stream()
                .collect(Collectors.toMap(i -> i.product().getId(), ResolvedItem::quantity, Integer::sum));
        return current.equals(wanted);
    }

    // --- Đóng gói / tháo combo (D-22) --------------------------------------------------------------

    /** Đóng gói `sets` bộ: trừ kho từng sản phẩm (số lượng × sets), cộng `sets` vào kho combo. Thiếu hàng → 409 OUT_OF_STOCK. */
    @Transactional
    public Detail pack(Long id, int sets, long userId) {
        ProductCombo combo = lockCombo(id);
        Map<Long, Product> products = lockProducts(combo);
        List<String> shortages = new ArrayList<>();
        for (ComboItem i : combo.getItems()) {
            Product p = products.get(i.getProduct().getId());
            int need = i.getQuantity() * sets;
            if (p.getAvailableQuantity() < need) {
                shortages.add(p.getName() + ": cần " + need + ", còn " + Math.max(0, p.getAvailableQuantity()));
            }
        }
        if (!shortages.isEmpty()) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK,
                    "Không đủ hàng để đóng gói " + sets + " bộ — " + String.join("; ", shortages) + ".",
                    Map.of("shortages", shortages));
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        String note = "Đóng gói " + sets + " bộ combo \"" + combo.getName() + "\"";
        combo.getItems().forEach(i -> {
            Product p = products.get(i.getProduct().getId());
            p.takeForCombo(i.getQuantity() * sets, now);
            logService.record(p, userId, -i.getQuantity() * sets, InventoryReason.EXPORT, note);
        });
        combo.addPackedSets(sets, now);
        logService.record(combo, userId, sets, InventoryReason.IMPORT, note);
        return detail(combo);
    }

    /** Tháo `sets` bộ (chưa bị giữ chỗ): trả hàng về kho từng sản phẩm, trừ kho combo. */
    @Transactional
    public Detail unpack(Long id, int sets, long userId) {
        ProductCombo combo = lockCombo(id);
        if (combo.getAvailableQuantity() < sets) {
            throw new BusinessException(ErrorCode.INVALID_STOCK_ADJUSTMENT, "Chỉ tháo được tối đa "
                    + Math.max(0, combo.getAvailableQuantity()) + " bộ (số bộ chưa bị giữ chỗ cho đơn hàng).", null);
        }
        Map<Long, Product> products = lockProducts(combo);
        OffsetDateTime now = OffsetDateTime.now(clock);
        String note = "Tháo " + sets + " bộ combo \"" + combo.getName() + "\"";
        combo.removePackedSets(sets, now);
        logService.record(combo, userId, -sets, InventoryReason.EXPORT, note);
        combo.getItems().forEach(i -> {
            Product p = products.get(i.getProduct().getId());
            p.returnFromCombo(i.getQuantity() * sets, now);
            logService.record(p, userId, i.getQuantity() * sets, InventoryReason.IMPORT, note);
        });
        return detail(combo);
    }

    private ProductCombo lockCombo(Long id) {
        return comboRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy combo.", null));
    }

    private static void addImages(ProductCombo combo, List<ComboRequest.ImageRequest> images, OffsetDateTime now) {
        images.stream()
                .sorted((a, b) -> Integer.compare(a.displayOrder(), b.displayOrder()))
                .forEach(i -> combo.addImage(i.url().trim(), i.isPrimary(), i.displayOrder(), now));
    }

    /** Có ảnh thì đúng 1 ảnh chính; không trùng URL trong danh sách và không dùng URL của sản phẩm/combo khác. */
    private void validateImages(List<ComboRequest.ImageRequest> images, long comboId) {
        if (images.isEmpty()) {
            return;
        }
        if (images.stream().filter(ComboRequest.ImageRequest::isPrimary).count() != 1) {
            throw fieldError("images", "Cần chọn đúng 1 ảnh chính.");
        }
        Set<String> seen = new HashSet<>();
        for (ComboRequest.ImageRequest i : images) {
            if (!seen.add(i.url().trim())) {
                throw fieldError("images", "Có ảnh bị trùng trong danh sách.");
            }
        }
        List<String> used = comboRepository.findUrlsUsedElsewhere(seen, comboId);
        if (!used.isEmpty()) {
            throw fieldError("images", "Ảnh đã được dùng cho sản phẩm hoặc combo khác: " + used.get(0));
        }
    }

    /** Khoá các sản phẩm thành phần theo id tăng dần (tránh deadlock với đặt hàng — database.md mục 6). */
    private Map<Long, Product> lockProducts(ProductCombo combo) {
        List<Long> ids = combo.getItems().stream().map(i -> i.getProduct().getId()).distinct().sorted().toList();
        return productRepository.findAllByIdForUpdate(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private ProductCombo find(Long id) {
        return comboRepository.findWithCategoryById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy combo.", null));
    }

    /** Combo chỉ gắn với danh mục cấp 1 (D-23) — trang combo lọc theo nhóm lớn, không cần đi sâu. */
    private Category findCategory(Long id) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> fieldError("categoryId", "Danh mục không tồn tại."));
        if (category.getParentId() != null) {
            throw fieldError("categoryId", "Combo chỉ gắn với danh mục cấp 1.");
        }
        return category;
    }

    private String requireFreeSlug(String slug, Long excludeId) {
        boolean taken = excludeId == null ? comboRepository.existsBySlug(slug) : comboRepository.existsBySlugAndIdNot(slug, excludeId);
        if (taken) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS);
        }
        return slug;
    }

    private String uniqueSlugFrom(String name) {
        String base = SlugUtil.toSlug(name);
        if (base.isEmpty()) {
            base = DEFAULT_SLUG;
        }
        String candidate = base;
        for (int n = 2; comboRepository.existsBySlug(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    private Map<Long, String> categoryNames(Collection<String> paths) {
        Set<Long> ids = new HashSet<>();
        paths.forEach(p -> ids.addAll(pathIds(p)));
        return ids.isEmpty() ? Map.of()
                : categoryRepository.findAllById(ids).stream().collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private static List<String> pathNames(String path, Map<Long, String> names) {
        return pathIds(path).stream().map(names::get).filter(n -> n != null).toList();
    }

    private static List<Long> pathIds(String path) {
        return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
    }

    private static String cleanDescription(String html) {
        String clean = HtmlSanitizer.sanitize(html).trim();
        return clean.isEmpty() ? null : clean;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static BusinessException fieldError(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                Map.of("fieldErrors", Map.of(field, message)));
    }

    // --- Specification ---------------------------------------------------------------------------

    private static Specification<ProductCombo> withCategoryFetched() {
        return (root, query, cb) -> {
            if (!isCountQuery(query)) {
                root.fetch("category");
            }
            return null;
        };
    }

    /** Tìm theo tên hoặc slug; gõ không dấu được: mỗi từ phải khớp trọn từ trong slug. */
    private static Specification<ProductCombo> matchesKeyword(String q) {
        if (isBlank(q)) {
            return null;
        }
        String pattern = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        List<String> words = Arrays.stream(SlugUtil.toSlug(q).split("-")).filter(w -> !w.isEmpty()).toList();
        return (root, query, cb) -> {
            var exact = cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'), cb.like(root.get("slug"), pattern, '\\'));
            if (words.isEmpty()) {
                return exact;
            }
            var dashedSlug = cb.concat(cb.concat("-", root.<String>get("slug")), "-");
            var allWords = cb.and(words.stream()
                    .map(w -> cb.like(dashedSlug, "%-" + escapeLike(w) + "-%", '\\'))
                    .toArray(jakarta.persistence.criteria.Predicate[]::new));
            return cb.or(exact, allWords);
        };
    }

    private Specification<ProductCombo> inCategoryTree(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        String path = categoryRepository.findById(categoryId).map(Category::getPath).orElse(null);
        if (path == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> cb.like(root.get("category").get("path"), escapeLike(path) + "%", '\\');
    }
}
