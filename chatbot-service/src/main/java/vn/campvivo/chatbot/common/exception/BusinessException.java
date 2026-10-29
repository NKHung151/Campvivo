package vn.campvivo.chatbot.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception nghiệp vụ — errorCode lấy từ {@link ErrorCode} (khớp api.md mục 2).
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

    public static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getStatus() { return status; }
    public Object getDetails() { return details; }
}
