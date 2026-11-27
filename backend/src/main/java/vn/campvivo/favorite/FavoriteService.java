package vn.campvivo.favorite;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.article.ArticleQueryService;
import vn.campvivo.catalog.ProductService;
import vn.campvivo.combo.ComboService;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.favorite.dto.FavoriteRequest;
import vn.campvivo.favorite.dto.FavoriteResponses;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Yêu thích sản phẩm / combo / bài viết của khách (FR-20, D-06, D-47). Đọc mặt hàng qua Service của `catalog`, `combo`,
 * `article`; ghi bằng JDBC để `INSERT … ON CONFLICT DO NOTHING` làm thêm idempotent kể cả khi hai yêu cầu tới cùng lúc.
 */
@Service
public class FavoriteService {

    /** Mặt hàng được nhắm tới: loại + id. */
    public record Target(FavoriteType type, Long itemId) {}

    /** Kết quả thêm: `created = false` khi đã thích từ trước (trả id cũ, HTTP 200). */
    public record AddResult(Long id, boolean created) {}

    private final FavoriteRepository repository;
    private final JdbcTemplate jdbc;
    private final ProductService productService;
    private final ComboService comboService;
    private final ArticleQueryService articleQueryService;

    public FavoriteService(FavoriteRepository repository, JdbcTemplate jdbc, ProductService productService,
                           ComboService comboService, ArticleQueryService articleQueryService) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.productService = productService;
        this.comboService = comboService;
        this.articleQueryService = articleQueryService;
    }

    /** Đúng một trong ba id, không thì `VALIDATION_ERROR` (dùng cho cả body POST và query DELETE). */
    public static Target target(Long productId, Long comboId, Long articleId) {
        int given = (productId != null ? 1 : 0) + (comboId != null ? 1 : 0) + (articleId != null ? 1 : 0);
        if (given != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    Map.of("fieldErrors", Map.of("productId", "Chọn đúng một sản phẩm, combo hoặc bài viết")));
        }
        return productId != null ? new Target(FavoriteType.PRODUCT, productId)
                : comboId != null ? new Target(FavoriteType.COMBO, comboId)
                : new Target(FavoriteType.ARTICLE, articleId);
    }

    /** Thêm; mặt hàng không tồn tại / đang ẩn → `NOT_FOUND`; đã có → id cũ. */
    @Transactional
    public AddResult add(Long userId, FavoriteRequest request) {
        Target t = target(request.productId(), request.comboId(), request.articleId());
        if (!visible(t)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String col = t.type().column();
        List<Long> inserted = jdbc.queryForList("INSERT INTO favorites (user_id, " + col + ") VALUES (?, ?) "
                + "ON CONFLICT (user_id, " + col + ") WHERE " + col + " IS NOT NULL DO NOTHING RETURNING id",
                Long.class, userId, t.itemId());
        if (!inserted.isEmpty()) {
            return new AddResult(inserted.get(0), true);
        }
        // Đã có (hoặc yêu cầu song song vừa commit trước): câu lệnh mới đọc thấy dòng đó
        Long existing = jdbc.queryForObject("SELECT id FROM favorites WHERE user_id = ? AND " + col + " = ?",
                Long.class, userId, t.itemId());
        return new AddResult(existing, false);
    }

    /** Bỏ theo id; không phải của mình / không tồn tại → `NOT_FOUND`. */
    @Transactional
    public void remove(Long userId, Long favoriteId) {
        Favorite favorite = repository.findByIdAndUserId(favoriteId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        repository.delete(favorite);
    }

    /** Bỏ theo mặt hàng (trang chi tiết) — idempotent. */
    @Transactional
    public void remove(Long userId, Target t) {
        jdbc.update("DELETE FROM favorites WHERE user_id = ? AND " + t.type().column() + " = ?", userId, t.itemId());
    }

    /** Danh sách của khách, mới thích trước; chỉ mặt hàng đang hiển thị. `type` null = cả 3 loại. */
    @Transactional(readOnly = true)
    public PageResponse<FavoriteResponses.Item> list(Long userId, FavoriteType type, int page, int limit) {
        Page<Favorite> result = repository.findVisible(userId, type == null ? "ALL" : type.name(),
                PageRequest.of(page - 1, limit));
        List<Favorite> rows = result.getContent();
        Map<Long, ?> products = productService.activeSummaries(itemIds(rows, FavoriteType.PRODUCT));
        Map<Long, ?> combos = comboService.activeSummaries(itemIds(rows, FavoriteType.COMBO));
        Map<Long, ?> articles = articleQueryService.publishedSummaries(itemIds(rows, FavoriteType.ARTICLE));
        // Mặt hàng có thể vừa bị ẩn giữa hai câu truy vấn → bỏ dòng thiếu item (hiếm; pagination giữ số của câu đếm)
        List<FavoriteResponses.Item> items = rows.stream().map(f -> {
            Object item = switch (f.getType()) {
                case PRODUCT -> products.get(f.getItemId());
                case COMBO -> combos.get(f.getItemId());
                case ARTICLE -> articles.get(f.getItemId());
            };
            return item == null ? null : new FavoriteResponses.Item(f.getId(), f.getType(), item, f.getCreatedAt());
        }).filter(Objects::nonNull).toList();
        return PageResponse.of(items, page, limit, result.getTotalElements());
    }

    /** Mọi mục của khách (kể cả mặt hàng đang ẩn) — tô tim ở client (D-47). */
    @Transactional(readOnly = true)
    public FavoriteResponses.Ids ids(Long userId) {
        List<Favorite> all = repository.findByUserId(userId);
        return new FavoriteResponses.Ids(sortedIds(all, FavoriteType.PRODUCT), sortedIds(all, FavoriteType.COMBO),
                sortedIds(all, FavoriteType.ARTICLE));
    }

    /**
     * Khách ĐANG HOẠT ĐỘNG yêu thích từng mặt hàng (người nhận `FAVORITE_ON_SALE` / `FAVORITE_BACK_IN_STOCK` — D-47):
     * itemId → userId tăng dần. Mặt hàng không ai thích thì không có khoá.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<Long>> customersFavoriting(FavoriteType type, Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        String col = type.column();
        Long[] ids = itemIds.stream().distinct().toArray(Long[]::new);
        Map<Long, List<Long>> out = new LinkedHashMap<>();
        jdbc.query("SELECT f." + col + ", f.user_id FROM favorites f JOIN users u ON u.id = f.user_id "
                        + "WHERE f." + col + " = ANY (?) AND u.is_active AND u.role = 'CUSTOMER' ORDER BY f." + col
                        + ", f.user_id",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("bigint", ids)),
                rs -> {
                    out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2));
                });
        return out;
    }

    // ------------------------------------------------------------------

    private boolean visible(Target t) {
        return switch (t.type()) {
            case PRODUCT -> productService.isActive(t.itemId());
            case COMBO -> comboService.isActive(t.itemId());
            case ARTICLE -> articleQueryService.isPublished(t.itemId());
        };
    }

    private static List<Long> itemIds(List<Favorite> rows, FavoriteType type) {
        return rows.stream().filter(f -> f.getType() == type).map(Favorite::getItemId).toList();
    }

    private static List<Long> sortedIds(List<Favorite> rows, FavoriteType type) {
        return rows.stream().filter(f -> f.getType() == type).map(Favorite::getItemId)
                .sorted(Comparator.naturalOrder()).toList();
    }
}
