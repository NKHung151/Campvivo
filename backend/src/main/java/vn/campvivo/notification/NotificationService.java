package vn.campvivo.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.realtime.RealtimeDestinations;
import vn.campvivo.common.realtime.RealtimeSender;
import vn.campvivo.notification.NotificationWriter.Written;
import vn.campvivo.notification.dto.NotificationListResponse;
import vn.campvivo.notification.dto.NotificationPush;
import vn.campvivo.notification.dto.NotificationResponse;
import vn.campvivo.notification.dto.ReadResponses;
import vn.campvivo.user.UserService;
import vn.campvivo.user.dto.UserProfileResponse;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tạo / gộp / đánh dấu đã xử lý thông báo rồi đẩy WebSocket (D-42, D-43, architecture.md "Sự kiện & side-effect").
 * Listener của các module gọi lớp này trong `@TransactionalEventListener(AFTER_COMMIT)`: mỗi lần ghi chạy trong transaction
 * MỚI (`REQUIRES_NEW` — transaction của nghiệp vụ đã commit) và chỉ đẩy WebSocket SAU khi transaction đó commit, nên client
 * nhận tin xong gọi REST là thấy ngay. `RealtimeSender` không ném lỗi; lỗi DB thì ném ra cho listener ghi log.
 */
@Service
public class NotificationService {

    private record Delivery(long userId, NotificationPush push) {}

    private final NotificationRepository repository;
    private final NotificationWriter writer;
    private final UserService userService;
    private final RealtimeSender sender;
    private final TransactionTemplate requiresNew;
    private final Clock clock;
    private final int batchSize;

    public NotificationService(NotificationRepository repository, NotificationWriter writer, UserService userService,
                               RealtimeSender sender, PlatformTransactionManager transactionManager,
                               AppProperties appProperties, Clock clock) {
        this.repository = repository;
        this.writer = writer;
        this.userService = userService;
        this.sender = sender;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.batchSize = appProperties.notification().fanOutBatchSize();
    }

    // ------------------------------------------------------------------ gửi

    public void notifyUser(Long userId, NotificationMessage message) {
        notifyUsers(List.of(userId), message);
    }

    /** Mọi nhân viên (EMPLOYEE, ADMIN) đang hoạt động, trừ `exceptUserId` (người gây ra sự kiện; null = không trừ ai). */
    public void notifyStaff(NotificationMessage message, Long exceptUserId) {
        notifyUsers(userService.activeStaffIds().stream().filter(id -> !id.equals(exceptUserId)).toList(), message);
    }

    /** Mọi khách đang hoạt động (VOUCHER_NEW). */
    public void notifyAllCustomers(NotificationMessage message) {
        notifyUsers(userService.activeCustomerIds(), message);
    }

