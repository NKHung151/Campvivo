package vn.campvivo.common.dto;

/**
 * Wrapper chuẩn cho mọi response thành công. Khớp docs/02-ky-thuat/api.md.
 * Response lỗi dùng vn.campvivo.common.dto.ApiErrorResponse (trả qua GlobalExceptionHandler).
 */
public record ApiResponse<T>(boolean success, T data) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data);
    }
}
