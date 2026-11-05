package vn.campvivo.support;

/** Phát sau khi lưu tin; {@link SupportRealtimeListener} đẩy realtime SAU khi giao dịch commit (D-15). */
public record SupportMessageSentEvent(SupportMessageResponse message) {
}
