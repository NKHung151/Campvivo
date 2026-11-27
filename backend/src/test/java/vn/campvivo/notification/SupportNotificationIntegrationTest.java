package vn.campvivo.notification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import vn.campvivo.support.PostgresTestContainerConfig;
import vn.campvivo.support.SupportService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thông báo chat hỗ trợ (N4-04, D-42, D-43) qua `SupportService` thật: `SUPPORT_NEW` gộp theo khách cho nhân viên, đã xử lý khi
 * có người trả lời; `SUPPORT_MESSAGE` gộp cho khách tới khi đọc. PostgreSQL thật; mỗi test tự tạo người dùng và tự dọn.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=none",
        "app.jwt.secret=test_secret_with_at_least_32_characters_ok"
})
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class SupportNotificationIntegrationTest {

    @Autowired SupportService support;
    @Autowired NotificationService notifications;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();
    private long customer;
    private long staffA;
    private long staffB;

    private record Row(long id, String type, Long refId, String title, String content, int eventCount, boolean read,
                       boolean resolved, Long resolvedBy) {}

    @BeforeEach
    void setUp() {
        customer = user("CUSTOMER", "Nguyễn An");
        staffA = user("EMPLOYEE", "Trần A");
        staffB = user("ADMIN", "Lê B");
    }

    @AfterEach
    void cleanUp() {
        dispatcher.drain(Duration.ofSeconds(10));
        createdUserIds.forEach(id -> jdbc.update("DELETE FROM support_messages WHERE user_id = ?", id));
        createdUserIds.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id)); // notifications xoá theo
        createdUserIds.clear();
    }

    private long user(String role, String name) {
        long id = jdbc.queryForObject("INSERT INTO users (email, password_hash, full_name, role, email_verified) "
                + "VALUES (?, 'x', ?, ?, TRUE) RETURNING id", Long.class, "nt-sup-" + UUID.randomUUID() + "@test.vn", name, role);
        createdUserIds.add(id);
        return id;
    }

    /** Thông báo của `userId` sau khi hàng đợi thông báo đã chạy hết việc của các thao tác trước đó. */
    private List<Row> rows(long userId, String type) {
        assertThat(dispatcher.drain(Duration.ofSeconds(10))).isTrue();
        return jdbc.query("SELECT id, type, ref_id, title, content, event_count, is_read, resolved_at, resolved_by "
                        + "FROM notifications WHERE user_id = ? AND type = ? ORDER BY id",
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getObject(3, Long.class), rs.getString(4),
                        rs.getString(5), rs.getInt(6), rs.getBoolean(7), rs.getObject(8) != null,
                        rs.getObject(9, Long.class)),
                userId, type);
    }

    @Test
    void customerMessagesAreGroupedPerStaffUntilSomeoneReplies() {
        support.sendAsCustomer(customer, "Lều này còn hàng không?");
        support.sendAsCustomer(customer, "Shop ơi");
        support.sendAsCustomer(customer, "  Cho mình hỏi size 4 người  ");

        for (long staff : List.of(staffA, staffB)) {
            assertThat(rows(staff, "SUPPORT_NEW")).singleElement().satisfies(r -> {
                assertThat(r.title()).isEqualTo("Nguyễn An đã nhắn tin hỗ trợ");
                assertThat(r.content()).isEqualTo("Cho mình hỏi size 4 người");
                assertThat(r.eventCount()).isEqualTo(3);
                assertThat(r.refId()).isEqualTo(customer);
                assertThat(r.resolved()).isFalse();
            });
        }
        assertThat(rows(customer, "SUPPORT_MESSAGE")).isEmpty();
    }

    @Test
    void staffReplyResolvesForEveryStaffAndNotifiesCustomer() {
        support.sendAsCustomer(customer, "Xin chào");

        support.sendAsStaff(staffA, customer, "Chào bạn, mình có thể giúp gì?");

        for (long staff : List.of(staffA, staffB)) {
            assertThat(rows(staff, "SUPPORT_NEW")).singleElement().satisfies(r -> {
                assertThat(r.resolved()).isTrue();
                assertThat(r.resolvedBy()).isEqualTo(staffA);
            });
        }
        assertThat(rows(customer, "SUPPORT_MESSAGE")).singleElement().satisfies(r -> {
            assertThat(r.title()).isEqualTo("Nhân viên hỗ trợ đã trả lời bạn");
            assertThat(r.content()).isEqualTo("Chào bạn, mình có thể giúp gì?");
        });
        assertThat(rows(staffA, "SUPPORT_MESSAGE")).as("nhân viên không nhận thông báo của khách").isEmpty();

        // Nhóm đã đóng: tin mới của khách mở dòng mới, chưa xử lý
        support.sendAsCustomer(customer, "Cảm ơn, cho mình hỏi thêm");
        assertThat(rows(staffB, "SUPPORT_NEW")).hasSize(2)
                .last().satisfies(r -> {
                    assertThat(r.resolved()).isFalse();
                    assertThat(r.eventCount()).isEqualTo(1);
                });
    }

    @Test
    void customerReplyNotificationIsGroupedUntilRead() {
        support.sendAsStaff(staffA, customer, "Tin 1");
        support.sendAsStaff(staffB, customer, "Tin 2");
        assertThat(rows(customer, "SUPPORT_MESSAGE")).singleElement().satisfies(r -> {
            assertThat(r.eventCount()).isEqualTo(2);
            assertThat(r.content()).isEqualTo("Tin 2");
        });

        long id = rows(customer, "SUPPORT_MESSAGE").get(0).id();
        notifications.markRead(customer, id);
        support.sendAsStaff(staffA, customer, "Tin 3");

        List<Row> after = rows(customer, "SUPPORT_MESSAGE");
        assertThat(after).hasSize(2);
        assertThat(after.get(0).read()).isTrue();
        assertThat(after.get(1).content()).isEqualTo("Tin 3");
        assertThat(after.get(1).read()).isFalse();
    }

    @Test
    void eachCustomerHasOwnGroupAndReplyOnlyResolvesThatCustomer() {
        long other = user("CUSTOMER", "Phạm Bình");
        support.sendAsCustomer(customer, "A hỏi");
        support.sendAsCustomer(other, "B hỏi");

        support.sendAsStaff(staffA, customer, "Trả lời A");

        List<Row> staffRows = rows(staffB, "SUPPORT_NEW");
        assertThat(staffRows).hasSize(2);
        assertThat(staffRows).filteredOn(r -> r.refId() == customer).singleElement()
                .satisfies(r -> assertThat(r.resolved()).isTrue());
        assertThat(staffRows).filteredOn(r -> r.refId() == other).singleElement()
                .satisfies(r -> {
                    assertThat(r.resolved()).isFalse();
                    assertThat(r.title()).isEqualTo("Phạm Bình đã nhắn tin hỗ trợ");
                });
    }

    @Test
    void concurrentCustomerMessagesStillEndInOneRowPerStaff() throws Exception {
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int n = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    support.sendAsCustomer(customer, "Tin song song " + n);
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

        for (long staff : List.of(staffA, staffB)) {
            assertThat(rows(staff, "SUPPORT_NEW")).singleElement()
                    .satisfies(r -> assertThat(r.eventCount()).isEqualTo(threads));
        }
    }
}
