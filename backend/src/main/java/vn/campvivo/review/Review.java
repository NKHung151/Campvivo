package vn.campvivo.review;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Đánh giá sản phẩm hoặc combo (FR-18, BR-10; database.md mục 3.17). Đúng một trong `productId`/`comboId`.
 * `updatedAt` chỉ đổi khi chủ sửa ({@link #edit}); ẩn/hiện/đánh dấu an toàn của nhân viên không đụng tới (D-31).
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "combo_id")
    private Long comboId;

    @Column(nullable = false)
    private short rating;

    @Column(length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, length = 20)
    private ModerationStatus moderationStatus = ModerationStatus.SAFE;

    /** Các mục từ điển bị bắt, cách nhau dấu phẩy; null khi không bắt được gì. */
    @Column(name = "flagged_terms", length = 500)
    private String flaggedTerms;

    @Column(name = "is_hidden", nullable = false)
    private boolean hidden;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @Column(name = "hidden_by")
    private Long hiddenBy;

    @Column(name = "hidden_at")
    private OffsetDateTime hiddenAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Review() {
    }

    private Review(Long userId, Long productId, Long comboId, OffsetDateTime now) {
        this.userId = userId;
        this.productId = productId;
        this.comboId = comboId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Review forProduct(Long userId, Long productId, OffsetDateTime now) {
        return new Review(userId, productId, null, now);
    }

    public static Review forCombo(Long userId, Long comboId, OffsetDateTime now) {
        return new Review(userId, null, comboId, now);
    }

    /** Lần viết đầu: đặt nội dung + kết quả kiểm duyệt, giữ `updatedAt = createdAt`. */
    public void write(int rating, String content, boolean sensitive, List<String> flaggedTerms) {
        this.rating = (short) rating;
        this.content = content;
        applyModeration(sensitive, flaggedTerms);
    }

    /** Chủ sửa: kiểm duyệt lại từ đầu (kể cả khi đang CLEARED). */
    public void edit(int rating, String content, boolean sensitive, List<String> flaggedTerms, OffsetDateTime now) {
        write(rating, content, sensitive, flaggedTerms);
        this.updatedAt = now;
    }

    private void applyModeration(boolean sensitive, List<String> terms) {
        this.moderationStatus = sensitive ? ModerationStatus.SENSITIVE : ModerationStatus.SAFE;
        this.flaggedTerms = terms == null || terms.isEmpty() ? null : String.join(",", terms);
    }

    /** Ẩn (hoặc ghi đè lý do nếu đang ẩn). */
    public void hide(String reason, Long staffId, OffsetDateTime now) {
        this.hidden = true;
        this.hiddenReason = reason;
        this.hiddenBy = staffId;
        this.hiddenAt = now;
    }

    public void unhide() {
        this.hidden = false;
        this.hiddenReason = null;
        this.hiddenBy = null;
        this.hiddenAt = null;
    }

    /** SENSITIVE → CLEARED (hệ thống bắt nhầm); trạng thái khác giữ nguyên. */
    public void clearSensitive() {
        if (moderationStatus == ModerationStatus.SENSITIVE) {
            moderationStatus = ModerationStatus.CLEARED;
        }
    }

    public boolean isSensitive() { return moderationStatus == ModerationStatus.SENSITIVE; }

    public boolean isForProduct() { return productId != null; }

    /** "PRODUCT" / "COMBO" — cùng cách ghi với ItemSummary, OrderDetail. */
    public String getItemType() { return isForProduct() ? "PRODUCT" : "COMBO"; }

    public Long getItemId() { return isForProduct() ? productId : comboId; }

    public List<String> getFlaggedTermList() {
        return flaggedTerms == null ? List.of() : List.of(flaggedTerms.split(","));
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }
    public Long getComboId() { return comboId; }
    public int getRating() { return rating; }
    public String getContent() { return content; }
    public ModerationStatus getModerationStatus() { return moderationStatus; }
    public String getFlaggedTerms() { return flaggedTerms; }
    public boolean isHidden() { return hidden; }
    public String getHiddenReason() { return hiddenReason; }
    public Long getHiddenBy() { return hiddenBy; }
    public OffsetDateTime getHiddenAt() { return hiddenAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
