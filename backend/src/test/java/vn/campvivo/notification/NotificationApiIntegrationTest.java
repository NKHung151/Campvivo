package vn.campvivo.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.campvivo.auth.JwtService;
import vn.campvivo.common.realtime.RealtimeSender;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/**
 * `GET /notifications`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all` (api.md 4.7, N4-03) qua chuỗi security
 * thật và PostgreSQL thật. Thông báo được tạo bằng NotificationService (đẩy WebSocket bị giả lập). Mỗi test tự tạo người dùng;
 * xoá người dùng thì thông báo xoá theo (ON DELETE CASCADE).
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class NotificationApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository users;
    @Autowired JwtService jwtService;
    @Autowired NotificationService service;
    @Autowired JdbcTemplate jdbc;
    @MockBean RealtimeSender sender;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // resolved_by trỏ tới người dùng của test khác trong cùng lớp đã bị SET NULL, nên xoá theo thứ tự nào cũng được
        createdUserIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
        createdUserIds.clear();
    }

    private record Account(long id, String token) {}

    private Account account(Role role, String fullName) {
        User u = new User("nt-api-" + UUID.randomUUID() + "@test.vn", "hash", fullName);
        u.setRole(role);
        u.setEmailVerified(true);
        u = users.saveAndFlush(u);
        createdUserIds.add(u.getId());
        return new Account(u.getId(), jwtService.createToken(u));
    }

    private static NotificationMessage msg(NotificationType type, long refId) {
        return new NotificationMessage(type, type.name() + " #" + refId, "Nội dung " + refId, "/x/" + refId,
                NotificationRefType.ORDER, refId, null);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Account as, int expectedStatus) throws Exception {
        var response = mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + as.token())).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        String body = response.getContentAsString();
        assertThat(response.getStatus()).as(body).isEqualTo(expectedStatus);
        return objectMapper.readTree(body);
    }

    private JsonNode list(Account as, String query) throws Exception {
        return call(get("/api/notifications" + query), as, 200);
    }

    private static List<String> titles(JsonNode listResponse) {
        return StreamSupport.stream(listResponse.path("data").path("items").spliterator(), false)
                .map(n -> n.path("title").asText()).toList();
    }

    private long idOf(Account as, String title) throws Exception {
        for (JsonNode n : list(as, "?limit=100").path("data").path("items")) {
            if (n.path("title").asText().equals(title)) {
                return n.path("id").asLong();
            }
        }
        throw new AssertionError("Không thấy thông báo " + title);
    }

    @Test
    void listShowsOnlyOwnNewestFirstWithPaginationAndUnreadCount() throws Exception {
        Account me = account(Role.CUSTOMER, "Khách A");
        Account other = account(Role.CUSTOMER, "Khách B");
        service.notifyUser(me.id(), msg(NotificationType.ORDER_STATUS, 1));
        service.notifyUser(me.id(), msg(NotificationType.ORDER_STATUS, 2));
        service.notifyUser(me.id(), msg(NotificationType.RETURN_STATUS, 3));
        service.notifyUser(other.id(), msg(NotificationType.ORDER_STATUS, 99));

        JsonNode page1 = list(me, "?limit=2");
        assertThat(page1.path("success").asBoolean()).isTrue();
        assertThat(titles(page1)).containsExactly("RETURN_STATUS #3", "ORDER_STATUS #2");
        assertThat(page1.path("data").path("unreadCount").asLong()).isEqualTo(3);
        assertThat(page1.path("pagination").path("totalItems").asLong()).isEqualTo(3);
        assertThat(page1.path("pagination").path("totalPages").asInt()).isEqualTo(2);
        assertThat(page1.path("pagination").path("currentPage").asInt()).isEqualTo(1);
        assertThat(titles(list(me, "?limit=2&page=2"))).containsExactly("ORDER_STATUS #1");

        JsonNode item = page1.path("data").path("items").get(0);
        assertThat(item.path("type").asText()).isEqualTo("RETURN_STATUS");
        assertThat(item.path("content").asText()).isEqualTo("Nội dung 3");
        assertThat(item.path("link").asText()).isEqualTo("/x/3");
        assertThat(item.path("eventCount").asInt()).isEqualTo(1);
        assertThat(item.path("isRead").asBoolean()).isFalse();
        assertThat(item.path("actionable").asBoolean()).isFalse();
        assertThat(item.path("createdAt").asText()).endsWith("+07:00");
        assertThat(item.has("resolvedAt")).isTrue();
        assertThat(item.path("resolvedAt").isNull()).isTrue();
        assertThat(item.path("resolvedBy").isNull()).isTrue();
        assertThat(item.has("userId")).isFalse();
        assertThat(item.has("groupKey")).isFalse();
    }

    @Test
    void filterByIsRead() throws Exception {
        Account me = account(Role.CUSTOMER, "Khách");
        service.notifyUser(me.id(), msg(NotificationType.ORDER_STATUS, 1));
        service.notifyUser(me.id(), msg(NotificationType.ORDER_STATUS, 2));
        call(patch("/api/notifications/" + idOf(me, "ORDER_STATUS #1") + "/read"), me, 200);

        assertThat(titles(list(me, "?isRead=true"))).containsExactly("ORDER_STATUS #1");
        assertThat(titles(list(me, "?isRead=false"))).containsExactly("ORDER_STATUS #2");
        assertThat(titles(list(me, ""))).hasSize(2);
    }

    @Test
    void staffSeesResolverAndFiltersByResolved() throws Exception {
        Account a = account(Role.EMPLOYEE, "Trần A");
        Account b = account(Role.ADMIN, "Lê B");
        service.notifyUsers(List.of(a.id(), b.id()), msg(NotificationType.ORDER_NEW, 10));
        service.notifyUsers(List.of(a.id(), b.id()), msg(NotificationType.ORDER_NEW, 11));
        service.notifyUser(b.id(), new NotificationMessage(NotificationType.ORDER_STATUS, "Không cần xử lý", "x", null,
                null, null, null));
        service.resolve(NotificationType.ORDER_NEW, NotificationRefType.ORDER, 10L, a.id());

        JsonNode resolved = list(b, "?resolved=true");
        assertThat(titles(resolved)).containsExactly("ORDER_NEW #10");
        JsonNode item = resolved.path("data").path("items").get(0);
        assertThat(item.path("actionable").asBoolean()).isTrue();
        assertThat(item.path("resolvedAt").asText()).endsWith("+07:00");
        assertThat(item.path("resolvedBy").path("id").asLong()).isEqualTo(a.id());
        assertThat(item.path("resolvedBy").path("fullName").asText()).isEqualTo("Trần A");

        // chưa xử lý = chỉ loại cần xử lý mà chưa ai làm (không gồm thông báo chỉ để biết)
        assertThat(titles(list(b, "?resolved=false"))).containsExactly("ORDER_NEW #11");
        // đã xử lý thì không còn đếm vào badge dù chưa đọc
        assertThat(list(b, "").path("data").path("unreadCount").asLong()).isEqualTo(2);
        assertThat(list(a, "").path("data").path("unreadCount").asLong()).isEqualTo(1);
    }

    @Test
    void markReadIsOwnerOnlyAndIdempotent() throws Exception {
        Account me = account(Role.CUSTOMER, "Khách A");
        Account other = account(Role.CUSTOMER, "Khách B");
        service.notifyUser(me.id(), msg(NotificationType.ORDER_STATUS, 1));
        service.notifyUser(me.id(), msg(NotificationType.ORDER_STATUS, 2));
        long id = idOf(me, "ORDER_STATUS #1");

        JsonNode notMine = call(patch("/api/notifications/" + id + "/read"), other, 404);
        assertThat(notMine.path("errorCode").asText()).isEqualTo("NOT_FOUND");
        assertThat(call(patch("/api/notifications/999999999/read"), me, 404).path("errorCode").asText()).isEqualTo("NOT_FOUND");

        JsonNode read = call(patch("/api/notifications/" + id + "/read"), me, 200);
        assertThat(read.path("data").path("unreadCount").asLong()).isEqualTo(1);
        JsonNode again = call(patch("/api/notifications/" + id + "/read"), me, 200);
        assertThat(again.path("data").path("unreadCount").asLong()).isEqualTo(1);
        assertThat(list(me, "?isRead=true").path("data").path("items").get(0).path("isRead").asBoolean()).isTrue();
    }

    @Test
    void readAllOnlyTouchesOwnAndReportsUpdated() throws Exception {
        Account me = account(Role.CUSTOMER, "Khách A");
        Account other = account(Role.CUSTOMER, "Khách B");
        service.notifyUser(me.id(), msg(NotificationType.ORDER_STATUS, 1));
        service.notifyUser(me.id(), msg(NotificationType.ORDER_STATUS, 2));
        service.notifyUser(other.id(), msg(NotificationType.ORDER_STATUS, 3));

        JsonNode first = call(patch("/api/notifications/read-all"), me, 200);
        assertThat(first.path("data").path("updated").asInt()).isEqualTo(2);
        assertThat(first.path("data").path("unreadCount").asLong()).isZero();
        assertThat(call(patch("/api/notifications/read-all"), me, 200).path("data").path("updated").asInt()).isZero();

        assertThat(list(other, "").path("data").path("unreadCount").asLong()).isEqualTo(1);
    }

    @Test
    void readAllClosesCustomerGroupButKeepsStaffGroupOpen() throws Exception {
        Account customer = account(Role.CUSTOMER, "Khách");
        Account staff = account(Role.EMPLOYEE, "NV");
        service.notifyUser(customer.id(), NotificationMessages.supportReply(customer.id(), "Tin 1"));
        service.notifyUser(staff.id(), NotificationMessages.supportNew(customer.id(), "Khách", "Tin 1"));

        call(patch("/api/notifications/read-all"), customer, 200);
        call(patch("/api/notifications/read-all"), staff, 200);
        service.notifyUser(customer.id(), NotificationMessages.supportReply(customer.id(), "Tin 2"));
        service.notifyUser(staff.id(), NotificationMessages.supportNew(customer.id(), "Khách", "Tin 2"));

        // khách: đọc đóng nhóm → tin sau là dòng mới
        JsonNode mine = list(customer, "");
        assertThat(mine.path("data").path("items")).hasSize(2);
        // nhân viên: chưa ai trả lời nên nhóm còn mở → vẫn một dòng, 2 sự kiện, chưa đọc lại
        JsonNode staffItems = list(staff, "").path("data").path("items");
        assertThat(staffItems).hasSize(1);
        assertThat(staffItems.get(0).path("eventCount").asInt()).isEqualTo(2);
        assertThat(staffItems.get(0).path("isRead").asBoolean()).isFalse();
        assertThat(staffItems.get(0).path("content").asText()).isEqualTo("Tin 2");
    }

    @Test
    void requiresLoginAndValidatesPaging() throws Exception {
        assertThat(mvc.perform(get("/api/notifications")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(patch("/api/notifications/read-all")).andReturn().getResponse().getStatus()).isEqualTo(401);

        Account me = account(Role.CUSTOMER, "Khách");
        assertThat(call(get("/api/notifications?limit=0"), me, 400).path("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
        call(get("/api/notifications?limit=101"), me, 400);
        call(get("/api/notifications?page=0"), me, 400);
        call(get("/api/notifications?isRead=abc"), me, 400);
    }
}
