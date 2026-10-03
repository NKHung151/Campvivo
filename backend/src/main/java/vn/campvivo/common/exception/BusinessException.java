package vn.campvivo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base cho mọi exception nghiệp vụ. errorCode PHẢI khớp với mã đã khai báo trong
 * docs/02-ky-thuat/api.md để frontend xử lý được chính xác (vd. hiển thị đúng thông báo).
 */
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    public BusinessException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getStatus() { return status; }
}
