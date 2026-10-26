package vn.campvivo.user.dto;

import vn.campvivo.user.Role;
import vn.campvivo.user.User;

/** Khớp kiểu `UserProfile` ở docs/02-ky-thuat/api.md mục 3. Không bao giờ chứa mật khẩu hay trường OTP. */
public record UserProfileResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String address,
        Role role
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.getAddress(), user.getRole());
    }
}
