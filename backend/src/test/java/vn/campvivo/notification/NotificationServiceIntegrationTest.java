package vn.campvivo.notification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;
import vn.campvivo.common.realtime.RealtimeDestinations;
import vn.campvivo.common.realtime.RealtimeSender;
import vn.campvivo.notification.dto.NotificationPush;
import vn.campvivo.support.PostgresTestContainerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * NotificationService (N4-03, D-42, D-43) trên PostgreSQL thật: fan-out, gộp bằng ON CONFLICT (kể cả ghi song song), đánh dấu
 * đã xử lý, nội dung đẩy WebSocket. DB dùng chung với test khác: mỗi test tự tạo người dùng, nội dung có `marker` riêng để dọn.
 * Lô nhỏ (`fan-out-batch-size=2`) để kiểm tra chia lô.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok",
        "app.notification.fan-out-batch-size=2"
})
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class NotificationServiceIntegrationTest {

    @Autowired NotificationService service;
    @Autowired NotificationRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @MockBean RealtimeSender sender;

    private final List<Long> createdUserIds = new ArrayList<>();
    private String marker;

    @BeforeEach
    void setUp() {
        marker = UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM notifications WHERE title LIKE ?", "%" + marker + "%");
        createdUserIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
        createdUserIds.clear();
    }

    private long user(String role, String name) {
        long id = jdbc.queryForObject("INSERT INTO users (email, password_hash, full_name, role) VALUES (?, 'x', ?, ?) RETURNING id",
                Long.class, "nt-" + UUID.randomUUID() + "@test.vn", name, role);
        createdUserIds.add(id);
        return id;
    }

    private NotificationMessage plain(NotificationType type, long refId) {
        return new NotificationMessage(type, "Thông báo " + marker, "Nội dung", "/x", NotificationRefType.ORDER, refId, null);
    }

    private NotificationMessage grouped(String content) {
        return new NotificationMessage(NotificationType.SUPPORT_MESSAGE, "Trả lời " + marker, content, "/account/support",
                NotificationRefType.CONVERSATION, 1L, "SUPPORT:" + marker);
    }

    private List<Notification> rowsOf(long userId) {
        return repository.findAll().stream().filter(n -> n.getUserId() == userId && n.getTitle().contains(marker)).toList();
    }

    private List<NotificationPush> pushesTo(long userId) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(sender, atLeastOnce()).toUser(eq(userId), eq(RealtimeDestinations.NOTIFICATIONS), payload.capture());
        return payload.getAllValues().stream().map(NotificationPush.class::cast).toList();
    }

    @Test
    void notifyUsersCreatesOneRowPerUserInBatchesAndPushesCreated() {
        List<Long> ids = List.of(user("CUSTOMER", "A"), user("CUSTOMER", "B"), user("CUSTOMER", "C"),
                user("CUSTOMER", "D"), user("CUSTOMER", "E"));
        List<Long> withDuplicate = new ArrayList<>(ids);
        withDuplicate.add(ids.get(0));

        service.notifyUsers(withDuplicate, plain(NotificationType.ORDER_STATUS, 108));

        for (long id : ids) {
            List<Notification> rows = rowsOf(id);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getRefId()).isEqualTo(108L);
            NotificationPush push = pushesTo(id).get(0);
            assertThat(push.event()).isEqualTo(NotificationPush.Event.CREATED);
            assertThat(push.notification().id()).isEqualTo(rows.get(0).getId());
            assertThat(push.notification().actionable()).isFalse();
            assertThat(push.unreadCount()).isEqualTo(1);
        }
    }

    @Test
    void notifyStaffSkipsActorAndCustomers() {
        long actor = user("EMPLOYEE", "NV A");
        long other = user("ADMIN", "Admin B");
        long customer = user("CUSTOMER", "Khách");
        jdbc.update("UPDATE users SET is_active = FALSE WHERE id = ?", user("EMPLOYEE", "NV đã khoá"));
        long locked = createdUserIds.get(createdUserIds.size() - 1);

        service.notifyStaff(plain(NotificationType.ORDER_NEW, 200), actor);

        assertThat(rowsOf(other)).hasSize(1);
        assertThat(rowsOf(actor)).isEmpty();
        assertThat(rowsOf(customer)).isEmpty();
        assertThat(rowsOf(locked)).isEmpty();
        verify(sender, never()).toUser(eq(actor), any(), any());
    }

    @Test
    void groupedMessageUpdatesOpenRowAndReopensItAsUnread() {
        long customer = user("CUSTOMER", "Khách");

        service.notifyUser(customer, grouped("Tin 1"));
        // đánh dấu đọc nhưng giữ nhóm mở (như loại cần xử lý): sự kiện mới phải làm dòng "chưa đọc" lại
        jdbc.update("UPDATE notifications SET is_read = TRUE WHERE user_id = ?", customer);
        service.notifyUser(customer, grouped("Tin 2"));

        List<Notification> rows = rowsOf(customer);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEventCount()).isEqualTo(2);
        assertThat(rows.get(0).getContent()).isEqualTo("Tin 2");
        assertThat(rows.get(0).isRead()).isFalse();
        assertThat(pushesTo(customer)).extracting(NotificationPush::event)
                .containsExactly(NotificationPush.Event.CREATED, NotificationPush.Event.UPDATED);
    }

    @Test
    void readingNonActionableGroupClosesItSoNextEventStartsNewRow() {
        long customer = user("CUSTOMER", "Khách");
        service.notifyUser(customer, grouped("Tin 1"));
        tx.executeWithoutResult(s -> repository.findAll().stream()
                .filter(n -> n.getUserId() == customer).forEach(Notification::markRead));

        service.notifyUser(customer, grouped("Tin 2"));

        List<Notification> rows = rowsOf(customer);
        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(n -> n.getGroupKey() != null).singleElement()
                .satisfies(n -> assertThat(n.getContent()).isEqualTo("Tin 2"));
    }

    @Test
    void concurrentGroupedEventsEndInOneRow() throws Exception {
        long customer = user("CUSTOMER", "Khách");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int n = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    service.notifyUser(customer, grouped("Tin " + n));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<Notification> rows = rowsOf(customer);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getEventCount()).isEqualTo(threads);
    }

    @Test
    void resolveMarksEveryRecipientAndPushesToOthersOnly() {
        long a = user("EMPLOYEE", "Trần A");
        long b = user("EMPLOYEE", "Lê B");
        long orderId = 900_000 + Math.abs(marker.hashCode() % 1000);
        service.notifyUsers(List.of(a, b), plain(NotificationType.ORDER_NEW, orderId));
        service.notifyUsers(List.of(a, b), plain(NotificationType.ORDER_NEW, orderId + 1)); // đơn khác: không bị đụng
        clearInvocations(sender);

        int changed = service.resolve(NotificationType.ORDER_NEW, NotificationRefType.ORDER, orderId, a);

        assertThat(changed).isEqualTo(2);
        for (long id : List.of(a, b)) {
            Map<Long, Notification> byRef = rowsOf(id).stream().collect(Collectors.toMap(Notification::getRefId, n -> n));
            assertThat(byRef.get(orderId).getResolvedBy()).isEqualTo(a);
            assertThat(byRef.get(orderId).getResolvedAt()).isNotNull();
            assertThat(byRef.get(orderId + 1).getResolvedAt()).isNull();
        }
        verify(sender, never()).toUser(eq(a), any(), any());
        NotificationPush push = pushesTo(b).get(0);
        assertThat(push.event()).isEqualTo(NotificationPush.Event.RESOLVED);
        assertThat(push.ids()).containsExactly(rowsOf(b).stream().filter(n -> n.getRefId() == orderId).findFirst()
                .orElseThrow().getId());
        assertThat(push.resolvedBy().id()).isEqualTo(a);
        assertThat(push.resolvedBy().fullName()).isEqualTo("Trần A");
        assertThat(push.unreadCount()).isEqualTo(1); // còn thông báo của đơn kia
        assertThat(repository.countByUserIdAndReadFalseAndResolvedAtIsNull(b)).isEqualTo(1);

        // Lần hai: không còn gì để xử lý, không đẩy thêm
        clearInvocations(sender);
        assertThat(service.resolve(NotificationType.ORDER_NEW, NotificationRefType.ORDER, orderId, b)).isZero();
        verify(sender, never()).toUser(anyLong(), any(), any());
    }

    @Test
    void resolveBySystemHasNoActorAndClosesGroup() {
        long a = user("EMPLOYEE", "Trần A");
        NotificationMessage low = new NotificationMessage(NotificationType.LOW_STOCK, "Sắp hết " + marker, "Còn 2", "/x",
                NotificationRefType.PRODUCT, 77L, "LOW_STOCK:PRODUCT:" + marker);
        service.notifyUser(a, low);
        clearInvocations(sender);

        service.resolve(NotificationType.LOW_STOCK, NotificationRefType.PRODUCT, 77L, null);

        Notification row = rowsOf(a).get(0);
        assertThat(row.getResolvedAt()).isNotNull();
        assertThat(row.getResolvedBy()).isNull();
        assertThat(row.getGroupKey()).isNull();
        NotificationPush push = pushesTo(a).get(0);
        assertThat(push.resolvedBy()).isNull();
        assertThat(push.unreadCount()).isZero();

        // sau khi đóng nhóm, lần sắp hết kế tiếp là một dòng mới
        service.notifyUser(a, low);
        assertThat(rowsOf(a)).hasSize(2);
    }

    @Test
    void resolveRejectsNonActionableType() {
        assertThatThrownBy(() -> service.resolve(NotificationType.ORDER_STATUS, NotificationRefType.ORDER, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
