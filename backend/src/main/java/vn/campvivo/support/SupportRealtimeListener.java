package vn.campvivo.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.campvivo.common.realtime.RealtimeDestinations;
import vn.campvivo.common.realtime.RealtimeSender;

/**
 * Đẩy tin mới qua WebSocket (api.md mục 4.9) SAU commit để không bao giờ đẩy tin chưa lưu. Tin của khách → mọi nhân viên;
 * tin của nhân viên → đúng khách đó (nhân viên khác thấy tin trả lời khi tải lại hội thoại bằng REST). Người gửi nhận tin của
 * mình qua phản hồi REST, KHÔNG nhận lại qua WebSocket (tránh trùng). `RealtimeSender` không ném lỗi nên đẩy hỏng không làm
 * mất tin đã lưu; client đồng bộ lại bằng REST.
 */
@Component
public class SupportRealtimeListener {

    private final RealtimeSender sender;

    public SupportRealtimeListener(RealtimeSender sender) {
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(SupportMessageSentEvent event) {
        SupportMessageResponse message = event.message();
        if (message.isFromStaff()) {
            sender.toUser(message.customerId(), RealtimeDestinations.SUPPORT, message);
        } else {
            sender.toStaff(RealtimeDestinations.SUPPORT, message);
        }
    }
}
