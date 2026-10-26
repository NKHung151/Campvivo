package vn.campvivo.catalog.admin;

import org.springframework.data.domain.Sort;

/**
 * Tham số GET /api/admin/products — docs/02-ky-thuat/api.md mục 4.10.
 * Giá trị enum viết hoa trên query: ?stock=OUT_OF_STOCK&sort=PRICE_ASC (sai giá trị → 400 VALIDATION_ERROR).
 */
public record AdminProductQuery(
        String q,
        Long categoryId,
        String brand,
        Boolean isActive,
        StockFilter stock,
        SortOption sort,
        int page,
        int limit
) {

    /** Theo tồn kho khả dụng (stock − reserved). Sắp hết = 1..app.inventory.low-stock-threshold. */
    public enum StockFilter { IN_STOCK, LOW_STOCK, OUT_OF_STOCK }

    public enum SortOption {
        UPDATED_DESC(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))),
        CREATED_DESC(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        NAME_ASC(Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))),
        PRICE_ASC(Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"))),
        PRICE_DESC(Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id"))),
        /* Theo tổng khả dụng của biến thể — sắp xếp bằng subquery trong AdminProductService. */
        STOCK_ASC(Sort.unsorted());

        private final Sort sort;

        SortOption(Sort sort) {
            this.sort = sort;
        }

        public Sort toSort() {
            return sort;
        }
    }
}
