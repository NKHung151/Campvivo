package vn.campvivo.user;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.user.dto.UserProfileResponse;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Hồ sơ người dùng (FR-04). Mới có xem hồ sơ; sửa hồ sơ và đổi mật khẩu làm ở N3-05. */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return UserProfileResponse.from(user);
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
