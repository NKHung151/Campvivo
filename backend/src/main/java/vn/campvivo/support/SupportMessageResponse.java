package vn.campvivo.support;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** `SupportMessage` trong api.md mục 4.8; cũng là nội dung đẩy qua `/user/queue/support`. */
public record SupportMessageResponse(Long id, Long customerId, String content, boolean isFromStaff, OffsetDateTime createdAt) {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    public static SupportMessageResponse from(SupportMessage m) {
        return new SupportMessageResponse(m.getId(), m.getUserId(), m.getContent(), m.isFromStaff(),
                OffsetDateTime.ofInstant(m.getCreatedAt(), VN));
    }
}
