package vn.campvivo.promotion.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductRepository;
import vn.campvivo.catalog.ProductService;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.util.HtmlSanitizer;
import vn.campvivo.common.util.SlugUtil;
import vn.campvivo.promotion.CampaignStatus;
import vn.campvivo.promotion.DiscountCampaign;
import vn.campvivo.promotion.DiscountCampaignRepository;
import vn.campvivo.promotion.DiscountType;
import vn.campvivo.promotion.admin.AdminCampaignResponses.Detail;
import vn.campvivo.promotion.admin.AdminCampaignResponses.Overlap;
import vn.campvivo.promotion.admin.AdminCampaignResponses.Summary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static vn.campvivo.catalog.ProductSpecifications.escapeLike;

/**
 * Quản lý chiến dịch giảm giá — FR-17, BR-6. Chỉ gán sản phẩm (không combo). Sản phẩm thuộc nhiều chiến dịch chồng thời
 * gian vẫn được lưu (giá bán = mức thấp nhất); `overlaps` chỉ để admin thấy cảnh báo. Xoá luôn được: đơn cũ đã lưu giá.
 */
@Service
public class AdminCampaignService {

    private static final String DEFAULT_SLUG = "khuyen-mai";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final DiscountCampaignRepository campaignRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final Clock clock;

    public AdminCampaignService(DiscountCampaignRepository campaignRepository, ProductRepository productRepository,
                                ProductService productService, Clock clock) {
        this.campaignRepository = campaignRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.clock = clock;
    }

    /** Lọc theo tên/slug (chứa chuỗi, không phân biệt hoa thường) và trạng thái; mới tạo trước. */
    @Transactional(readOnly = true)
    public PageResponse<Summary> list(String q, CampaignStatus status, int page, int limit) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Specification<DiscountCampaign> spec = Specification.<DiscountCampaign>where(matches(q)).and(hasStatus(status, now));
        Page<DiscountCampaign> result = campaignRepository.findAll(spec,
                PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        Map<Long, Long> counts = productCounts(result.getContent().stream().map(DiscountCampaign::getId).toList());
        return PageResponse.of(result.map(c -> Summary.from(c, counts.getOrDefault(c.getId(), 0L), now)));
    }

    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return detail(find(id));
    }

    @Transactional
    public Detail create(CampaignRequest req) {
        validate(req);
        Set<Product> products = resolveProducts(req.productIds());
        String slug = isBlank(req.slug()) ? uniqueSlugFrom(req.name()) : requireFreeSlug(req.slug(), null);
        OffsetDateTime now = OffsetDateTime.now(clock);
        DiscountCampaign c = new DiscountCampaign(now);
        apply(c, req, slug, now);
        c.replaceProducts(products);
        return detail(campaignRepository.save(c));
    }

    @Transactional
    public Detail update(Long id, CampaignRequest req) {
        validate(req);
        DiscountCampaign c = find(id);
        Set<Product> products = resolveProducts(req.productIds());
        String slug = isBlank(req.slug()) ? c.getSlug() : requireFreeSlug(req.slug(), id);
        OffsetDateTime now = OffsetDateTime.now(clock);
        apply(c, req, slug, now);
        c.replaceProducts(products);
        return detail(c);
    }

    @Transactional
    public void delete(Long id) {
        campaignRepository.delete(find(id));
    }

    /** Sản phẩm đã thuộc chiến dịch khác đang bật, thời gian giao [startDate, endDate) — chỉ để cảnh báo. */
    @Transactional(readOnly = true)
    public List<Overlap> overlaps(List<Long> productIds, OffsetDateTime startDate, OffsetDateTime endDate, Long excludeId) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        if (!endDate.isAfter(startDate)) {
            throw fieldError("endDate", "Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
        return campaignRepository.findOverlaps(productIds, startDate, endDate, excludeId == null ? -1 : excludeId).stream()
                .map(r -> new Overlap(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), (String) r[2]))
                .toList();
    }

    // --- helpers ---------------------------------------------------------------------------------

    private Detail detail(DiscountCampaign c) {
        Map<Long, String> images = productService.primaryImageUrls(c.getProducts().stream().map(Product::getId).toList());
        return Detail.from(c, images, OffsetDateTime.now(clock));
    }

    private static void apply(DiscountCampaign c, CampaignRequest req, String slug, OffsetDateTime now) {
        c.update(req.name().trim(), slug, cleanDescription(req.description()), blankToNull(req.bannerUrl()),
                req.discountType(), req.discountValue(), req.startDate(), req.endDate(), req.isActive(), now);
    }

    /** Ràng buộc chéo (khớp CHECK của V6__promotion.sql, báo lỗi đúng ô). */
    private static void validate(CampaignRequest req) {
        if (req.discountType() == DiscountType.PERCENTAGE && req.discountValue().compareTo(HUNDRED) > 0) {
            throw fieldError("discountValue", "Giảm theo % chỉ từ 1 đến 100.");
        }
        if (!req.endDate().isAfter(req.startDate())) {
            throw fieldError("endDate", "Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
    }

    private Set<Product> resolveProducts(List<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>(ids);
        List<Product> found = productRepository.findAllById(unique);
        if (found.size() != unique.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Có sản phẩm không tồn tại trong danh sách áp dụng.", null);
        }
        return new LinkedHashSet<>(found);
    }

    private Map<Long, Long> productCounts(Collection<Long> ids) {
        Map<Long, Long> out = new HashMap<>();
        if (!ids.isEmpty()) {
            campaignRepository.countProducts(ids).forEach(r -> out.put((Long) r[0], (Long) r[1]));
        }
        return out;
    }

    private DiscountCampaign find(Long id) {
        return campaignRepository.findWithProductsById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private String requireFreeSlug(String slug, Long excludeId) {
        boolean taken = excludeId == null ? campaignRepository.existsBySlug(slug) : campaignRepository.existsBySlugAndIdNot(slug, excludeId);
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
        for (int n = 2; campaignRepository.existsBySlug(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    private static String cleanDescription(String html) {
        String clean = HtmlSanitizer.sanitize(html).trim();
        return clean.isEmpty() ? null : clean;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static BusinessException fieldError(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                Map.of("fieldErrors", Map.of(field, message)));
    }

    // --- Specification ---------------------------------------------------------------------------

    private static Specification<DiscountCampaign> matches(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(q.trim().toLowerCase()) + "%";
        return (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
                cb.like(root.get("slug"), pattern, '\\'));
    }

    /** Cùng nghĩa với {@link DiscountCampaign#statusAt}. */
    private static Specification<DiscountCampaign> hasStatus(CampaignStatus status, OffsetDateTime now) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> {
            var active = cb.isTrue(root.get("active"));
            var start = root.<OffsetDateTime>get("startDate");
            var end = root.<OffsetDateTime>get("endDate");
            return switch (status) {
                case INACTIVE -> cb.isFalse(root.get("active"));
                case UPCOMING -> cb.and(active, cb.greaterThan(start, now));
                case RUNNING -> cb.and(active, cb.lessThanOrEqualTo(start, now), cb.greaterThan(end, now));
                case ENDED -> cb.and(active, cb.lessThanOrEqualTo(end, now));
            };
        };
    }
}
