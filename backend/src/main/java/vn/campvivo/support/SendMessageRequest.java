package vn.campvivo.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body gửi tin (api.md mục 4.8): chỉ text, 1–2000 ký tự (khoảng trắng hai đầu bị bỏ khi lưu). */
public record SendMessageRequest(
        @NotBlank(message = "Nội dung tin nhắn không được để trống.")
        @Size(max = 2000, message = "Nội dung tin nhắn tối đa 2000 ký tự.")
        String content) {
}
