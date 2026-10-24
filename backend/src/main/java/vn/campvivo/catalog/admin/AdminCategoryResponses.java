package vn.campvivo.catalog.admin;

import java.util.List;

/** DTO quản lý danh mục — docs/02-ky-thuat/api.md mục 4.11. */
public final class AdminCategoryResponses {

    private AdminCategoryResponses() {
    }

    /**
     * Nút cây danh mục. `productCount`/`comboCount`: gắn trực tiếp vào danh mục này (không gồm danh mục con) —
     * khác 0 thì không xoá được.
     */
    public record Node(
            Long id,
            String name,
            String slug,
            Long parentId,
            long productCount,
            long comboCount,
            List<Node> children
    ) {}
}
