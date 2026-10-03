package vn.campvivo.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import vn.campvivo.support.PostgresTestContainerConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test tích hợp bắt buộc theo .agents/rules/testing.md: chứng minh OrderService.createOrder
 * (vn.campvivo.order.OrderService) không bán vượt tồn kho khi nhiều request đặt hàng song song
 * cùng 1 sản phẩm còn ít hàng.
 *
 * Đây là KHUNG test — cần hoàn thiện phần setup dữ liệu (tạo user, product, cart) khi các
 * module auth/catalog/cart có đủ Repository/Service thật.
 */
@SpringBootTest
@ContextConfiguration(initializers = PostgresTestContainerConfig.class)
class OrderServiceIT {

    @Autowired
    private OrderService orderService;

    @Test
    void khong_duoc_ban_vuot_ton_kho_khi_dat_hang_song_song() throws InterruptedException {
        // TODO: seed 1 product với stockQuantity = 5, 10 user/cart khác nhau mỗi cart có
        // 1 sản phẩm đó với quantity = 1.
        int numberOfConcurrentRequests = 10;
        int stockQuantity = 5;

        ExecutorService executor = Executors.newFixedThreadPool(numberOfConcurrentRequests);
        CountDownLatch latch = new CountDownLatch(numberOfConcurrentRequests);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < numberOfConcurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    // TODO: orderService.createOrder(userId_i, request_voi_cartId_i);
                    successCount.incrementAndGet();
                } catch (OutOfStockException ignored) {
                    // Mong đợi: 1 số request bị từ chối vì hết hàng — đây là hành vi ĐÚNG.
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Số đơn tạo thành công không được vượt quá tồn kho ban đầu.
        assertThat(successCount.get()).isLessThanOrEqualTo(stockQuantity);
    }
}
