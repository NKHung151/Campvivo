package vn.campvivo.auth;

import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Luật mật khẩu dùng chung cho đăng ký và đặt lại mật khẩu: 8–64 ký tự, có ít nhất 1 chữ và 1 số (các hằng dưới đây
 * dùng cho annotation của DTO), và tối đa 72 byte UTF-8 vì BCrypt chỉ dùng 72 byte đầu (kiểm bằng {@link #requireBcryptSafe}).
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    public static final String PATTERN = "^(?=.*\\p{L})(?=.*\\d).*$";
    public static final String LENGTH_MESSAGE = "Mật khẩu phải từ 8 đến 64 ký tự";
    public static final String PATTERN_MESSAGE = "Mật khẩu phải có ít nhất 1 chữ và 1 số";

    /** BCrypt chỉ dùng 72 byte đầu của mật khẩu; chặn mật khẩu dài hơn thay vì cắt âm thầm (ký tự tiếng Việt chiếm 2–3 byte). */
    private static final int BCRYPT_MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    /** @throws BusinessException VALIDATION_ERROR (fieldErrors theo `field`) nếu mật khẩu vượt 72 byte UTF-8. */
    public static void requireBcryptSafe(String password, String field) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                    Map.of("fieldErrors", Map.of(field, "Mật khẩu quá dài")));
        }
    }
}
