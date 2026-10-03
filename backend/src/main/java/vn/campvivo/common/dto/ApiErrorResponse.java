package vn.campvivo.common.dto;

/**
 * Format lỗi thống nhất toàn hệ thống — xem docs/02-ky-thuat/api.md.
 * Ví dụ: { "success": false, "errorCode": "OUT_OF_STOCK", "message": "..." }
 */
public record ApiErrorResponse(boolean success, String errorCode, String message) {
    public static ApiErrorResponse of(String errorCode, String message) {
        return new ApiErrorResponse(false, errorCode, message);
    }
}
