package vn.campvivo.catalog;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import vn.campvivo.common.util.SlugUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Điều kiện lọc sản phẩm dùng chung cho danh sách admin và danh sách public. */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** Nạp sẵn danh mục khi lấy dữ liệu (không áp cho câu đếm của phân trang). */
    public static Specification<Product> withCategoryFetched() {
        return (root, query, cb) -> {
            if (!isCountQuery(query)) {
                root.fetch("category", JoinType.INNER);
            }
            return null;
        };
    }

    /** Từ khoá ngắn hơn mức này không khớp chuỗi con (tránh "áo" khớp "tháo", "ao" khớp "AONIJIE"). */
    private static final int MIN_SUBSTRING_LENGTH = 4;

    /**
     * Tìm theo tên, thương hiệu, slug. Gõ không dấu được: mỗi từ ("leu", "2", "nguoi") phải khớp trọn từ trong slug
     * hoặc trong thương hiệu. Từ khoá từ 4 ký tự còn khớp chuỗi con trong tên/slug/thương hiệu (vd. mã "22dq029").
     */
    public static Specification<Product> matchesKeyword(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String text = q.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + escapeLike(text) + "%";
        List<String> words = Arrays.stream(SlugUtil.toSlug(q).split("-")).filter(w -> !w.isEmpty()).toList();
        return (root, query, cb) -> {
            Predicate exact = text.length() >= MIN_SUBSTRING_LENGTH
                    ? cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
                            cb.like(cb.lower(root.get("brand")), pattern, '\\'),
                            cb.like(root.get("slug"), pattern, '\\'))
                    : cb.disjunction();
            if (words.isEmpty()) {
                return exact;
            }
            // Khớp trọn từ: thêm dấu phân cách hai đầu ("2" không khớp "9562", "ao" không khớp "aonijie")
            var dashedSlug = cb.concat(cb.concat("-", root.<String>get("slug")), "-");
            var spacedBrand = cb.concat(cb.concat(" ", cb.function("replace", String.class,
                    cb.lower(root.get("brand")), cb.literal("-"), cb.literal(" "))), " ");
            Predicate allWords = cb.and(words.stream()
                    .map(w -> cb.or(
                            cb.like(dashedSlug, "%-" + escapeLike(w) + "-%", '\\'),
                            cb.like(spacedBrand, "% " + escapeLike(w) + " %", '\\')))
                    .toArray(Predicate[]::new));
            return cb.or(exact, allWords);
        };
    }

    /**
     * Mức liên quan với từ khoá (nhỏ = liên quan hơn): 0 tên bắt đầu đúng từ khoá (có dấu), 1 tên chứa nguyên từ khoá,
     * 2 slug bắt đầu bằng từ khoá không dấu, 3 còn lại. Dùng để xếp kết quả tìm kiếm trước cách sắp xếp đã chọn.
     */
    public static Expression<Integer> relevanceRank(Root<Product> root, CriteriaBuilder cb, String q) {
        String text = escapeLike(q.trim().toLowerCase(Locale.ROOT));
        String slug = escapeLike(SlugUtil.toSlug(q));
        var name = cb.lower(root.<String>get("name"));
        var spacedName = cb.concat(cb.concat(" ", name), " ");
        return cb.<Integer>selectCase()
                .when(cb.like(name, text + "%", '\\'), 0)
                .when(cb.like(spacedName, "% " + text + " %", '\\'), 1)
                .when(cb.or(cb.equal(root.get("slug"), slug), cb.like(root.get("slug"), slug + "-%", '\\')), 2)
                .otherwise(3);
    }

    /** Sort của Spring Data → thứ tự Criteria (dùng khi phải tự đặt orderBy trong Specification). */
    public static List<Order> toOrders(Sort sort, Root<?> root, CriteriaBuilder cb) {
        List<Order> out = new ArrayList<>();
        sort.forEach(o -> out.add(o.isAscending() ? cb.asc(root.get(o.getProperty())) : cb.desc(root.get(o.getProperty()))));
        return out;
    }

    /** Thương hiệu khớp đúng tên, không phân biệt hoa thường. */
    public static Specification<Product> hasBrand(String brand) {
        if (brand == null || brand.isBlank()) {
            return null;
        }
        String b = brand.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("brand")), b);
    }

    /** Danh mục và mọi danh mục con: path bắt đầu bằng path của danh mục (BR-13). null = không có danh mục nào. */
    public static Specification<Product> inCategoryPath(String path) {
        if (path == null) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> cb.like(root.get("category").get("path"), escapeLike(path) + "%", '\\');
    }

    /** Số lượng khả dụng (BR-1: stock − reserved) của sản phẩm. */
    public static Expression<Integer> available(Root<Product> root, CriteriaBuilder cb) {
        return cb.diff(root.<Integer>get("stockQuantity"), root.<Integer>get("reservedQuantity"));
    }

    public static boolean isCountQuery(CriteriaQuery<?> query) {
        return query.getResultType() == Long.class || query.getResultType() == long.class;
    }

    public static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
