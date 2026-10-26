package vn.campvivo.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Danh mục mã lỗi — PHẢI khớp docs/02-ky-thuat/api.md mục 2 (mã, HTTP status). Thêm mã mới thì
 * cập nhật api.md trước. `message` là câu mặc định hiển thị được cho người dùng; chỗ cần câu cụ thể
 * (vd. "Chỉ còn 3 sản phẩm khả dụng.") thì truyền message riêng vào BusinessException.
 */
public enum ErrorCode {

    // --- Chung ---
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu yêu cầu."),
    SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT, "Đường dẫn (slug) đã tồn tại. Vui lòng chọn tên khác."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống đang gặp sự cố. Vui lòng thử lại sau."),

    // --- auth / user ---
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email này đã được đăng ký."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email chưa được xác thực. Vui lòng nhập mã xác thực đã gửi tới email của bạn."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản của bạn đã bị khoá. Vui lòng liên hệ cửa hàng."),
    INVALID_OTP(HttpStatus.BAD_REQUEST, "Mã xác thực không đúng."),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "Mã xác thực đã hết hạn. Vui lòng yêu cầu gửi lại mã."),
    OTP_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã nhập sai mã quá số lần cho phép. Vui lòng yêu cầu gửi lại mã."),
    OTP_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng đợi một lúc trước khi yêu cầu gửi lại mã."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng."),
    CANNOT_MODIFY_SELF(HttpStatus.BAD_REQUEST, "Bạn không thể tự khoá hoặc thay đổi quyền của chính mình."),
    EMAIL_SEND_FAILED(HttpStatus.BAD_GATEWAY, "Không gửi được email. Vui lòng thử lại sau."),

    // --- catalog / inventory ---
    PARENT_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục cha."),
    CIRCULAR_CATEGORY_DEPENDENCY(HttpStatus.BAD_REQUEST, "Không thể chọn danh mục này làm danh mục cha vì sẽ tạo vòng lặp."),
    CATEGORY_NOT_EMPTY(HttpStatus.CONFLICT, "Không thể xoá danh mục còn danh mục con hoặc sản phẩm."),
    INVALID_STOCK_ADJUSTMENT(HttpStatus.BAD_REQUEST, "Số lượng điều chỉnh không hợp lệ."),
    IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "Không tải được ảnh lên. Vui lòng thử lại sau."),

    // --- cart / order / payment ---
    PRODUCT_INACTIVE(HttpStatus.BAD_REQUEST, "Sản phẩm này đã ngừng kinh doanh."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "Sản phẩm không đủ số lượng khả dụng."),
    CART_EMPTY(HttpStatus.BAD_REQUEST, "Giỏ hàng đang trống."),
    PRICE_CHANGED(HttpStatus.CONFLICT, "Giá đã thay đổi. Vui lòng kiểm tra lại đơn hàng."),
    INVALID_VOUCHER(HttpStatus.BAD_REQUEST, "Mã giảm giá không tồn tại hoặc đã bị tắt."),
    VOUCHER_EXPIRED(HttpStatus.BAD_REQUEST, "Mã giảm giá không còn trong thời hạn sử dụng."),
    VOUCHER_MIN_ORDER_NOT_MET(HttpStatus.BAD_REQUEST, "Đơn hàng chưa đạt giá trị tối thiểu để dùng mã giảm giá."),
    VOUCHER_EXHAUSTED(HttpStatus.CONFLICT, "Mã giảm giá đã hết lượt sử dụng."),
    VOUCHER_ALREADY_USED(HttpStatus.CONFLICT, "Bạn đã sử dụng mã giảm giá này."),
    VOUCHER_IN_USE(HttpStatus.CONFLICT, "Không thể xoá mã giảm giá đã có đơn hàng sử dụng. Hãy tắt mã thay vì xoá."),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "Chỉ có thể huỷ đơn hàng khi đơn đang chờ xử lý."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "Không thể chuyển sang trạng thái này từ trạng thái hiện tại."),
    PAYMENT_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "Không tạo được liên kết thanh toán. Vui lòng thử lại sau."),

    // --- return / review ---
    RETURN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Chỉ có thể yêu cầu trả hàng với đơn đã hoàn tất."),
    RETURN_WINDOW_EXPIRED(HttpStatus.BAD_REQUEST, "Đã quá thời hạn cho phép trả hàng."),
    RETURN_ALREADY_EXISTS(HttpStatus.CONFLICT, "Đơn hàng này đã có yêu cầu trả hàng."),
    NOT_PURCHASED(HttpStatus.FORBIDDEN, "Bạn chỉ có thể đánh giá sản phẩm đã mua."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bạn đã đánh giá sản phẩm này rồi.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
