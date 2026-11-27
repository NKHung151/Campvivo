package vn.campvivo.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Bảng notifications — khớp docs/02-ky-thuat/database.md 3.20 (D-42, D-43). Mỗi người nhận một dòng (fan-out).
 * Dòng được TẠO / gộp / đánh dấu đã xử lý bằng SQL trong {@link NotificationWriter} (một câu cho nhiều người, ON CONFLICT);
 * entity chỉ dùng để đọc và đánh dấu đã đọc.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(length = 500)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", length = 20)
    private NotificationRefType refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(name = "group_key", length = 100)
    private String groupKey;

    @Column(name = "event_count", nullable = false)
    private int eventCount = 1;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Notification() {
    }

    /**
     * Đánh dấu đã đọc. Loại không cần xử lý thì đóng luôn dòng gộp (sự kiện sau tạo dòng mới); loại cần xử lý giữ nhóm mở tới khi
     * có người xử lý (D-43). Trả `false` nếu đã đọc từ trước (idempotent).
     */
    public boolean markRead() {
        if (read) {
            return false;
        }
        read = true;
        if (!type.actionable()) {
            groupKey = null;
        }
        return true;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getLink() { return link; }
    public NotificationRefType getRefType() { return refType; }
    public Long getRefId() { return refId; }
    public String getGroupKey() { return groupKey; }
    public int getEventCount() { return eventCount; }
    public boolean isRead() { return read; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public Long getResolvedBy() { return resolvedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
