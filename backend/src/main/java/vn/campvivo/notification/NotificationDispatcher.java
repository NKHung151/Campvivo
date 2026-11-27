package vn.campvivo.notification;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Hàng đợi MỘT luồng cho việc tạo thông báo (architecture.md "Sự kiện & side-effect"). Listener `AFTER_COMMIT` chỉ gửi việc vào
 * đây rồi trả về ngay, vì:
 * <ul>
 *   <li>trong `AFTER_COMMIT` luồng nghiệp vụ VẪN giữ kết nối DB của transaction gốc; ghi thông báo ngay tại đó (transaction mới)
 *       cần kết nối thứ hai → nhiều yêu cầu đồng thời làm cạn pool và treo;</li>
 *   <li>một luồng, vào trước làm trước: sự kiện sau (vd. nhân viên trả lời) không bao giờ được xử lý trước sự kiện trước nó (khách
 *       nhắn tin) — nếu không, "đã xử lý" có thể chạy trước "tạo" và thông báo bị kẹt ở trạng thái chưa xử lý.</li>
 * </ul>
 * Không phải bean `Executor` (để không thay executor mặc định của `@Async`). Việc còn trong hàng đợi lúc tắt máy được chờ tối đa
 * {@link #SHUTDOWN_WAIT}; quá hạn thì mất (thông báo không phải dữ liệu nghiệp vụ). Lỗi của từng việc chỉ ghi log.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(10);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notification-dispatcher");
        t.setDaemon(true);
        return t;
    });

    /** Gửi một việc vào hàng đợi; `what` chỉ để ghi log khi lỗi. */
    public void submit(String what, Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("Không tạo được thông báo ({})", what, e);
            }
        });
    }

    /**
     * Chờ mọi việc đã gửi TRƯỚC lời gọi này chạy xong (một luồng, vào trước làm trước). Dùng trong test để kiểm tra kết quả.
     * Trả `false` nếu quá `timeout`.
     */
    public boolean drain(Duration timeout) {
        try {
            executor.submit(() -> { }).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(SHUTDOWN_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
            log.warn("Tắt máy khi còn thông báo chưa tạo xong — bỏ qua phần còn lại");
            executor.shutdownNow();
        }
    }
}
