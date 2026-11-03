package vn.campvivo.common.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import vn.campvivo.user.Role;
import vn.campvivo.user.UserRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Hành vi của {@link StompRealtimeSender} không cần dựng ứng dụng: lỗi gửi không lan ra, chỉ gửi cho nhân viên đang online. */
class StompRealtimeSenderTest {

    private SimpMessagingTemplate template;
    private SimpUserRegistry registry;
    private UserRepository users;
    private StompRealtimeSender sender;

    @BeforeEach
    void setUp() {
        template = mock(SimpMessagingTemplate.class);
        registry = mock(SimpUserRegistry.class);
        users = mock(UserRepository.class);
        sender = new StompRealtimeSender(template, registry, users);
    }

    @Test
    void toUserSendsToTheStringifiedUserId() {
        sender.toUser(42L, RealtimeDestinations.SUPPORT, Map.of("a", 1));

        verify(template).convertAndSendToUser("42", "/queue/support", Map.of("a", 1));
    }

    @Test
    void aSendFailureIsLoggedNotThrown() {
        doThrow(new MessageDeliveryException("broker lỗi")).when(template)
                .convertAndSendToUser(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> sender.toUser(1L, RealtimeDestinations.NOTIFICATIONS, "x")).doesNotThrowAnyException();
    }

    @Test
    void aFailureLookingUpStaffIsLoggedNotThrown() {
        when(users.findActiveIdsByRoleIn(any())).thenThrow(new IllegalStateException("db"));

        assertThatCode(() -> sender.toStaff(RealtimeDestinations.SUPPORT, "x")).doesNotThrowAnyException();
    }

    @Test
    void toStaffOnlySendsToActiveEmployeesAndAdminsWhoAreConnected() {
        when(users.findActiveIdsByRoleIn(List.of(Role.EMPLOYEE, Role.ADMIN))).thenReturn(List.of(1L, 2L, 3L));
        when(registry.getUser("1")).thenReturn(mock(SimpUser.class));
        when(registry.getUser("2")).thenReturn(null); // offline
        when(registry.getUser("3")).thenReturn(mock(SimpUser.class));

        sender.toStaff(RealtimeDestinations.SUPPORT, "tin");

        verify(template).convertAndSendToUser(eq("1"), eq("/queue/support"), eq("tin"));
        verify(template).convertAndSendToUser(eq("3"), eq("/queue/support"), eq("tin"));
        verify(template, never()).convertAndSendToUser(eq("2"), anyString(), any(Object.class));
    }

    @Test
    void anUnknownDestinationIsRejectedBeforeAnythingIsSent() {
        assertThatThrownBy(() -> sender.toUser(1L, "/queue/typo", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sender.toUser(1L, null, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sender.toStaff("/topic/all", "x")).isInstanceOf(IllegalArgumentException.class);

        Mockito.verifyNoInteractions(template);
        assertThat(RealtimeDestinations.isSubscribable("/user/queue/support")).isTrue();
        assertThat(RealtimeDestinations.isSubscribable("/queue/support")).isFalse();
    }
}
