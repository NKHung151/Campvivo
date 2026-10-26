package vn.campvivo.order;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Mã đơn `ORD-yyyyMMdd-XXXXXX` (database.md 3.9; 6 ký tự A-Z0-9 ngẫu nhiên, sinh lại nếu trùng).
 * Mã này cũng là `vnp_TxnRef` (duy nhất, mỗi đơn chỉ thanh toán một lần) và `client_order_code` của GHN.
 */
@Component
public class OrderCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SecureRandom random = new SecureRandom();
    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderCodeGenerator(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    public String next() {
        String prefix = "ORD-" + LocalDate.now(clock).format(DAY) + "-";
        String code;
        do {
            StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < 6; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = sb.toString();
        } while (orderRepository.existsByOrderCode(code));
        return code;
    }
}
