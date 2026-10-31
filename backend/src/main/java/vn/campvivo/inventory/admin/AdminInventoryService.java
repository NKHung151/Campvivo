package vn.campvivo.inventory.admin;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductImage;
import vn.campvivo.catalog.ProductImageRepository;
import vn.campvivo.catalog.ProductRepository;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.combo.ProductComboRepository;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.util.SlugUtil;
import vn.campvivo.inventory.InventoryLog;
import vn.campvivo.inventory.InventoryLogRepository;
import vn.campvivo.inventory.InventoryLogService;
import vn.campvivo.inventory.InventoryReason;
import vn.campvivo.inventory.admin.AdminInventoryResponses.Item;
import vn.campvivo.inventory.admin.AdminInventoryResponses.ItemType;
import vn.campvivo.inventory.admin.AdminInventoryResponses.LogEntry;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static vn.campvivo.catalog.ProductSpecifications.available;
import static vn.campvivo.catalog.ProductSpecifications.escapeLike;
import static vn.campvivo.catalog.ProductSpecifications.isCountQuery;
import static vn.campvivo.catalog.ProductSpecifications.matchesKeyword;

/**
 * Trang Tồn kho (FR-08, BR-8) — ADMIN và EMPLOYEE.
 * <ul>
 *   <li>Danh sách tồn kho sản phẩm / combo, ít hàng khả dụng nhất lên đầu; lọc "sắp hết" (khả dụng ≤ ngưỡng).</li>
 *   <li>Điều chỉnh tay: sản phẩm nhập/xuất/điều chỉnh; combo CHỈ được giảm (hỏng/mất) — muốn tăng phải đóng gói để trừ
 *       hàng lẻ (D-27). Không được làm tồn kho thấp hơn số đang giữ chỗ. Mỗi lần ghi đúng 1 dòng nhật ký.</li>
 *   <li>Nhật ký: lọc theo mặt hàng, lý do, khoảng ngày (giờ Việt Nam), mới nhất trước.</li>
 * </ul>
 */
