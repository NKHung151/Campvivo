package vn.campvivo.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PUT /users/me — docs/02-ky-thuat/api.md mục 4.2. Chỉ ba trường này sửa được: email và role không nằm trong request nên
 * dù client gửi kèm cũng bị bỏ qua. `phone` cùng luật với địa chỉ nhận hàng khi đặt đơn (10 số, bắt đầu bằng 0); `phone`
 * và `address` bỏ trống/rỗng nghĩa là xoá giá trị đã lưu.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
        String fullName,

        @Pattern(regexp = "^(0\\d{9})?$", message = "Số điện thoại phải gồm 10 số, bắt đầu bằng 0")
        String phone,

        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address
) {}
