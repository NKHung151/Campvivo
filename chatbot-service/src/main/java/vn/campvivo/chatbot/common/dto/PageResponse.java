package vn.campvivo.chatbot.common.dto;

import java.util.List;

/**
 * Response danh sách có phân trang — docs/02-ky-thuat/api.md mục 1 (trang đánh số từ 1).
 */
public record PageResponse<T>(boolean success, List<T> data, Pagination pagination) {

    public record Pagination(int currentPage, int limit, int totalPages, long totalItems) {}

    public static <T> PageResponse<T> of(List<T> data, int currentPage, int limit, long totalItems) {
        int totalPages = limit <= 0 ? 0 : (int) Math.ceil((double) totalItems / limit);
        return new PageResponse<>(true, data, new Pagination(currentPage, limit, totalPages, totalItems));
    }
}
