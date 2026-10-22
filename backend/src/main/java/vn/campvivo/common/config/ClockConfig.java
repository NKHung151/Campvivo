package vn.campvivo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Nguồn thời gian duy nhất của hệ thống. Code nghiệp vụ inject Clock, KHÔNG gọi now() trực tiếp,
 * để test OTP hết hạn, đơn quá 15 phút, hạn trả hàng bằng cách thay Clock (không dùng sleep).
 * Múi giờ Asia/Ho_Chi_Minh theo api.md mục 1.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
