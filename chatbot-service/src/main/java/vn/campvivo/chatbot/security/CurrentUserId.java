package vn.campvivo.chatbot.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lấy userId của người dùng đang đăng nhập vào tham số controller: {@code @CurrentUserId Long userId}.
 * Principal được JwtAuthFilter đặt là userId (Long). KHÔNG nhận userId từ request body/query
 * (authentication.md mục 5).
 */
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
public @interface CurrentUserId {
}
