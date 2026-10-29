package vn.campvivo.chatbot.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Mã lỗi của chatbot-service — PHẢI khớp docs/02-ky-thuat/api.md mục 2 (phần chung + nhóm "chatbot").
 * Tên và câu mặc định của mã chung giống hệt backend để frontend xử lý một kiểu.
 */
public enum ErrorCode {

    // --- Chung (giống backend) ---
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu yêu cầu."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống đang gặp sự cố. Vui lòng thử lại sau."),

    // --- chatbot (api.md 4.12) ---
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "Chỉ nhận tài liệu PDF, Word (.docx), .txt hoặc .md."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "Tài liệu vượt quá dung lượng cho phép."),
    DOCUMENT_DUPLICATE(HttpStatus.CONFLICT, "Tài liệu này đã được tải lên trước đó."),
    DOCUMENT_PROCESSING(HttpStatus.CONFLICT, "Tài liệu đang được xử lý. Vui lòng đợi xử lý xong."),
    CHAT_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Bạn gửi tin nhắn quá nhanh. Vui lòng đợi một chút rồi thử lại."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Trợ lý ảo đang tạm thời gián đoạn. Vui lòng thử lại sau.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
