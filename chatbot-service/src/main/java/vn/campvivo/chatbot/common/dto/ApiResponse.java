package vn.campvivo.chatbot.common.dto;

/**
 * Wrapper chuẩn cho mọi response thành công — giống backend (docs/02-ky-thuat/api.md mục 1).
 */
public record ApiResponse<T>(boolean success, T data) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data);
    }
}
