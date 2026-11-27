package vn.campvivo.notification;

import java.util.Objects;

/**
 * Nội dung một thông báo trước khi gửi cho (một hoặc nhiều) người nhận. Dựng bằng {@link NotificationMessages}.
 * `groupKey` khác null = gộp vào dòng đang mở cùng khoá của từng người nhận (D-43). Tiêu đề / nội dung quá dài bị cắt cho vừa
 * cột (255 / 1000 ký tự).
 */
public record NotificationMessage(
        NotificationType type,
        String title,
        String content,
        String link,
        NotificationRefType refType,
        Long refId,
        String groupKey
) {

    static final int MAX_TITLE = 255;
    static final int MAX_CONTENT = 1000;
    static final int MAX_LINK = 500;
    static final int MAX_GROUP_KEY = 100;

    public NotificationMessage {
        Objects.requireNonNull(type, "type");
        title = cut(Objects.requireNonNull(title, "title"), MAX_TITLE);
        content = cut(Objects.requireNonNull(content, "content"), MAX_CONTENT);
        if (link != null && link.length() > MAX_LINK) {
            throw new IllegalArgumentException("link quá " + MAX_LINK + " ký tự");
        }
        if (groupKey != null && groupKey.length() > MAX_GROUP_KEY) {
            throw new IllegalArgumentException("groupKey quá " + MAX_GROUP_KEY + " ký tự");
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
