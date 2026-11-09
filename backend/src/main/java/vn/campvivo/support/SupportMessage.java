package vn.campvivo.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Bảng support_messages — khớp docs/02-ky-thuat/database.md 3.19 (D-05). `userId` luôn là KHÁCH (chủ hội thoại), kể cả khi
 * tin do nhân viên gửi (`fromStaff = true`). Không lưu nhân viên nào trả lời, không có trạng thái đã đọc (KI-02).
 */
@Entity
@Table(name = "support_messages")
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "is_from_staff", nullable = false)
    private boolean fromStaff;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupportMessage() {
    }

    public SupportMessage(Long userId, String content, boolean fromStaff) {
        this.userId = userId;
        this.content = content;
        this.fromStaff = fromStaff;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public boolean isFromStaff() {
        return fromStaff;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
