package vn.campvivo.common.realtime;

import java.util.Set;

/**
 * Các kênh server đẩy tới client (docs/02-ky-thuat/api.md mục 4.9). Truyền cho {@link RealtimeSender} dạng không có tiền tố
 * `/user`; client SUBSCRIBE với tiền tố `/user` (vd. `/user/queue/support`).
 */
public final class RealtimeDestinations {

    public static final String NOTIFICATIONS = "/queue/notifications";
    public static final String SUPPORT = "/queue/support";

    /** Tiền tố kênh riêng từng người dùng của STOMP (khớp WebSocketConfig). */
    public static final String USER_PREFIX = "/user";

    private static final Set<String> SERVER_DESTINATIONS = Set.of(NOTIFICATIONS, SUPPORT);
    private static final Set<String> SUBSCRIBABLE = Set.of(USER_PREFIX + NOTIFICATIONS, USER_PREFIX + SUPPORT);

    private RealtimeDestinations() {
    }

    /** Kênh server được phép đẩy tới. */
    public static boolean isServerDestination(String destination) {
        return destination != null && SERVER_DESTINATIONS.contains(destination);
    }

    /** Kênh client được phép SUBSCRIBE (client chỉ nhận, không SEND). */
    public static boolean isSubscribable(String destination) {
        return destination != null && SUBSCRIBABLE.contains(destination);
    }
}