@Service
public class AdminInventoryService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductComboRepository comboRepository;
    private final InventoryLogRepository logRepository;
    private final InventoryLogService logService;
    private final Clock clock;
    private final int lowStockThreshold;

    public AdminInventoryService(ProductRepository productRepository, ProductImageRepository productImageRepository,
                                 ProductComboRepository comboRepository, InventoryLogRepository logRepository,
                                 InventoryLogService logService, Clock clock, AppProperties appProperties) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.comboRepository = comboRepository;
        this.logRepository = logRepository;
        this.logService = logService;
        this.clock = clock;
        this.lowStockThreshold = appProperties.inventory().lowStockThreshold();
    }

    // --- Danh sách tồn kho -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<Item> list(ItemType type, String q, boolean lowStock, int page, int limit) {
        PageRequest pageable = PageRequest.of(page - 1, limit);
        if (type == ItemType.COMBO) {
            Page<ProductCombo> result = comboRepository.findAll(comboSpec(q, lowStock), pageable);
            Map<Long, String> images = comboRepository.findCoverImageUrls(result.getContent().stream().map(ProductCombo::getId).toList());
            return PageResponse.of(result.map(c -> Item.of(c, images.get(c.getId()), lowStockThreshold)));
        }
        Page<Product> result = productRepository.findAll(productSpec(q, lowStock), pageable);
        List<Long> ids = result.getContent().stream().map(Product::getId).toList();
        Map<Long, String> images = ids.isEmpty() ? Map.of()
                : productImageRepository.findPrimaryByProductIds(ids).stream()
                        .collect(Collectors.toMap(i -> i.getProduct().getId(), ProductImage::getImageUrl, (a, b) -> a));
        return PageResponse.of(result.map(p -> Item.of(p, images.get(p.getId()), lowStockThreshold)));
    }

    private Specification<Product> productSpec(String q, boolean lowStock) {
        return Specification.where(matchesKeyword(q))
                .and(lowStock ? (root, query, cb) -> cb.le(available(root, cb), lowStockThreshold) : null)
                .and((root, query, cb) -> {
                    if (!isCountQuery(query)) {
                        query.orderBy(cb.asc(available(root, cb)), cb.asc(root.get("id")));
                    }
                    return null;
                });
    }

    /** Combo: tìm theo tên hoặc slug (gõ không dấu được), ít bộ khả dụng nhất lên đầu. */
    private Specification<ProductCombo> comboSpec(String q, boolean lowStock) {
        Specification<ProductCombo> keyword = null;
        if (q != null && !q.isBlank()) {
            String text = "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
            String slug = "%" + escapeLike(SlugUtil.toSlug(q)) + "%";
            keyword = (root, query, cb) -> cb.or(cb.like(cb.lower(root.get("name")), text, '\\'), cb.like(root.get("slug"), slug, '\\'));
        }
        return Specification.where(keyword)
                .and(lowStock ? (root, query, cb) -> cb.le(cb.diff(root.<Integer>get("stockQuantity"),
                        root.<Integer>get("reservedQuantity")), lowStockThreshold) : null)
                .and((root, query, cb) -> {
                    if (!isCountQuery(query)) {
                        query.orderBy(cb.asc(cb.diff(root.<Integer>get("stockQuantity"), root.<Integer>get("reservedQuantity"))),
                                cb.asc(root.get("id")));
                    }
                    return null;
                });
    }

    // --- Điều chỉnh tay ----------------------------------------------------------------------------

    @Transactional
    public LogEntry adjust(AdjustmentRequest req, long userId) {
        if ((req.productId() == null) == (req.comboId() == null)) {
            throw fieldError("productId", "Chọn đúng một sản phẩm hoặc một combo.");
        }
        int change = req.changeQuantity();
        switch (req.reason()) {
            case IMPORT -> {
                if (change <= 0) throw fieldError("changeQuantity", "Nhập kho phải là số dương.");
            }
            case EXPORT -> {
                if (change >= 0) throw fieldError("changeQuantity", "Xuất kho phải là số âm.");
            }
            case ADJUST -> {
                if (change == 0) throw fieldError("changeQuantity", "Số lượng điều chỉnh phải khác 0.");
            }
            case RETURN -> throw fieldError("reason", "Lý do trả hàng chỉ được ghi khi hoàn tất yêu cầu trả hàng.");
        }
        return req.productId() != null ? adjustProduct(req.productId(), change, req, userId)
                : writeOffCombo(req.comboId(), change, req, userId);
    }

    private LogEntry adjustProduct(long id, int change, AdjustmentRequest req, long userId) {
        Product p = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy sản phẩm.", null));
        if (p.getStockQuantity() + change < p.getReservedQuantity()) {
            throw belowReserved(p.getStockQuantity() + change, p.getReservedQuantity());
        }
        p.adjustStock(change, OffsetDateTime.now(clock));
        return LogEntry.from(logService.record(p, userId, change, req.reason(), req.note()));
    }

    /** Combo chỉ được giảm tay (D-27): tăng bộ combo phải qua đóng gói để trừ hàng lẻ. */
    private LogEntry writeOffCombo(long id, int change, AdjustmentRequest req, long userId) {
        if (change > 0) {
            throw new BusinessException(ErrorCode.INVALID_STOCK_ADJUSTMENT,
                    "Combo chỉ được xuất/giảm tay. Muốn thêm bộ combo hãy dùng \"Đóng gói\" để trừ hàng lẻ tương ứng.", null);
        }
        ProductCombo c = comboRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy combo.", null));
        if (c.getStockQuantity() + change < c.getReservedQuantity()) {
            throw belowReserved(c.getStockQuantity() + change, c.getReservedQuantity());
        }
        c.writeOff(-change, OffsetDateTime.now(clock));
        return LogEntry.from(logService.record(c, userId, change, req.reason(), req.note()));
    }

    private static BusinessException belowReserved(int after, int reserved) {
        return new BusinessException(ErrorCode.INVALID_STOCK_ADJUSTMENT, "Tồn kho sau điều chỉnh (" + Math.max(after, 0)
                + ") thấp hơn số đang giữ chỗ cho đơn hàng (" + reserved + ").", null);
    }

    // --- Nhật ký -----------------------------------------------------------------------------------

    /** `from`/`to`: ngày theo giờ Việt Nam, gồm cả hai đầu. */
    @Transactional(readOnly = true)
    public PageResponse<LogEntry> logs(Long productId, Long comboId, ItemType type, InventoryReason reason,
                                       LocalDate from, LocalDate to, int page, int limit) {
        Specification<InventoryLog> spec = Specification.<InventoryLog>where((root, query, cb) -> {
                    if (!isCountQuery(query)) {
                        root.fetch("product", JoinType.LEFT);
                        root.fetch("combo", JoinType.LEFT);
                        root.fetch("user", JoinType.INNER);
                        query.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
                    }
                    return null;
                })
                .and(productId == null ? null : (root, query, cb) -> cb.equal(root.get("product").get("id"), productId))
                .and(comboId == null ? null : (root, query, cb) -> cb.equal(root.get("combo").get("id"), comboId))
                .and(type == null ? null : (root, query, cb) -> type == ItemType.PRODUCT
                        ? cb.isNotNull(root.get("product")) : cb.isNotNull(root.get("combo")))
                .and(reason == null ? null : (root, query, cb) -> cb.equal(root.get("reason"), reason))
                .and(from == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startOf(from)))
                .and(to == null ? null : (root, query, cb) -> cb.lessThan(root.get("createdAt"), startOf(to.plusDays(1))));
        Page<InventoryLog> result = logRepository.findAll(spec, PageRequest.of(page - 1, limit));
        return PageResponse.of(result.map(LogEntry::from));
    }

    private OffsetDateTime startOf(LocalDate day) {
        return day.atStartOfDay(clock.getZone()).toOffsetDateTime();
    }

    private static BusinessException fieldError(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                Map.of("fieldErrors", Map.of(field, message)));
    }
}
