package vn.campvivo.user.admin;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.campvivo.user.Role;

/**
 * PATCH /api/admin/users/{id} — docs/02-ky-thuat/api.md mục 4.10. Sửa TỪNG PHẦN: trường vắng hoặc null = giữ nguyên; `phone` là
 * chuỗi rỗng = xoá. Chỉ bốn trường này sửa được: email, mật khẩu, OTP... không nằm trong request nên gửi kèm cũng bị bỏ qua.
 * `role` / `isActive` đổi giá trị thật thì tăng token_version (người đó bị đăng xuất ở mọi thiết bị).
 */
public record AdminUpdateUserRequest(
        @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
        String fullName,

        @Pattern(regexp = "^(0\\d{9})?$", message = "Số điện thoại phải gồm 10 số, bắt đầu bằng 0")
        String phone,

        Role role,

        Boolean isActive
) {
}
