package vn.campvivo.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.campvivo.common.dto.ApiErrorResponse;

/**
 * Xử lý tập trung mọi BusinessException -> response lỗi chuẩn (xem common/dto/ApiErrorResponse).
 * Mọi exception nghiệp vụ mới trong các module khác nên kế thừa BusinessException thay vì
 * tự throw RuntimeException trần, để không phải viết thêm handler ở đây.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getErrorCode(), ex.getMessage()));
    }
}
