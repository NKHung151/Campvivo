package vn.campvivo.notification.dto;

/** Phản hồi của `PATCH /notifications/{id}/read` và `PATCH /notifications/read-all` (api.md 4.7). */
public final class ReadResponses {

    private ReadResponses() {
    }

    public record Read(long unreadCount) {}

    public record ReadAll(int updated, long unreadCount) {}
}
