package vn.campvivo.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/** Đọc thông báo. Ghi hàng loạt (tạo, gộp, đã xử lý, đọc tất cả) nằm ở {@link NotificationWriter}. */
public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    /** Quyền sở hữu: thông báo của người khác coi như không tồn tại (404). */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /** `unreadCount` (api.md 4.7): chưa đọc VÀ chưa xử lý. */
    long countByUserIdAndReadFalseAndResolvedAtIsNull(Long userId);
}
