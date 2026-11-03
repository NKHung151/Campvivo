package vn.campvivo.auth;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import vn.campvivo.common.realtime.RealtimeDestinations;
import vn.campvivo.user.User;

import java.util.Optional;

/**
 * Xác thực và phân quyền khung STOMP từ client (authentication.md mục 5, api.md mục 4.9).
 * <ul>
 *   <li>CONNECT: bắt buộc header "Authorization: Bearer &lt;JWT&gt;" hợp lệ (cùng quy tắc với HTTP, qua
 *       {@link TokenAuthenticator}); principal = userId. Trình duyệt không đặt được header ở bước bắt tay WebSocket nên
 *       token đi trong khung CONNECT. Sai/thiếu token → ném lỗi, STOMP trả ERROR và đóng kết nối.</li>
 *   <li>SUBSCRIBE: chỉ khi đã CONNECT hợp lệ và chỉ vào `/user/queue/notifications` hoặc `/user/queue/support`.</li>
 *   <li>SEND và mọi khung khác: bị từ chối — client chỉ NHẬN qua WebSocket, gửi qua REST.</li>
 * </ul>
 * Kiểm tra chỉ chạy lúc CONNECT: kết nối đang mở KHÔNG tự đóng khi token hết hạn / người dùng đăng xuất hay bị khoá.
 * Không log token.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final TokenAuthenticator tokenAuthenticator;

    public StompAuthChannelInterceptor(TokenAuthenticator tokenAuthenticator) {
        this.tokenAuthenticator = tokenAuthenticator;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // khung heartbeat
        }

        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case UNSUBSCRIBE, DISCONNECT -> { }
            default -> throw new AccessDeniedException("Máy khách không được gửi khung " + command);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = TokenAuthenticator.bearerToken(accessor.getFirstNativeHeader("Authorization"));
        Optional<User> user = token == null ? Optional.empty() : tokenAuthenticator.authenticate(token);
        if (user.isEmpty()) {
            throw new AccessDeniedException("Token không hợp lệ");
        }
        accessor.setUser(TokenAuthenticator.toAuthentication(user.get()));
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new AccessDeniedException("Chưa xác thực");
        }
        if (!RealtimeDestinations.isSubscribable(accessor.getDestination())) {
            throw new AccessDeniedException("Không được đăng ký kênh này");
        }
    }
}
