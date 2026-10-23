package vn.campvivo.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import vn.campvivo.common.dto.ApiErrorResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Xử lý tập trung mọi lỗi -> response chuẩn docs/02-ky-thuat/api.md mục 1. Không bao giờ trả stack trace.
 * <ul>
 *   <li>BusinessException -> errorCode + status của nó (kèm details nếu có)</li>
 *   <li>Validate DTO / tham số -> 400 VALIDATION_ERROR, details.fieldErrors</li>
 *   <li>AccessDenied -> 403 FORBIDDEN; Authentication -> 401 UNAUTHORIZED</li>
 *   <li>Lỗi MVC có sẵn (JSON hỏng, sai kiểu tham số, 404 route, 405...) -> 404 NOT_FOUND hoặc 400 VALIDATION_ERROR;
 *       lỗi 5xx của framework -> 500 INTERNAL_ERROR</li>
 *   <li>Còn lại -> 500 INTERNAL_ERROR, chỉ ghi log phía server</li>
 * </ul>
 * Exception nghiệp vụ mới nên kế thừa BusinessException để không phải thêm handler ở đây.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getErrorCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fieldErrors.putIfAbsent(field, v.getMessage());
        });
        return validationError(fieldErrors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiErrorResponse.of(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.of(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Lỗi không lường trước", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    /** @Valid trên @RequestBody / @ModelAttribute. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fieldErrors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> fieldErrors.putIfAbsent(e.getObjectName(), e.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                        Map.of("fieldErrors", fieldErrors)));
    }

    /** Mọi lỗi MVC có sẵn khác: ép về format chuẩn, không để lộ ProblemDetail mặc định. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code;
        if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
            code = ErrorCode.NOT_FOUND;
        } else if (statusCode.is5xxServerError()) {
            log.error("Lỗi MVC {}", statusCode.value(), ex);
            code = ErrorCode.INTERNAL_ERROR;
        } else {
            code = ErrorCode.VALIDATION_ERROR;
        }
        return ResponseEntity.status(code.getStatus()).body(ApiErrorResponse.of(code));
    }

    private ResponseEntity<ApiErrorResponse> validationError(Map<String, String> fieldErrors) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                        Map.of("fieldErrors", fieldErrors)));
    }
}
