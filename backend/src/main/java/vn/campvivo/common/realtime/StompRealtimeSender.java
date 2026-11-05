package vn.campvivo.common.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import vn.campvivo.user.Role;
import vn.campvivo.user.UserRepository;

import java.util.List;

/**
 * Hiện thực {@link RealtimeSender} bằng STOMP simple broker trong bộ nhớ (chỉ đúng khi chạy MỘT instance backend). Principal
 * của kết nối là userId nên `convertAndSendToUser(String.valueOf(userId), ...)` tới đúng người. Không log nội dung tin.
 */
@Service
public class StompRealtimeSender implements RealtimeSender {

    private static final Logger log = LoggerFactory.getLogger(StompRealtimeSender.class);
    private static final List<Role> STAFF_ROLES = List.of(Role.EMPLOYEE, Role.ADMIN);

    private final SimpMessagingTemplate template;
    private final SimpUserRegistry userRegistry;
    private final UserRepository userRepository;

    public StompRealtimeSender(SimpMessagingTemplate template, SimpUserRegistry userRegistry,
                               UserRepository userRepository) {
        this.template = template;
        this.userRegistry = userRegistry;
        this.userRepository = userRepository;
    }

    @Override
    public void toUser(Long userId, String destination, Object payload) {
        requireKnownDestination(destination);
        try {
            template.convertAndSendToUser(String.valueOf(userId), destination, payload);
        } catch (RuntimeException e) {
            log.warn("Không đẩy được tin realtime tới user {} ({}): {}", userId, destination, e.getClass().getSimpleName());
        }
    }

    @Override
    public void toStaff(String destination, Object payload) {
        toStaffExcept(null, destination, payload);
    }

    @Override
    public void toStaffExcept(Long excludedUserId, String destination, Object payload) {
        requireKnownDestination(destination);
        try {
            for (Long staffId : userRepository.findActiveIdsByRoleIn(STAFF_ROLES)) {
                if (staffId.equals(excludedUserId)) {
                    continue;
                }
                // Chỉ gửi cho người đang kết nối: tránh gọi broker vô ích cho nhân viên đang offline.
                if (userRegistry.getUser(String.valueOf(staffId)) != null) {
                    toUser(staffId, destination, payload);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Không đẩy được tin realtime tới nhân viên ({}): {}", destination, e.getClass().getSimpleName());
        }
    }

    private static void requireKnownDestination(String destination) {
        if (!RealtimeDestinations.isServerDestination(destination)) {
            throw new IllegalArgumentException("Kênh realtime không hợp lệ: " + destination);
        }
    }
}
