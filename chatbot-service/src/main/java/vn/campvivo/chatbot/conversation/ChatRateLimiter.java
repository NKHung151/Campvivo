package vn.campvivo.chatbot.conversation;

import org.springframework.stereotype.Component;
import vn.campvivo.chatbot.common.config.ChatbotProperties;
import vn.campvivo.chatbot.common.exception.BusinessException;
import vn.campvivo.chatbot.common.exception.ErrorCode;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Giới hạn số tin mỗi khách (`app.chatbot.rate-limit.*`), đếm trên tin USER đã lưu nên đúng cả khi chạy nhiều
 * instance. Vượt → 429 CHAT_RATE_LIMITED + `details.retryAfterSeconds`.
 */
@Component
public class ChatRateLimiter {

    private final MessageRepository messages;
    private final ChatbotProperties props;
    private final Clock clock;

    public ChatRateLimiter(MessageRepository messages, ChatbotProperties props, Clock clock) {
        this.messages = messages;
        this.props = props;
        this.clock = clock;
    }

    public void check(long userId) {
        var limit = props.chatbot().rateLimit();
        OffsetDateTime now = OffsetDateTime.now(clock);
        check(userId, now, Duration.ofMinutes(1), limit.perMinute(),
                "Bạn gửi tin nhắn quá nhanh. Vui lòng đợi một chút rồi thử lại.");
        check(userId, now, Duration.ofDays(1), limit.perDay(),
                "Bạn đã dùng hết lượt hỏi trợ lý trong hôm nay. Vui lòng quay lại sau.");
    }

    private void check(long userId, OffsetDateTime now, Duration window, int max, String message) {
        OffsetDateTime since = now.minus(window);
        if (messages.countUserMessagesSince(userId, since) < max) {
            return;
        }
        OffsetDateTime oldestCounted = messages.nthLatestUserMessageSince(userId, since, max);
        long retryAfter = oldestCounted == null ? window.toSeconds()
                : Math.max(1, Duration.between(now, oldestCounted.plus(window)).toSeconds());
        throw new BusinessException(ErrorCode.CHAT_RATE_LIMITED, message, Map.of("retryAfterSeconds", retryAfter));
    }
}
