package vn.campvivo.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật job định kỳ (OrderTimeoutJob — BR-3) và xử lý bất đồng bộ cho side-effect sau commit (tạo vận đơn GHN, sau này
 * thông báo/email — D-15). Dùng executor mặc định của Spring Boot (spring.task.execution.*).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {
}