    /**
     * Cùng một thông báo cho nhiều người, ghi theo lô `app.notification.fan-out-batch-size` (mỗi lô một transaction). Có
     * `groupKey` → gộp vào dòng đang mở của từng người (đẩy `UPDATED`), chưa có thì tạo (đẩy `CREATED`).
     */
    public void notifyUsers(Collection<Long> userIds, NotificationMessage message) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        for (int from = 0; from < ids.size(); from += batchSize) {
            List<Long> batch = ids.subList(from, Math.min(from + batchSize, ids.size()));
            List<Delivery> deliveries = requiresNew.execute(s -> write(batch, message));
            push(deliveries);
        }
    }

    /**
     * Cập nhật tiêu đề / nội dung dòng gộp đang mở `message.groupKey()` của mọi người nhận mà không báo lại (giữ trạng thái đọc,
     * không tăng `eventCount`); đẩy `UPDATED` cho những dòng thật sự đổi. Chưa có dòng mở → không làm gì. Trả số dòng đã đổi.
     */
    public int refreshGroup(NotificationMessage message) {
        List<Delivery> deliveries = requiresNew.execute(s -> deliveries(writer.refreshGroup(message)));
        push(deliveries);
        return deliveries.size();
    }

    /**
     * Đánh dấu "đã xử lý" mọi thông báo còn mở `type` của đối tượng `(refType, refId)` và đóng nhóm gộp (D-43). Đẩy `RESOLVED`
     * cho người nhận, trừ chính người xử lý (đã có kết quả qua REST). `resolvedBy` null = Hệ thống. Trả số dòng đã đổi.
     */
    public int resolve(NotificationType type, NotificationRefType refType, Long refId, Long resolvedBy) {
        if (!type.actionable()) {
            throw new IllegalArgumentException(type + " không phải loại cần xử lý");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Written> resolved = requiresNew.execute(s -> writer.resolveOpen(type, refType, refId, resolvedBy, now));
        if (resolved.isEmpty()) {
            return 0;
        }
        Map<Long, List<Long>> idsByUser = resolved.stream().collect(Collectors.groupingBy(Written::userId,
                LinkedHashMap::new, Collectors.mapping(Written::id, Collectors.toList())));
        idsByUser.remove(resolvedBy);
        if (!idsByUser.isEmpty()) {
            NotificationResponse.Actor actor = actor(resolvedBy);
            Map<Long, Long> unread = writer.unreadCounts(idsByUser.keySet());
            List<Delivery> deliveries = new ArrayList<>();
            idsByUser.forEach((userId, ids) -> deliveries.add(new Delivery(userId,
                    NotificationPush.resolved(ids, now, actor, unread.getOrDefault(userId, 0L)))));
            push(deliveries);
        }
        return resolved.size();
    }

    // ------------------------------------------------------------------ đọc (api.md 4.7)

    /** Thông báo của chính `userId`, mới nhất trước, kèm `unreadCount`. */
    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, Boolean isRead, Boolean resolved, int page, int limit) {
        PageRequest pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Notification> result = repository.findAll(NotificationSpecifications.of(userId, isRead, resolved), pageable);
        Map<Long, String> names = fullNames(result.getContent().stream().map(Notification::getResolvedBy)
                .filter(Objects::nonNull).distinct().toList());
        PageResponse<NotificationResponse> items = PageResponse.of(result.map(n -> NotificationResponse.from(n, names)));
        return NotificationListResponse.of(items, repository.countByUserIdAndReadFalseAndResolvedAtIsNull(userId));
    }

    /** Đánh dấu một thông báo của chính mình đã đọc (idempotent). Của người khác / không tồn tại → NOT_FOUND. */
    @Transactional
    public ReadResponses.Read markRead(Long userId, Long id) {
        Notification n = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        n.markRead();
        repository.flush();
        return new ReadResponses.Read(repository.countByUserIdAndReadFalseAndResolvedAtIsNull(userId));
    }

    /** Đọc tất cả thông báo của chính mình; `updated` = số dòng vừa đổi. */
    @Transactional
    public ReadResponses.ReadAll markAllRead(Long userId) {
        int updated = writer.markAllRead(userId);
        return new ReadResponses.ReadAll(updated, repository.countByUserIdAndReadFalseAndResolvedAtIsNull(userId));
    }

    // ------------------------------------------------------------------

    private Map<Long, String> fullNames(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userService.profiles(userIds).values().stream()
                .collect(Collectors.toMap(UserProfileResponse::id, p -> p.fullName() == null ? "" : p.fullName()));
    }

    private List<Delivery> write(List<Long> userIds, NotificationMessage message) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return deliveries(message.groupKey() == null
                ? writer.insert(userIds, message, now)
                : writer.upsertGroup(userIds, message, now));
    }

    /** Phong bì `CREATED` / `UPDATED` cho các dòng vừa ghi (đọc lại dòng + `unreadCount` trong cùng transaction). */
    private List<Delivery> deliveries(List<Written> written) {
        if (written.isEmpty()) {
            return List.of();
        }
        Map<Long, Notification> rows = repository.findAllById(written.stream().map(Written::id).toList()).stream()
                .collect(Collectors.toMap(Notification::getId, Function.identity()));
        Map<Long, Long> unread = writer.unreadCounts(written.stream().map(Written::userId).toList());
        return written.stream()
                .map(w -> new Delivery(w.userId(), NotificationPush.written(w.inserted(),
                        NotificationResponse.from(rows.get(w.id()), Map.of()), unread.getOrDefault(w.userId(), 0L))))
                .toList();
    }

    private void push(List<Delivery> deliveries) {
        deliveries.forEach(d -> sender.toUser(d.userId(), RealtimeDestinations.NOTIFICATIONS, d.push()));
    }

    private NotificationResponse.Actor actor(Long userId) {
        if (userId == null) {
            return null;
        }
        UserProfileResponse profile = userService.profiles(List.of(userId)).get(userId);
        return new NotificationResponse.Actor(userId, profile == null ? "" : profile.fullName());
    }
}
