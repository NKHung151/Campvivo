package vn.campvivo.support;

/**
 * Phát sau khi lưu tin; {@link SupportRealtimeListener} đẩy realtime SAU khi giao dịch commit (D-15).
 * `staffSenderId` là nhân viên đã gửi (null nếu tin do khách gửi); nhân viên đó không được đẩy lại tin của chính mình.
 */
public record SupportMessageSentEvent(SupportMessageResponse message, Long staffSenderId) {
}
