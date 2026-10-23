import { CLIENT_ERROR_CODES, isApiError } from "@/services/apiError";

/**
 * errorCode → thông báo tiếng Việt hiển thị cho người dùng (.agents/rules/frontend.md).
 * Các mã backend PHẢI khớp docs/02-ky-thuat/api.md mục 2 và `ErrorCode.java` (cùng câu chữ).
 * Thêm mã mới ở backend thì thêm ở đây.
 */
const BACKEND_MESSAGES: Record<string, string> = {
  // Chung
  VALIDATION_ERROR: "Dữ liệu gửi lên không hợp lệ.",
  UNAUTHORIZED: "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.",
  FORBIDDEN: "Bạn không có quyền thực hiện thao tác này.",
  NOT_FOUND: "Không tìm thấy dữ liệu yêu cầu.",
  SLUG_ALREADY_EXISTS: "Đường dẫn (slug) đã tồn tại. Vui lòng chọn tên khác.",
  INTERNAL_ERROR: "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.",
  // auth / user
  EMAIL_ALREADY_EXISTS: "Email này đã được đăng ký.",
  INVALID_CREDENTIALS: "Email hoặc mật khẩu không đúng.",
  EMAIL_NOT_VERIFIED: "Email chưa được xác thực. Vui lòng nhập mã xác thực đã gửi tới email của bạn.",
  ACCOUNT_DISABLED: "Tài khoản của bạn đã bị khoá. Vui lòng liên hệ cửa hàng.",
  INVALID_OTP: "Mã xác thực không đúng.",
  OTP_EXPIRED: "Mã xác thực đã hết hạn. Vui lòng yêu cầu gửi lại mã.",
  OTP_TOO_MANY_ATTEMPTS: "Bạn đã nhập sai mã quá số lần cho phép. Vui lòng yêu cầu gửi lại mã.",
  OTP_RESEND_TOO_SOON: "Vui lòng đợi một lúc trước khi yêu cầu gửi lại mã.",
  INVALID_CURRENT_PASSWORD: "Mật khẩu hiện tại không đúng.",
  CANNOT_MODIFY_SELF: "Bạn không thể tự khoá hoặc thay đổi quyền của chính mình.",
  EMAIL_SEND_FAILED: "Không gửi được email. Vui lòng thử lại sau.",
  // catalog / inventory
  PARENT_CATEGORY_NOT_FOUND: "Không tìm thấy danh mục cha.",
  CIRCULAR_CATEGORY_DEPENDENCY: "Không thể chọn danh mục này làm danh mục cha vì sẽ tạo vòng lặp.",
  CATEGORY_NOT_EMPTY: "Không thể xoá danh mục còn danh mục con hoặc sản phẩm.",
  INVALID_STOCK_ADJUSTMENT: "Số lượng điều chỉnh không hợp lệ.",
  // cart / order / payment
  PRODUCT_INACTIVE: "Sản phẩm này đã ngừng kinh doanh.",
  OUT_OF_STOCK: "Sản phẩm không đủ số lượng khả dụng.",
  CART_EMPTY: "Giỏ hàng đang trống.",
  PRICE_CHANGED: "Giá đã thay đổi. Vui lòng kiểm tra lại đơn hàng.",
  INVALID_VOUCHER: "Mã giảm giá không tồn tại hoặc đã bị tắt.",
  VOUCHER_EXPIRED: "Mã giảm giá không còn trong thời hạn sử dụng.",
  VOUCHER_MIN_ORDER_NOT_MET: "Đơn hàng chưa đạt giá trị tối thiểu để dùng mã giảm giá.",
  VOUCHER_EXHAUSTED: "Mã giảm giá đã hết lượt sử dụng.",
  VOUCHER_ALREADY_USED: "Bạn đã sử dụng mã giảm giá này.",
  VOUCHER_IN_USE: "Không thể xoá mã giảm giá đã có đơn hàng sử dụng. Hãy tắt mã thay vì xoá.",
  ORDER_NOT_CANCELLABLE: "Chỉ có thể huỷ đơn hàng khi đơn đang chờ xử lý.",
  INVALID_STATUS_TRANSITION: "Không thể chuyển sang trạng thái này từ trạng thái hiện tại.",
  PAYMENT_GATEWAY_ERROR: "Không tạo được liên kết thanh toán. Vui lòng thử lại sau.",
  // return / review
  RETURN_NOT_ALLOWED: "Chỉ có thể yêu cầu trả hàng với đơn đã hoàn tất.",
  RETURN_WINDOW_EXPIRED: "Đã quá thời hạn cho phép trả hàng.",
  RETURN_ALREADY_EXISTS: "Đơn hàng này đã có yêu cầu trả hàng.",
  NOT_PURCHASED: "Bạn chỉ có thể đánh giá sản phẩm đã mua.",
  REVIEW_ALREADY_EXISTS: "Bạn đã đánh giá sản phẩm này rồi.",
};

/** Mã do frontend tự sinh (services/apiError.ts), không có ở backend. */
const CLIENT_MESSAGES: Record<string, string> = {
  [CLIENT_ERROR_CODES.NETWORK_ERROR]: "Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.",
  [CLIENT_ERROR_CODES.UNKNOWN_ERROR]: "Có lỗi xảy ra. Vui lòng thử lại.",
};

export const ERROR_MESSAGES: Readonly<Record<string, string>> = { ...BACKEND_MESSAGES, ...CLIENT_MESSAGES };

export const DEFAULT_ERROR_MESSAGE = "Có lỗi xảy ra. Vui lòng thử lại.";

/**
 * Thông báo hiển thị cho người dùng. Thứ tự: bảng map theo errorCode → `message` do server trả
 * (backend luôn gửi tiếng Việt) → câu mặc định. Không bao giờ in lỗi kỹ thuật thô (stack, tên class).
 * Cần câu cụ thể của server (vd. OUT_OF_STOCK "Chỉ còn 3 sản phẩm") thì dùng `error.message` / `error.details`.
 */
export function getErrorMessage(error: unknown, fallback: string = DEFAULT_ERROR_MESSAGE): string {
  if (!isApiError(error)) return fallback;
  return ERROR_MESSAGES[error.errorCode] ?? (error.message.trim() !== "" ? error.message : fallback);
}
