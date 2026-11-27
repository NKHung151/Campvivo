package vn.campvivo.notification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import vn.campvivo.auth.JwtService;
import vn.campvivo.common.realtime.RealtimeDestinations;
import vn.campvivo.common.realtime.RealtimeSender;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thông báo đi tới trình duyệt từ đầu đến cuối (NT-33, api.md 4.9, D-43): client STOMP thật qua SockJS, kênh
 * `/user/queue/notifications`, PostgreSQL thật. Kiểm phong bì `CREATED` / `UPDATED` / `RESOLVED`, `unreadCount` của từng người,
 * người xử lý không nhận lại `RESOLVED`, và cô lập giữa người dùng. Mỗi test tự dọn user + thông báo của mình (`marker`).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class NotificationRealtimeIntegrationTest {

    private static final String ORIGIN = "http://localhost:3000";
    private static final long WAIT_SECONDS = 5;

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired RealtimeSender sender;
    @Autowired NotificationService service;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();
    private String marker;

    @BeforeEach
    void setUp() {
        marker = "RT" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @AfterEach
    void cleanUp() {
        sessions.forEach(s -> {
            if (s.isConnected()) {
                s.disconnect();
            }
        });
        sessions.clear();
        jdbc.update("DELETE FROM notifications WHERE title LIKE ? OR content LIKE ?", "%" + marker + "%", "%" + marker + "%");
        createdUserIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        createdUserIds.clear();
    }

    // --- Hạ tầng ---

    private User newUser(Role role, String name) {
        User user = new User("ntrt-" + UUID.randomUUID() + "@campvivo.vn", passwordEncoder.encode("Passw0rd1"), name);
        user.setEmailVerified(true);
        user.setRole(role);
        user = users.saveAndFlush(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private static class Inbox implements StompFrameHandler {
        final BlockingQueue<Map<String, Object>> frames = new LinkedBlockingQueue<>();

        @Override public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @SuppressWarnings("unchecked")
        @Override public void handleFrame(StompHeaders headers, Object payload) {
            frames.add((Map<String, Object>) payload);
        }

        Map<String, Object> next() throws InterruptedException {
            return frames.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        }

        /** Không có khung nào tới thêm trong một khoảng ngắn. */
        boolean staysEmpty() throws InterruptedException {
            return frames.poll(700, TimeUnit.MILLISECONDS) == null;
        }
    }

    /** Nối STOMP bằng JWT của `user`, đăng ký kênh thông báo và CHỜ tới khi đăng ký thật sự nhận được (broker bất đồng bộ). */
    private Inbox listen(User user) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin(ORIGIN);
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + jwtService.createToken(user));
        StompSession session = client.connectAsync("http://localhost:" + port + "/ws", handshake, connect,
                new StompSessionHandlerAdapter() {}).get(WAIT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);

        Inbox inbox = new Inbox();
        session.subscribe("/user" + RealtimeDestinations.NOTIFICATIONS, inbox);
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000;
        while (System.currentTimeMillis() < deadline) {
            sender.toUser(user.getId(), RealtimeDestinations.NOTIFICATIONS, Map.of("probe", true));
            if (inbox.frames.poll(150, TimeUnit.MILLISECONDS) != null) {
                Thread.sleep(300);
                inbox.frames.clear();
                return inbox;
            }
        }
        throw new AssertionError("Đăng ký /user/queue/notifications không hoạt động");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> notificationOf(Map<String, Object> frame) {
        return (Map<String, Object>) frame.get("notification");
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    // --- Test ---

    @Test
    void aNewNotificationReachesOnlyItsRecipientAsACreatedEnvelopeWithTheirUnreadCount() throws Exception {
        User customer = newUser(Role.CUSTOMER, "Khách RT");
        User other = newUser(Role.CUSTOMER, "Khách khác");
        Inbox customerInbox = listen(customer);
        Inbox otherInbox = listen(other);

        service.notifyUser(customer.getId(), NotificationMessages.orderStatus(9_000_001L, marker,
                vn.campvivo.order.OrderStatus.SHIPPING, null));

        Map<String, Object> frame = customerInbox.next();
        assertThat(frame).as("khách phải nhận khung CREATED").isNotNull().containsEntry("event", "CREATED");
        assertThat(number(frame.get("unreadCount"))).isEqualTo(1);
        Map<String, Object> n = notificationOf(frame);
        assertThat(n).containsEntry("type", "ORDER_STATUS").containsEntry("isRead", false)
                .containsEntry("actionable", false).containsEntry("eventCount", 1)
                .containsEntry("link", "/account/orders/9000001")
                .containsEntry("resolvedAt", null).containsEntry("resolvedBy", null);
        assertThat((String) n.get("title")).contains(marker);
        assertThat(number(n.get("id"))).isPositive();
        assertThat((String) n.get("createdAt")).as("giờ Việt Nam, kèm offset").endsWith("+07:00");

        assertThat(customerInbox.staysEmpty()).isTrue();
        assertThat(otherInbox.staysEmpty()).as("người khác không nhận gì").isTrue();
    }

    @Test
    void aSecondEventOnAnOpenGroupIsPushedAsUpdatedOnTheSameRow() throws Exception {
        User customer = newUser(Role.CUSTOMER, "Khách gộp");
        Inbox inbox = listen(customer);

        service.notifyUser(customer.getId(), NotificationMessages.supportReply(customer.getId(), "Tin 1 " + marker));
        Map<String, Object> first = inbox.next();
        service.notifyUser(customer.getId(), NotificationMessages.supportReply(customer.getId(), "Tin 2 " + marker));
        Map<String, Object> second = inbox.next();

        assertThat(first).containsEntry("event", "CREATED");
        assertThat(second).as("dòng gộp nhận thêm sự kiện").containsEntry("event", "UPDATED");
        assertThat(notificationOf(second).get("id")).isEqualTo(notificationOf(first).get("id"));
        assertThat(notificationOf(second)).containsEntry("eventCount", 2).containsEntry("type", "SUPPORT_MESSAGE");
        assertThat((String) notificationOf(second).get("content")).contains("Tin 2");
        assertThat(number(second.get("unreadCount"))).as("vẫn một dòng chưa đọc").isEqualTo(1);
    }

    @Test
    void whenStaffAResolvesStaffBReceivesResolvedWithAsNameAndAReceivesNothing() throws Exception {
        User staffA = newUser(Role.EMPLOYEE, "Nhân Viên A " + marker);
        User staffB = newUser(Role.ADMIN, "Quản Trị B");
        Inbox inboxA = listen(staffA);
        Inbox inboxB = listen(staffB);
        long orderId = 9_000_000L + Math.abs(marker.hashCode() % 99_999);

        service.notifyUsers(List.of(staffA.getId(), staffB.getId()),
                NotificationMessages.orderNew(orderId, marker, new BigDecimal("1250000"), 3));
        Map<String, Object> createdA = inboxA.next();
        Map<String, Object> createdB = inboxB.next();
        assertThat(createdA).containsEntry("event", "CREATED");
        assertThat(notificationOf(createdB)).containsEntry("type", "ORDER_NEW").containsEntry("actionable", true);
        assertThat(number(createdB.get("unreadCount"))).isEqualTo(1);

        int resolved = service.resolve(NotificationType.ORDER_NEW, NotificationRefType.ORDER, orderId, staffA.getId());

        assertThat(resolved).isEqualTo(2);
        Map<String, Object> frame = inboxB.next();
        assertThat(frame).as("B nhận RESOLVED").isNotNull().containsEntry("event", "RESOLVED");
        assertThat(((List<?>) frame.get("ids")).stream().map(x -> number(x)).toList())
                .containsExactly(number(notificationOf(createdB).get("id")));
        assertThat(frame.get("resolvedBy")).isEqualTo(Map.of("id", staffA.getId().intValue(), "fullName", staffA.getFullName()));
        assertThat((String) frame.get("resolvedAt")).endsWith("+07:00");
        assertThat(number(frame.get("unreadCount"))).as("dòng đã xử lý không còn tính chưa đọc").isZero();
        assertThat(inboxA.staysEmpty()).as("người xử lý đã có kết quả qua REST, không nhận lại").isTrue();
    }

    @Test
    void aResolutionByTheSystemReachesEveryRecipientWithANullActor() throws Exception {
        User staffA = newUser(Role.EMPLOYEE, "NV hệ thống A");
        User staffB = newUser(Role.EMPLOYEE, "NV hệ thống B");
        Inbox inboxA = listen(staffA);
        Inbox inboxB = listen(staffB);
        long orderId = 9_100_000L + Math.abs(marker.hashCode() % 99_999);
        service.notifyUsers(List.of(staffA.getId(), staffB.getId()),
                NotificationMessages.orderNew(orderId, marker, new BigDecimal("500000"), 1));
        inboxA.next();
        inboxB.next();

        service.resolve(NotificationType.ORDER_NEW, NotificationRefType.ORDER, orderId, null); // vd. job huỷ đơn quá hạn

        for (Inbox inbox : List.of(inboxA, inboxB)) {
            Map<String, Object> frame = inbox.next();
            assertThat(frame).isNotNull().containsEntry("event", "RESOLVED").containsEntry("resolvedBy", null);
        }
    }
}
