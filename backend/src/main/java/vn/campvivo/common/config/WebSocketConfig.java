package vn.campvivo.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import vn.campvivo.auth.StompAuthChannelInterceptor;
import vn.campvivo.common.realtime.RealtimeDestinations;

/**
 * WebSocket STOMP cho thông báo và chat hỗ trợ (D-15, architecture.md mục Realtime, api.md mục 4.9): endpoint `/ws` (có
 * SockJS dự phòng), simple broker `/queue`, kênh riêng từng người dùng `/user/...`. Xác thực JWT ở khung CONNECT
 * ({@link StompAuthChannelInterceptor}). Broker nằm trong bộ nhớ nên chỉ đúng khi chạy MỘT instance backend.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Client không gửi gì ngoài khung điều khiển nhỏ, nên giới hạn kích thước thấp. */
    private static final int MESSAGE_SIZE_LIMIT = 8 * 1024;
    /** Kết nối mở mà không gửi CONNECT trong khoảng này bị đóng. */
    private static final int TIME_TO_FIRST_MESSAGE_MS = 30_000;

    private final AppProperties properties;
    private final StompAuthChannelInterceptor authInterceptor;

    public WebSocketConfig(AppProperties properties, StompAuthChannelInterceptor authInterceptor) {
        this.properties = properties;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Origin của WebSocket kiểm RIÊNG với CORS; dùng cùng danh sách app.cors.allowed-origins.
        registry.addEndpoint("/ws")
                .setAllowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix(RealtimeDestinations.USER_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MESSAGE_SIZE_LIMIT).setTimeToFirstMessage(TIME_TO_FIRST_MESSAGE_MS);
    }
}
