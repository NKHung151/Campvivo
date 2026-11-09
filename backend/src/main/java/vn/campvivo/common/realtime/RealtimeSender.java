package vn.campvivo.common.realtime;

/**
 * Đẩy tin realtime tới trình duyệt qua WebSocket (D-15). Các module khác (notification, support) gọi giao diện này,
 * không đụng tới WebSocket/STOMP trực tiếp. Thường được gọi trong `@TransactionalEventListener(AFTER_COMMIT)` nên việc
 * gửi KHÔNG BAO GIỜ ném lỗi ra ngoài (lỗi chỉ được ghi log): tin realtime hỏng không được làm hỏng nghiệp vụ; client mất
 * tin sẽ đồng bộ lại bằng REST.
 */
public interface RealtimeSender {

    /**
     * Gửi tới MỘT người dùng đang kết nối (không kết nối thì không làm gì).
     *
     * @param destination một hằng của {@link RealtimeDestinations} (không có tiền tố `/user`)
     * @throws IllegalArgumentException nếu `destination` không phải kênh đã khai báo (lỗi lập trình, không phải lỗi gửi)
     */
    void toUser(Long userId, String destination, Object payload);

    /** Gửi tới mọi nhân viên (EMPLOYEE, ADMIN còn hoạt động) đang kết nối. */
    void toStaff(String destination, Object payload);

    /**
     * Như {@link #toStaff} nhưng bỏ qua nhân viên `excludedUserId` (thường là chính người vừa gây ra tin, đã có kết quả qua phản hồi
     * REST nên không cần nhận lại). `excludedUserId` null = không bỏ qua ai.
     */
    void toStaffExcept(Long excludedUserId, String destination, Object payload);
}
