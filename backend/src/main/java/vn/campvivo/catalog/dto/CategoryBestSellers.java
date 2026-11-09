package vn.campvivo.catalog.dto;

import java.util.List;

/**
 * Sản phẩm bán chạy của một danh mục cấp 2 — GET /products/best-sellers (menu cửa hàng hiện thay cho danh mục cấp 3
 * đã bỏ, D-38). `items` theo thứ tự bán chạy nhất trước.
 */
public record CategoryBestSellers(Long categoryId, List<ItemSummary> items) {
}
