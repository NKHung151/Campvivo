package vn.campvivo.combo;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductImage;
import vn.campvivo.catalog.ProductImageRepository;
import vn.campvivo.catalog.dto.ItemSummary;
import vn.campvivo.combo.dto.ComboDetailResponse;
import vn.campvivo.combo.dto.ComboSummary;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.util.SlugUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static vn.campvivo.catalog.ProductSpecifications.escapeLike;
import static vn.campvivo.catalog.ProductSpecifications.isCountQuery;

/** Combo public — FR-07, docs/02-ky-thuat/api.md mục 4.3. Chỉ combo đang bán (is_active). */
@Service
public class ComboService {

    /** Thứ tự trên query viết thường: ?sortBy=price_asc (sai giá trị → 400). */
    public enum SortBy {
        newest(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        price_asc(Sort.by(Sort.Order.asc("comboPrice"), Sort.Order.asc("id"))),
        price_desc(Sort.by(Sort.Order.desc("comboPrice"), Sort.Order.asc("id")));

        private final Sort sort;

        SortBy(Sort sort) {
            this.sort = sort;
        }
    }

    private final ProductComboRepository comboRepository;
    private final ProductImageRepository productImageRepository;

    public ComboService(ProductComboRepository comboRepository, ProductImageRepository productImageRepository) {
        this.comboRepository = comboRepository;
        this.productImageRepository = productImageRepository;
    }

    /** `category`: slug danh mục cấp 1 (combo chỉ gắn danh mục cấp 1 — D-23). `q`: tên hoặc slug, gõ không dấu được. */
    @Transactional(readOnly = true)
    public PageResponse<ComboSummary> list(String category, String q, SortBy sortBy, int page, int limit) {
        Specification<ProductCombo> spec = Specification.<ProductCombo>where((root, query, cb) -> {
                    if (!isCountQuery(query)) {
                        root.fetch("category", JoinType.INNER);
                    }
                    return cb.isTrue(root.get("active"));
                })
                .and(isBlank(category) ? null : (root, query, cb) -> cb.equal(root.get("category").get("slug"), category.trim()))
                .and(matchesKeyword(q));
        Page<ProductCombo> result = comboRepository.findAll(spec, PageRequest.of(page - 1, limit, sortBy.sort));
        List<Long> ids = result.getContent().stream().map(ProductCombo::getId).toList();
        Map<Long, String> primary = comboRepository.findCoverImageUrls(ids);
        return PageResponse.of(result.map(c -> ComboSummary.from(c, primary.get(c.getId()))));
    }

    @Transactional(readOnly = true)
    public ComboDetailResponse getBySlug(String slug) {
        ProductCombo combo = comboRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy combo.", null));
        List<Long> productIds = combo.getItems().stream().map(i -> i.getProduct().getId()).toList();
        Map<Long, String> images = productIds.isEmpty() ? Map.of()
                : productImageRepository.findPrimaryByProductIds(productIds).stream()
                        .collect(Collectors.toMap(i -> i.getProduct().getId(), ProductImage::getImageUrl, (a, b) -> a));
        List<ComboDetailResponse.Item> items = combo.getItems().stream().map(i -> {
            Product p = i.getProduct();
            return new ComboDetailResponse.Item(ItemSummary.from(p, images.get(p.getId())), i.getQuantity());
        }).toList();
        return ComboDetailResponse.from(combo, items);
    }

    private static Specification<ProductCombo> matchesKeyword(String q) {
        if (isBlank(q)) {
            return null;
        }
        String text = escapeLike(q.trim().toLowerCase(Locale.ROOT));
        List<String> words = Arrays.stream(SlugUtil.toSlug(q).split("-")).filter(w -> !w.isEmpty()).toList();
        return (root, query, cb) -> {
            var dashedSlug = cb.concat(cb.concat("-", root.<String>get("slug")), "-");
            var allWords = cb.and(words.stream()
                    .map(w -> cb.like(dashedSlug, "%-" + escapeLike(w) + "-%", '\\'))
                    .toArray(jakarta.persistence.criteria.Predicate[]::new));
            return cb.or(cb.like(cb.lower(root.get("name")), "%" + text + "%", '\\'), allWords);
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
