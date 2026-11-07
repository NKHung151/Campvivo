package vn.campvivo.user;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.user.dto.ChangePasswordRequest;
import vn.campvivo.user.dto.UpdateProfileRequest;
import vn.campvivo.user.dto.UserProfileResponse;

/** /api/users/me — docs/02-ky-thuat/api.md mục 4.2. userId luôn lấy từ token, không nhận từ request. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@CurrentUserId Long userId) {
        return ApiResponse.of(userService.getProfile(userId));
    }

    /** Sửa hồ sơ của chính mình (mọi role đã đăng nhập). Chỉ fullName, phone, address; email không sửa được. */
    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateMe(@CurrentUserId Long userId,
                                                     @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.of(userService.updateProfile(userId, request));
    }

    /** Đổi mật khẩu; thành công thì mọi token cũ hết hiệu lực (phải đăng nhập lại). */
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@CurrentUserId Long userId,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request);
        return ApiResponse.of(null);
    }
}
