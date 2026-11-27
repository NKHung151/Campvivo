package vn.campvivo.notification;

import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;

/** Bộ lọc của `GET /notifications` (api.md 4.7). Luôn kèm điều kiện người nhận — không bao giờ lộ thông báo của người khác. */
final class NotificationSpecifications {

    private static final List<NotificationType> ACTIONABLE = Arrays.stream(NotificationType.values())
            .filter(NotificationType::actionable).toList();

    private NotificationSpecifications() {
    }

    /**
     * `isRead` null = mọi trạng thái đọc. `resolved` null = tất cả; `true` = đã có người xử lý; `false` = loại cần xử lý mà chưa
     * ai làm (D-43).
     */
    static Specification<Notification> of(Long userId, Boolean isRead, Boolean resolved) {
        Specification<Notification> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);
        if (isRead != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("read"), isRead));
        }
        if (Boolean.TRUE.equals(resolved)) {
            spec = spec.and((root, query, cb) -> cb.isNotNull(root.get("resolvedAt")));
        } else if (Boolean.FALSE.equals(resolved)) {
            spec = spec.and((root, query, cb) -> cb.and(cb.isNull(root.get("resolvedAt")), root.get("type").in(ACTIONABLE)));
        }
        return spec;
    }
}
