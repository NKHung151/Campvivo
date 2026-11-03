package vn.campvivo.user;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.auth.PasswordPolicy;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.user.dto.ChangePasswordRequest;
import vn.campvivo.user.dto.UpdateProfileRequest;
import vn.campvivo.user.dto.UserProfileResponse;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Hồ sơ người dùng (FR-04): xem, sửa hồ sơ, đổi mật khẩu. Không log mật khẩu. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return UserProfileResponse.from(user);
    }

    /**
     * Sửa hồ sơ của chính mình: CHỈ `fullName`, `phone`, `address` (email, role... không bao giờ đổi qua đây). Họ tên được `trim`
     * trước khi kiểm độ dài; `phone`/`address` rỗng hoặc toàn khoảng trắng nghĩa là xoá (lưu null).
     */
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        String fullName = request.fullName().trim();
        if (fullName.length() < 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                    Map.of("fieldErrors", Map.of("fullName", "Họ tên phải từ 2 đến 100 ký tự")));
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        user.setFullName(fullName);
        user.setPhone(blankToNull(request.phone()));
        user.setAddress(blankToNull(request.address()));
        return UserProfileResponse.from(user);
    }

    /**
     * Đổi mật khẩu (FR-04). Mật khẩu mới quá 72 byte bị từ chối TRƯỚC khi so mật khẩu cũ; sai mật khẩu hiện tại →
     * INVALID_CURRENT_PASSWORD và không đổi gì. Thành công: đổi hash + tăng token_version trong một câu UPDATE nguyên tử, nên
     * mọi token đã cấp (kể cả của thiết bị này) hết hiệu lực và người dùng phải đăng nhập lại. KHÔNG đánh dấu @Transactional:
     * không giữ entity user trong lúc UPDATE hàng loạt.
     */
    public void changePassword(Long userId, ChangePasswordRequest request) {
        PasswordPolicy.requireBcryptSafe(request.newPassword(), "newPassword");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String currentHash = user.getPasswordHash();
        if (!passwordEncoder.matches(request.currentPassword(), currentHash)) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        // Chỉ ghi khi mật khẩu trong DB vẫn là bản vừa xác nhận: lần đổi / đặt lại khác chen vào giữa thì coi như mật khẩu hiện tại sai.
        if (userRepository.changePassword(userId, currentHash, passwordEncoder.encode(request.newPassword())) == 0) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** "Lưu làm mặc định" khi đặt hàng (D-02): cập nhật SĐT + địa chỉ mặc định trong hồ sơ. */
    @Transactional
    public void updateDefaultContact(Long userId, String phone, String address) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        user.setPhone(phone);
        user.setAddress(address);
    }

    /** Hồ sơ nhiều user một lần (danh sách đơn hàng admin hiển thị khách). */
    @Transactional(readOnly = true)
    public Map<Long, UserProfileResponse> profiles(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, UserProfileResponse::from));
    }

    /** Id user có email / họ tên chứa từ khoá (không phân biệt hoa thường), tối đa `max` kết quả. */
    @Transactional(readOnly = true)
    public List<Long> findIdsByKeyword(String keyword, int max) {
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT).replace("\\", "").replace("%", "").replace("_", "") + "%";
        return userRepository.findIdsByKeyword(pattern, PageRequest.of(0, max));
    }
}
