package vn.campvivo.auth.dto;

import vn.campvivo.user.dto.UserProfileResponse;

/** Kết quả đăng nhập: `{ accessToken, user: UserProfile }` (api.md mục 4.1). */
public record LoginResponse(String accessToken, UserProfileResponse user) {}
