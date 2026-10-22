package vn.campvivo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base cho mọi exception nghiệp vụ. errorCode PHẢI nằm trong ErrorCode (khớp
 * docs/02-ky-thuat/api.md mục 2) để frontend xử lý được chính xác.
 * Dùng: throw new BusinessException(ErrorCode.OUT_OF_STOCK, "Chỉ còn 3 sản phẩm.", details).
 */
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;
    private final transient Object details;

    public BusinessException(ErrorCode code) {
        this(code, code.getMessage(), null);
    }

    public BusinessException(ErrorCode code, Object details) {
        this(code, code.getMessage(), details);
    }

    public BusinessException(ErrorCode code, String message, Object details) {
        super(message);
        this.errorCode = code.name();
        this.status = code.getStatus();
        this.details = details;
    }

    /**
     * @deprecated Dùng constructor nhận {@link ErrorCode}. Giữ lại để code khung cũ (order, catalog)
     * còn biên dịch; chủ module chuyển dần sang ErrorCode.
     */
    @Deprecated
    public BusinessException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.details = null;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getStatus() { return status; }
    public Object getDetails() { return details; }
}
