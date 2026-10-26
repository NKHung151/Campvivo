package vn.campvivo.catalog;

import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tham số GET /products (public). `category` là slug (gồm danh mục con); `slugs` lấy đúng các sản phẩm
 * theo slug (dùng cho khối sản phẩm chọn sẵn ở trang chủ, đã xem, so sánh).
 */
public record ProductQuery(
        String category,
        String brand,
        String q,
        List<String> slugs,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        SortBy sortBy,
        int page,
        int limit
) {
    /** Giá trị trên query viết thường: ?sortBy=price_asc (sai giá trị → 400). */
    public enum SortBy {
        newest(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        price_asc(Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"))),
        price_desc(Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id"))),
        name_asc(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));

        private final Sort sort;

        SortBy(Sort sort) {
            this.sort = sort;
        }

        public Sort toSort() {
            return sort;
        }
    }
}
