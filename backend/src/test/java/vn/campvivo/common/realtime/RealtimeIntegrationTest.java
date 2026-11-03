package vn.campvivo.common.realtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
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
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WebSocket STOMP từ đầu đến cuối (N3-08, FR-22/FR-26): client STOMP thật qua SockJS tới cổng ngẫu nhiên của ứng dụng,
 * PostgreSQL thật. Kiểm tra xác thực JWT ở khung CONNECT, cô lập giữa người dùng, `toStaff`, chặn SEND / SUBSCRIBE sai kênh
 * và kiểm tra Origin. Mỗi test tự dọn user của mình.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class RealtimeIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final long WAIT_SECONDS = 5;

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired RealtimeSender sender;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<StompSession> openSessions = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        openSessions.forEach(s -> {
            if (s.isConnected()) {
                s.disconnect();
            }
        });
        openSessions.clear();
        createdUserIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        createdUserIds.clear();
    }

    // --- Hạ tầng test ---

    private User newUser(Role role) {
        String email = "ws-" + UUID.randomUUID() + "@campvivo.vn";
        User user = new User(email, passwordEncoder.encode("Passw0rd1"), "Người Dùng WS");
        user.setEmailVerified(true);
        user.setRole(role);
        user = users.saveAndFlush(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.createToken(user);
    }

    /** Kết quả một lần thử CONNECT: hoặc có phiên, hoặc có lý do thất bại. */
    private record Attempt(StompSession session, String failure) {
        boolean accepted() {
            return session != null;
        }
    }

    private static class ConnectionHandler extends StompSessionHandlerAdapter {
        final CompletableFuture<StompSession> connected = new CompletableFuture<>();
        final CompletableFuture<String> failure = new CompletableFuture<>();

        @Override public void afterConnected(StompSession session, StompHeaders headers) {
            connected.complete(session);
        }

        @Override public void handleFrame(StompHeaders headers, Object payload) {
            failure.complete("ERROR frame: " + headers.getFirst("message"));
        }

        @Override public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                              byte[] payload, Throwable exception) {
            failure.complete("exception: " + exception);
        }

        @Override public void handleTransportError(StompSession session, Throwable exception) {
            failure.complete("transport: " + exception);
        }
    }

    private WebSocketStompClient sockJsClient() {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private Attempt attempt(WebSocketStompClient client, String url, String authorization, String origin) throws Exception {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        if (origin != null) {
            handshake.setOrigin(origin);
        }
        StompHeaders connect = new StompHeaders();
        if (authorization != null) {
            connect.add("Authorization", authorization);
        }
        ConnectionHandler handler = new ConnectionHandler();
        client.connectAsync(url, handshake, connect, handler)
                .exceptionally(ex -> {
                    handler.failure.complete("connect: " + ex);
                    return null;
                });
        CompletableFuture.anyOf(handler.connected, handler.failure).get(WAIT_SECONDS, TimeUnit.SECONDS);
        if (handler.connected.isDone()) {
            StompSession session = handler.connected.get();
            openSessions.add(session);
            return new Attempt(session, null);
        }
        return new Attempt(null, handler.failure.get());
    }

    private Attempt connect(String authorization) throws Exception {
        return attempt(sockJsClient(), "http://localhost:" + port + "/ws", authorization, ALLOWED_ORIGIN);
    }

    /** Người nhận một kênh `/user/queue/...`; các tin nhận được nằm trong `inbox`. */
    private static class Listener implements StompFrameHandler {
        final BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();

        @Override public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @SuppressWarnings("unchecked")
        @Override public void handleFrame(StompHeaders headers, Object payload) {
            inbox.add((Map<String, Object>) payload);
        }

        Map<String, Object> next() throws InterruptedException {
            return inbox.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * Đăng ký `/user/queue/support` và CHỜ tới khi thật sự nhận được tin thử (đăng ký ở broker là bất đồng bộ nên không thể
     * chỉ tin vào việc lệnh SUBSCRIBE đã gửi). Sau đó dọn các tin thử còn sót để test bắt đầu với hộp thư sạch.
     */
    private Listener subscribeAndWaitUntilLive(StompSession session, User user) throws Exception {
        Listener listener = new Listener();
        session.subscribe("/user" + RealtimeDestinations.SUPPORT, listener);
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000;
        while (System.currentTimeMillis() < deadline) {
            sender.toUser(user.getId(), RealtimeDestinations.SUPPORT, Map.of("probe", true));
            if (listener.inbox.poll(150, TimeUnit.MILLISECONDS) != null) {
                Thread.sleep(300); // chờ các tin thử đã gửi dồn nốt
                listener.inbox.clear();
                return listener;
            }
        }
        throw new AssertionError("Đăng ký kênh không hoạt động sau " + WAIT_SECONDS + " giây");
    }

    /**
     * Bị từ chối ĐÚNG bởi xác thực CONNECT: bắt tay thành công rồi server trả khung ERROR của STOMP (Spring chỉ gửi nội dung
     * chung "Failed to send message to clientInboundChannel", không kèm lý do). Cần kiểm kiểu thất bại này chứ không chỉ "không
     * kết nối được", nếu không test vẫn xanh khi mọi kết nối đều hỏng (bắt tay 500, mất mạng...).
     */
    private static void assertRejectedByAuthentication(Attempt attempt) {
        assertThat(attempt.accepted()).as("CONNECT không hợp lệ phải bị từ chối").isFalse();
        assertThat(attempt.failure()).as("phải bị từ chối bằng khung ERROR của STOMP, không phải lỗi khác")
                .startsWith("ERROR frame");
    }

    private static Map<String, Object> msg(String to) {
        return Map.of("to", to);
    }

    /** Server trả ERROR rồi đóng kết nối khi vi phạm: chờ tới khi phiên không còn kết nối. */
    private static boolean waitUntilClosed(StompSession session) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000;
        while (System.currentTimeMillis() < deadline) {
            if (!session.isConnected()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    // --- Xác thực khi CONNECT ---

    @Test
    void aValidTokenConnectsAndReceivesMessagesSentToThatUser() throws Exception {
        User customer = newUser(Role.CUSTOMER);
        Attempt attempt = connect(bearer(customer));
        assertThat(attempt.accepted()).as(attempt.failure()).isTrue();
        Listener listener = subscribeAndWaitUntilLive(attempt.session(), customer);

        sender.toUser(customer.getId(), RealtimeDestinations.SUPPORT, Map.of("content", "Xin chào", "isFromStaff", true));

        Map<String, Object> received = listener.next();
        assertThat(received).containsEntry("content", "Xin chào").containsEntry("isFromStaff", true);
    }

    @Test
    void connectingWithoutATokenIsRejected() throws Exception {
        assertRejectedByAuthentication(connect(null));
    }

    @Test
    void connectingWithAMalformedOrTamperedTokenIsRejected() throws Exception {
        User customer = newUser(Role.CUSTOMER);

        assertRejectedByAuthentication(connect("Bearer khong-phai-jwt"));
        assertRejectedByAuthentication(connect("Bearer " + jwtService.createToken(customer) + "x")); // sai chữ ký
        assertRejectedByAuthentication(connect(jwtService.createToken(customer))); // thiếu tiền tố Bearer
        assertRejectedByAuthentication(connect("Bearer ")); // token rỗng
    }

    @Test
    void aTokenWithAStaleTokenVersionIsRejected() throws Exception {
        User customer = newUser(Role.CUSTOMER);
        String staleToken = bearer(customer);
        customer.setTokenVersion(customer.getTokenVersion() + 1); // như khi đăng xuất / đổi mật khẩu
        users.saveAndFlush(customer);

        assertRejectedByAuthentication(connect(staleToken));
    }

    @Test
    void aDisabledAccountIsRejectedEvenWithAValidToken() throws Exception {
        User customer = newUser(Role.CUSTOMER);
        String token = bearer(customer);
        customer.setActive(false);
        users.saveAndFlush(customer);

        assertRejectedByAuthentication(connect(token));
    }

    // --- Cô lập và gửi tới nhân viên ---

    @Test
    void aUserNeverReceivesMessagesAddressedToSomeoneElse() throws Exception {
        User alice = newUser(Role.CUSTOMER);
        User bob = newUser(Role.CUSTOMER);
        Listener aliceInbox = subscribeAndWaitUntilLive(connect(bearer(alice)).session(), alice);
        Listener bobInbox = subscribeAndWaitUntilLive(connect(bearer(bob)).session(), bob);

        sender.toUser(bob.getId(), RealtimeDestinations.SUPPORT, msg("bob"));
        sender.toUser(alice.getId(), RealtimeDestinations.SUPPORT, msg("alice"));

        assertThat(bobInbox.next()).containsEntry("to", "bob");
        assertThat(aliceInbox.next()).as("tin đầu tiên Alice nhận phải là của chính Alice").containsEntry("to", "alice");
        assertThat(aliceInbox.inbox).isEmpty();
        assertThat(bobInbox.inbox).isEmpty();
    }

    @Test
    void toStaffReachesEveryConnectedEmployeeAndAdminButNoCustomer() throws Exception {
        User customer = newUser(Role.CUSTOMER);
        User employee = newUser(Role.EMPLOYEE);
        User admin = newUser(Role.ADMIN);
        Listener customerInbox = subscribeAndWaitUntilLive(connect(bearer(customer)).session(), customer);
        Listener employeeInbox = subscribeAndWaitUntilLive(connect(bearer(employee)).session(), employee);
        Listener adminInbox = subscribeAndWaitUntilLive(connect(bearer(admin)).session(), admin);

        sender.toStaff(RealtimeDestinations.SUPPORT, msg("staff"));
        sender.toUser(customer.getId(), RealtimeDestinations.SUPPORT, msg("customer"));

        assertThat(employeeInbox.next()).containsEntry("to", "staff");
        assertThat(adminInbox.next()).containsEntry("to", "staff");
        assertThat(customerInbox.next()).as("khách chỉ nhận tin của mình").containsEntry("to", "customer");
        assertThat(customerInbox.inbox).isEmpty();
    }

    @Test
    void toStaffSkipsDisabledStaffAndOfflineStaffWithoutErrors() throws Exception {
        User online = newUser(Role.EMPLOYEE);
        User disabled = newUser(Role.EMPLOYEE);
        newUser(Role.ADMIN); // nhân viên không kết nối
        String disabledToken = bearer(disabled);
        Listener onlineInbox = subscribeAndWaitUntilLive(connect(bearer(online)).session(), online);
        disabled.setActive(false);
        users.saveAndFlush(disabled);

        sender.toStaff(RealtimeDestinations.SUPPORT, msg("staff"));

        assertThat(onlineInbox.next()).containsEntry("to", "staff");
        assertRejectedByAuthentication(connect(disabledToken));
    }

    // --- Chỉ nhận, không gửi ---

    @Test
    void subscribingToAnyOtherDestinationIsRejected() throws Exception {
        User customer = newUser(Role.CUSTOMER);
        for (String destination : List.of("/queue/support", "/topic/anything", "/user/queue/other", "/queue/notifications")) {
            Attempt attempt = connect(bearer(customer));
            assertThat(attempt.accepted()).isTrue();
            attempt.session().subscribe(destination, new Listener());

            assertThat(waitUntilClosed(attempt.session())).as("đăng ký " + destination + " phải bị từ chối").isTrue();
        }
    }

    @Test
    void aClientCannotSendFramesAndOthersNeverSeeThem() throws Exception {
        User attacker = newUser(Role.CUSTOMER);
        User victim = newUser(Role.CUSTOMER);
        Listener victimInbox = subscribeAndWaitUntilLive(connect(bearer(victim)).session(), victim);
        StompSession attackerSession = connect(bearer(attacker)).session();

        attackerSession.send("/user/" + victim.getId() + RealtimeDestinations.SUPPORT, Map.of("to", "gia-mao"));
        assertThat(waitUntilClosed(attackerSession)).as("khung SEND của client phải bị từ chối").isTrue();

        sender.toUser(victim.getId(), RealtimeDestinations.SUPPORT, msg("that"));
        assertThat(victimInbox.next()).as("nạn nhân chỉ nhận tin thật từ server").containsEntry("to", "that");
        assertThat(victimInbox.inbox).isEmpty();
    }

    // --- Origin ---

    @Test
    void aHandshakeFromAnUnlistedOriginIsRejectedButTheAllowedOneWorks() throws Exception {
        User customer = newUser(Role.CUSTOMER);
        String rawUrl = "ws://localhost:" + port + "/ws/websocket";
        WebSocketStompClient raw = new WebSocketStompClient(new StandardWebSocketClient());
        raw.setMessageConverter(new MappingJackson2MessageConverter());

        Attempt foreign = attempt(raw, rawUrl, bearer(customer), "http://evil.example");
        Attempt allowed = attempt(raw, rawUrl, bearer(customer), ALLOWED_ORIGIN);

        assertThat(foreign.accepted()).as("origin lạ phải bị từ chối").isFalse();
        assertThat(foreign.failure()).as("phải bị chặn ở bước bắt tay (403), không phải lỗi khác").contains("403");
        assertThat(allowed.accepted()).as(allowed.failure()).isTrue();
    }

    // --- RealtimeSender ---

    @Test
    void sendingToAnOfflineUserIsANoOpAndAnUnknownDestinationIsAProgrammingError() {
        User offline = newUser(Role.CUSTOMER);

        sender.toUser(offline.getId(), RealtimeDestinations.NOTIFICATIONS, msg("khong-ai-nhan"));

        assertThatThrownBy(() -> sender.toUser(offline.getId(), "/queue/khong-co", msg("x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sender.toStaff("/topic/all", msg("x"))).isInstanceOf(IllegalArgumentException.class);
    }
}
