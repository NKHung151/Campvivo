package vn.campvivo.combo.admin;

import org.springframework.data.domain.Sort;

/** Tham số GET /api/admin/combos. Giá trị enum viết hoa trên query (sai → 400). */
public record AdminComboQuery(
        String q,
        Long categoryId,
        Boolean isActive,
        SortOption sort,
        int page,
        int limit
) {
    public enum SortOption {
        UPDATED_DESC(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))),
        CREATED_DESC(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        NAME_ASC(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))),
        PRICE_ASC(Sort.by(Sort.Order.asc("comboPrice"), Sort.Order.asc("id"))),
        PRICE_DESC(Sort.by(Sort.Order.desc("comboPrice"), Sort.Order.asc("id"))),
        STOCK_ASC(Sort.by(Sort.Order.asc("stockQuantity"), Sort.Order.asc("id")));

        private final Sort sort;

        SortOption(Sort sort) {
            this.sort = sort;
        }

        public Sort toSort() {
            return sort;
        }
    }
}
