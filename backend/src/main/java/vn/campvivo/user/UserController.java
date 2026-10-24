package vn.campvivo.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
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
}
