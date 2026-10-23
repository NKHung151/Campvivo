package vn.campvivo.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Response danh sách có phân trang — docs/02-ky-thuat/api.md mục 1.
 * API đánh số trang từ 1, còn Spring Data từ 0: dùng {@link #of(Page)} để đổi.
 */
public record PageResponse<T>(boolean success, List<T> data, Pagination pagination) {

    public record Pagination(int currentPage, int limit, int totalPages, long totalItems) {}

    public static <T> PageResponse<T> of(List<T> data, int currentPage, int limit, long totalItems) {
        int totalPages = limit <= 0 ? 0 : (int) Math.ceil((double) totalItems / limit);
        return new PageResponse<>(true, data, new Pagination(currentPage, limit, totalPages, totalItems));
    }

    /** Từ Page của Spring Data (page 0-based); data đã map sang DTO. */
    public static <T> PageResponse<T> of(Page<T> page) {
        return of(page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }
}
