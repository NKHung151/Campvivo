package vn.campvivo.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import vn.campvivo.auth.JwtService;
import vn.campvivo.common.realtime.RealtimeDestinations;
import vn.campvivo.common.realtime.RealtimeSender;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Chat hỗ trợ khách ↔ nhân viên (FR-26, N4-06) trên PostgreSQL thật: REST (MockMvc) và đẩy realtime qua STOMP thật trên cùng
 * một ngữ cảnh. DB dùng chung với các test khác nên mỗi test tự tạo người dùng riêng và tự dọn tin + người dùng của mình.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class SupportChatIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final long WAIT_SECONDS = 5;

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired RealtimeSender sender;
    @Autowired SupportService supportService;
    @Autowired TransactionTemplate tx;

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
        createdUserIds.forEach(id -> {
            jdbc.update("delete from support_messages where user_id = ?", id);
            users.findById(id).ifPresent(users::delete);
        });
        createdUserIds.clear();
    }

    // --- Hạ tầng test ---

    private User newUser(Role role, String fullName) {
        User user = new User("chat-" + UUID.randomUUID() + "@campvivo.vn", passwordEncoder.encode("Passw0rd1"), fullName);
        user.setRole(role);
        user.setEmailVerified(true);
        user = users.saveAndFlush(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private User customer() {
        return newUser(Role.CUSTOMER, "Khách Hàng");
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.createToken(user);
    }

    private MvcResult send(String path, User as, String content) throws Exception {
        MockHttpServletRequestBuilder request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content(objectMapper.writeValueAsBytes(Map.of("content", content)));
        if (as != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(as));
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult sendRaw(String path, User as, String body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8")
                .header(HttpHeaders.AUTHORIZATION, bearer(as)).content(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .andReturn();
    }

    private MvcResult read(String path, User as) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (as != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(as));
        }
        return mvc.perform(request).andReturn();
    }

    private MvcResult customerSends(User customer, String content) throws Exception {
        return send("/api/support/messages", customer, content);
    }

    private MvcResult staffReplies(User staff, User customer, String content) throws Exception {
        return send("/api/admin/support/conversations/" + customer.getId() + "/messages", staff, content);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private List<String> contents(MvcResult result) throws Exception {
        return StreamSupport.stream(json(result).at("/data").spliterator(), false).map(n -> n.get("content").asText()).toList();
    }

    private List<Long> messageIds(MvcResult result) throws Exception {
        return StreamSupport.stream(json(result).at("/data").spliterator(), false).map(n -> n.get("id").asLong()).toList();
    }

    private List<Long> conversationCustomerIds(MvcResult result) throws Exception {
        return StreamSupport.stream(json(result).at("/data").spliterator(), false)
                .map(n -> n.at("/customer/id").asLong()).toList();
    }

    // --- Quyền truy cập ---

    @Test
    void guestsAreRejectedOnEveryEndpoint() throws Exception {
        assertThat(status(read("/api/support/messages", null))).isEqualTo(401);
        assertThat(status(send("/api/support/messages", null, "Xin chào"))).isEqualTo(401);
        assertThat(status(read("/api/admin/support/conversations", null))).isEqualTo(401);
        assertThat(status(read("/api/admin/support/conversations/1/messages", null))).isEqualTo(401);
        assertThat(status(send("/api/admin/support/conversations/1/messages", null, "Xin chào"))).isEqualTo(401);
    }

    @Test
    void staffCannotUseTheCustomerEndpoints() throws Exception {
        for (Role role : List.of(Role.EMPLOYEE, Role.ADMIN)) {
            User staff = newUser(role, "Nhân Viên");
            assertThat(status(read("/api/support/messages", staff))).as("GET " + role).isEqualTo(403);
            assertThat(status(send("/api/support/messages", staff, "Xin chào"))).as("POST " + role).isEqualTo(403);
        }
    }

    @Test
    void customersCannotUseTheStaffEndpoints() throws Exception {
        User victim = customer();
        User attacker = customer();
        customerSends(victim, "Tin riêng của nạn nhân");

        assertThat(status(read("/api/admin/support/conversations", attacker))).isEqualTo(403);
        MvcResult peek = read("/api/admin/support/conversations/" + victim.getId() + "/messages", attacker);
        assertThat(status(peek)).isEqualTo(403);
        assertThat(peek.getResponse().getContentAsString()).doesNotContain("nạn nhân");
        assertThat(status(staffReplies(attacker, victim, "Giả danh nhân viên"))).isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from support_messages where user_id = ? and is_from_staff", Integer.class,
                victim.getId())).as("khách không được tự ghi tin 'của nhân viên'").isZero();
    }

    // --- Khách gửi và đọc ---

    @Test
    void aCustomerSendsAMessageToTheirOwnConversation() throws Exception {
        User customer = customer();

        MvcResult result = customerSends(customer, "  Tôi cần tư vấn lều 4 người  ");

        assertThat(status(result)).isEqualTo(201);
        JsonNode data = json(result).get("data");
        assertThat(data.get("id").asLong()).isPositive();
        assertThat(data.get("customerId").asLong()).isEqualTo(customer.getId());
        assertThat(data.get("content").asText()).as("khoảng trắng hai đầu bị bỏ").isEqualTo("Tôi cần tư vấn lều 4 người");
        assertThat(data.get("isFromStaff").asBoolean()).isFalse();
        assertThat(data.get("createdAt").asText()).isNotBlank();
    }

    @Test
    void contentMustBeBetween1And2000Characters() throws Exception {
        User customer = customer();

        for (String bad : List.of("", "   ", "\n\t")) {
            MvcResult r = customerSends(customer, bad);
            assertThat(status(r)).as("'" + bad + "'").isEqualTo(400);
            assertThat(json(r).at("/errorCode").asText()).isEqualTo("VALIDATION_ERROR");
        }
        assertThat(status(sendRaw("/api/support/messages", customer, "{}"))).as("thiếu content").isEqualTo(400);
        MvcResult tooLong = customerSends(customer, "a".repeat(2001));
        assertThat(status(tooLong)).isEqualTo(400);
        assertThat(json(tooLong).at("/errorCode").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(status(customerSends(customer, "a".repeat(2000)))).as("đúng 2000 ký tự thì được").isEqualTo(201);
        assertThat(jdbc.queryForObject("select count(*) from support_messages where user_id = ?", Integer.class, customer.getId()))
                .as("chỉ tin hợp lệ được lưu").isEqualTo(1);
    }

    @Test
    void textIsStoredVerbatimWithoutBeingInterpretedAsHtml() throws Exception {
        User customer = customer();
        String html = "<script>alert('x')</script> & <b>đậm</b>";

        customerSends(customer, html);

        assertThat(contents(read("/api/support/messages", customer))).containsExactly(html);
    }

    @Test
    void aCustomerOnlySeesTheirOwnConversationNewestFirst() throws Exception {
        User mine = customer();
        User other = customer();
        customerSends(mine, "một");
        customerSends(other, "bí mật của người khác");
        customerSends(mine, "hai");
        staffReplies(newUser(Role.EMPLOYEE, "NV"), mine, "ba - nhân viên trả lời");
        staffReplies(newUser(Role.EMPLOYEE, "NV"), other, "trả lời người khác");

        MvcResult result = read("/api/support/messages", mine);

        assertThat(status(result)).isEqualTo(200);
        assertThat(contents(result)).containsExactly("ba - nhân viên trả lời", "hai", "một");
        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("người khác");
        JsonNode first = json(result).at("/data/0");
        assertThat(first.get("isFromStaff").asBoolean()).isTrue();
        assertThat(first.get("customerId").asLong()).as("tin của nhân viên vẫn thuộc hội thoại của khách").isEqualTo(mine.getId());
    }

    @Test
    void anEmptyConversationReturnsAnEmptyList() throws Exception {
        MvcResult result = read("/api/support/messages", customer());

        assertThat(status(result)).isEqualTo(200);
        assertThat(json(result).at("/data").size()).isZero();
    }

    @Test
    void scrollingBackWithBeforeNeitherSkipsNorRepeatsMessages() throws Exception {
        User customer = customer();
        for (int i = 1; i <= 5; i++) {
            customerSends(customer, "tin " + i);
        }

        MvcResult page1 = read("/api/support/messages?limit=2", customer);
        List<Long> ids1 = messageIds(page1);
        MvcResult page2 = read("/api/support/messages?limit=2&before=" + ids1.get(1), customer);
        List<Long> ids2 = messageIds(page2);
        MvcResult page3 = read("/api/support/messages?limit=2&before=" + ids2.get(1), customer);
        List<Long> ids3 = messageIds(page3);
        MvcResult page4 = read("/api/support/messages?limit=2&before=" + ids3.get(0), customer);

        assertThat(contents(page1)).containsExactly("tin 5", "tin 4");
        assertThat(contents(page2)).containsExactly("tin 3", "tin 2");
        assertThat(contents(page3)).containsExactly("tin 1");
        assertThat(json(page4).at("/data").size()).as("hết tin cũ").isZero();
    }

    @Test
    void beforeNeverReturnsAnotherCustomersMessages() throws Exception {
        User mine = customer();
        User other = customer();
        customerSends(mine, "của tôi 1");
        customerSends(other, "của người khác");
        customerSends(mine, "của tôi 2");
        long newest = messageIds(read("/api/support/messages", mine)).get(0);

        // `before` lớn hơn id của mọi tin: nếu bộ lọc user_id bị mất thì tin của người khác sẽ lọt vào
        assertThat(contents(read("/api/support/messages?before=" + (newest + 1000), mine)))
                .containsExactly("của tôi 2", "của tôi 1");
    }

    @Test
    void paginationParametersAreValidated() throws Exception {
        User customer = customer();

        for (String query : List.of("limit=0", "limit=101", "limit=abc", "before=0", "before=-5", "before=abc")) {
            MvcResult r = read("/api/support/messages?" + query, customer);
            assertThat(status(r)).as(query).isEqualTo(400);
            assertThat(json(r).at("/errorCode").asText()).as(query).isEqualTo("VALIDATION_ERROR");
        }
        assertThat(status(read("/api/support/messages?limit=100", customer))).isEqualTo(200);
    }

    // --- Nhân viên trả lời ---

    @Test
    void staffReplyIsStoredOnTheCustomersConversationAndTheCustomerSeesIt() throws Exception {
        User customer = customer();
        for (Role role : List.of(Role.EMPLOYEE, Role.ADMIN)) {
            User staff = newUser(role, "Nhân viên " + role);

            MvcResult result = staffReplies(staff, customer, "Dạ lều 4 người còn hàng " + role);

            assertThat(status(result)).as(role.name()).isEqualTo(201);
            JsonNode data = json(result).get("data");
            assertThat(data.get("customerId").asLong()).isEqualTo(customer.getId());
            assertThat(data.get("isFromStaff").asBoolean()).isTrue();
        }
        assertThat(contents(read("/api/support/messages", customer)))
                .containsExactly("Dạ lều 4 người còn hàng ADMIN", "Dạ lều 4 người còn hàng EMPLOYEE");
        assertThat(jdbc.queryForObject("select count(*) from support_messages where user_id = ? and is_from_staff", Integer.class,
                customer.getId())).isEqualTo(2);
    }

    @Test
    void staffCannotWriteToOrReadAnUnknownOrNonCustomerConversation() throws Exception {
        User staff = newUser(Role.EMPLOYEE, "NV");
        User otherStaff = newUser(Role.ADMIN, "Quản trị khác");

        for (long id : List.of(otherStaff.getId(), 999_999_999L)) {
            MvcResult write = send("/api/admin/support/conversations/" + id + "/messages", staff, "Gửi nhầm");
            assertThat(status(write)).as("ghi " + id).isEqualTo(404);
            assertThat(json(write).at("/errorCode").asText()).isEqualTo("NOT_FOUND");
            assertThat(status(read("/api/admin/support/conversations/" + id + "/messages", staff))).as("đọc " + id).isEqualTo(404);
        }
        assertThat(jdbc.queryForObject("select count(*) from support_messages where user_id = ?", Integer.class,
                otherStaff.getId())).as("không có tin nào gắn vào tài khoản nhân viên").isZero();
    }

    @Test
    void staffReadsOneCustomersMessagesWithPaging() throws Exception {
        User staff = newUser(Role.EMPLOYEE, "NV");
        User customer = customer();
        User other = customer();
        customerSends(customer, "a");
        customerSends(other, "không liên quan");
        staffReplies(staff, customer, "b");
        customerSends(customer, "c");
        String base = "/api/admin/support/conversations/" + customer.getId() + "/messages";

        MvcResult all = read(base, staff);
        MvcResult first = read(base + "?limit=2", staff);
        MvcResult older = read(base + "?limit=2&before=" + messageIds(first).get(1), staff);

        assertThat(contents(all)).containsExactly("c", "b", "a");
        assertThat(contents(first)).containsExactly("c", "b");
        assertThat(contents(older)).containsExactly("a");
    }

    // --- Danh sách hội thoại ---

    @Test
    void conversationsAreOnePerCustomerNewestActivityFirst() throws Exception {
        User staff = newUser(Role.EMPLOYEE, "NV");
        User a = newUser(Role.CUSTOMER, "Khách A");
        User b = newUser(Role.CUSTOMER, "Khách B");
        User c = newUser(Role.CUSTOMER, "Khách C");
        User silent = newUser(Role.CUSTOMER, "Khách chưa nhắn");
        customerSends(a, "A1");
        customerSends(b, "B1");
        customerSends(c, "C1");
        customerSends(a, "A2");

        MvcResult result = read("/api/admin/support/conversations?limit=100", staff);

        assertThat(status(result)).isEqualTo(200);
        List<Long> mine = conversationCustomerIds(result).stream()
                .filter(id -> List.of(a.getId(), b.getId(), c.getId(), silent.getId()).contains(id)).toList();
        assertThat(mine).as("mỗi khách một hội thoại, khách chưa nhắn không có; A mới nhất nên đứng đầu")
                .containsExactly(a.getId(), c.getId(), b.getId());
        JsonNode top = StreamSupport.stream(json(result).at("/data").spliterator(), false)
                .filter(n -> n.at("/customer/id").asLong() == a.getId()).findFirst().orElseThrow();
        assertThat(top.at("/customer/fullName").asText()).isEqualTo("Khách A");
        assertThat(top.at("/customer/email").asText()).isEqualTo(a.getEmail());
        assertThat(top.at("/lastMessage/content").asText()).as("tin cuối của hội thoại").isEqualTo("A2");
        assertThat(top.at("/customer").has("passwordHash")).isFalse();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash").doesNotContain("password");
        assertThat(json(result).at("/pagination/totalItems").asLong()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void aNewMessageMovesItsConversationToTheTopAndStaffReplyCountsAsActivity() throws Exception {
        User staff = newUser(Role.EMPLOYEE, "NV");
        User a = customer();
        User b = customer();
        customerSends(a, "A1");
        customerSends(b, "B1");
        assertThat(conversationCustomerIds(read("/api/admin/support/conversations?limit=1", staff))).containsExactly(b.getId());

        staffReplies(staff, a, "Trả lời A");

        MvcResult result = read("/api/admin/support/conversations?limit=1", staff);
        assertThat(conversationCustomerIds(result)).containsExactly(a.getId());
        assertThat(json(result).at("/data/0/lastMessage/content").asText()).isEqualTo("Trả lời A");
        assertThat(json(result).at("/data/0/lastMessage/isFromStaff").asBoolean()).isTrue();
    }

    @Test
    void conversationsPaginate() throws Exception {
        User staff = newUser(Role.ADMIN, "QT");
        User a = customer();
        User b = customer();
        customerSends(a, "A");
        customerSends(b, "B");

        MvcResult page1 = read("/api/admin/support/conversations?limit=1&page=1", staff);
        MvcResult page2 = read("/api/admin/support/conversations?limit=1&page=2", staff);

        assertThat(conversationCustomerIds(page1)).containsExactly(b.getId());
        assertThat(conversationCustomerIds(page2)).containsExactly(a.getId());
        assertThat(json(page1).at("/pagination/currentPage").asInt()).isEqualTo(1);
        assertThat(json(page1).at("/pagination/limit").asInt()).isEqualTo(1);
        assertThat(json(page1).at("/pagination/totalItems").asLong()).isGreaterThanOrEqualTo(2);
        for (String bad : List.of("page=0", "limit=0", "limit=101")) {
            assertThat(status(read("/api/admin/support/conversations?" + bad, staff))).as(bad).isEqualTo(400);
        }
    }

    // --- Realtime (STOMP thật) ---

    private static class Inbox implements StompFrameHandler {
        final BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();

        @Override public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @SuppressWarnings("unchecked")
        @Override public void handleFrame(StompHeaders headers, Object payload) {
            messages.add((Map<String, Object>) payload);
        }

        /** Tin thật kế tiếp; bỏ qua các tin thử `probe` còn trễ từ bước đăng ký của người khác. */
        Map<String, Object> next() throws InterruptedException {
            return nextWithin(WAIT_SECONDS * 1000);
        }

        /** true nếu KHÔNG có tin thật nào tới trong `millis` ms (khẳng định người không liên quan không nhận được gì). */
        boolean staysEmptyFor(long millis) throws InterruptedException {
            return nextWithin(millis) == null;
        }

        private Map<String, Object> nextWithin(long millis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + millis;
            while (true) {
                long left = deadline - System.currentTimeMillis();
                Map<String, Object> m = messages.poll(Math.max(left, 0), TimeUnit.MILLISECONDS);
                if (m == null || !m.containsKey("probe")) {
                    return m;
                }
            }
        }
    }

    private StompSession connect(User user) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin(ALLOWED_ORIGIN);
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", bearer(user));
        StompSession session = client.connectAsync("http://localhost:" + port + "/ws", handshake, headers,
                new StompSessionHandlerAdapter() { }).get(WAIT_SECONDS, TimeUnit.SECONDS);
        openSessions.add(session);
        return session;
    }

    /** Đăng ký `/user/queue/support` và CHỜ tới khi nhận được tin thử (SUBSCRIBE ở broker là bất đồng bộ); rồi dọn hộp thư. */
    private Inbox listen(User user) throws Exception {
        StompSession session = connect(user);
        Inbox inbox = new Inbox();
        session.subscribe("/user" + RealtimeDestinations.SUPPORT, inbox);
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000;
        while (System.currentTimeMillis() < deadline) {
            if (user.getRole() == Role.CUSTOMER) {
                sender.toUser(user.getId(), RealtimeDestinations.SUPPORT, Map.of("probe", true));
            } else {
                sender.toStaff(RealtimeDestinations.SUPPORT, Map.of("probe", true));
            }
            if (inbox.messages.poll(150, TimeUnit.MILLISECONDS) != null) {
                Thread.sleep(300);
                inbox.messages.clear();
                return inbox;
            }
        }
        throw new AssertionError("Đăng ký kênh không hoạt động sau " + WAIT_SECONDS + " giây");
    }

    /** Các tin thử của người đăng ký sau có thể tới hộp thư người đăng ký trước (toStaff): chờ rồi dọn hết trước khi kiểm. */
    private static void settle(Inbox... inboxes) throws InterruptedException {
        Thread.sleep(500);
        for (Inbox inbox : inboxes) {
            inbox.messages.clear();
        }
    }

    @Test
    void aCustomerMessageIsPushedToStaffOnlyNotToOtherCustomersOrTheSender() throws Exception {
        User employee = newUser(Role.EMPLOYEE, "NV");
        User admin = newUser(Role.ADMIN, "QT");
        User sender1 = customer();
        User bystander = customer();
        Inbox employeeInbox = listen(employee);
        Inbox adminInbox = listen(admin);
        Inbox senderInbox = listen(sender1);
        Inbox bystanderInbox = listen(bystander);
        settle(employeeInbox, adminInbox, senderInbox, bystanderInbox);

        MvcResult result = customerSends(sender1, "Có lều 4 người không?");

        assertThat(status(result)).isEqualTo(201);
        for (Inbox staffInbox : List.of(employeeInbox, adminInbox)) {
            Map<String, Object> received = staffInbox.next();
            assertThat(received).isNotNull();
            assertThat(received).containsEntry("content", "Có lều 4 người không?").containsEntry("isFromStaff", false);
            assertThat(((Number) received.get("customerId")).longValue()).isEqualTo(sender1.getId());
            assertThat(((Number) received.get("id")).longValue()).isEqualTo(json(result).at("/data/id").asLong());
        }
        assertThat(bystanderInbox.staysEmptyFor(800)).as("khách khác không nhận tin").isTrue();
        assertThat(senderInbox.staysEmptyFor(800)).as("người gửi nhận tin qua REST, không nhận lại qua WebSocket").isTrue();
    }

    @Test
    void aStaffReplyIsPushedToExactlyThatCustomer() throws Exception {
        User staff = newUser(Role.EMPLOYEE, "NV");
        User target = customer();
        User bystander = customer();
        Inbox targetInbox = listen(target);
        Inbox bystanderInbox = listen(bystander);

        MvcResult result = staffReplies(staff, target, "Dạ còn hàng ạ");

        assertThat(status(result)).isEqualTo(201);
        Map<String, Object> received = targetInbox.next();
        assertThat(received).isNotNull();
        assertThat(received).containsEntry("content", "Dạ còn hàng ạ").containsEntry("isFromStaff", true);
        assertThat(((Number) received.get("customerId")).longValue()).isEqualTo(target.getId());
        assertThat(bystanderInbox.staysEmptyFor(800)).as("khách khác không nhận tin trả lời").isTrue();
    }

    @Test
    void nothingIsPushedWhenTheTransactionRollsBack() throws Exception {
        User staff = newUser(Role.EMPLOYEE, "NV");
        User customer = customer();
        Inbox staffInbox = listen(staff);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            supportService.sendAsCustomer(customer.getId(), "Tin sẽ bị huỷ");
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(staffInbox.staysEmptyFor(1000)).as("không đẩy tin chưa được lưu").isTrue();
        assertThat(jdbc.queryForObject("select count(*) from support_messages where user_id = ?", Integer.class, customer.getId()))
                .isZero();
    }
}
