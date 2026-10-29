package vn.campvivo.chatbot.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.campvivo.chatbot.common.exception.ErrorCode;

/**
 * Format lỗi thống nhất với backend — docs/02-ky-thuat/api.md mục 1. `details` bỏ qua khi null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(boolean success, String errorCode, String message, Object details) {

    public static ApiErrorResponse of(String errorCode, String message, Object details) {
        return new ApiErrorResponse(false, errorCode, message, details);
    }

    public static ApiErrorResponse of(ErrorCode code, String message, Object details) {
        return of(code.name(), message, details);
    }

    public static ApiErrorResponse of(ErrorCode code) {
        return of(code, code.getMessage(), null);
    }
}
