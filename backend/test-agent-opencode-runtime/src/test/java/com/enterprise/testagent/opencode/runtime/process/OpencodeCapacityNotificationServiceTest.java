package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.enterprise.testagent.domain.opencodeprocess.ManagerRuntimeSnapshot;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainer;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserManagementQuery;
import com.enterprise.testagent.domain.user.UserManagementQueryRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证单节点容量预警的 80% 触发、70% 恢复滞回和通知失败隔离。 */
class OpencodeCapacityNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-24T02:00:00Z");
    private static final String TRACE_ID = "trace_capacity_warning";
    private static final UserId ACTIVE_ADMIN = new UserId("usr_active_admin");
    private static final UserId SECOND_PAGE_ADMIN = new UserId("usr_second_page_admin");
    private static final UserId INACTIVE_ADMIN = new UserId("usr_inactive_admin");

    private UserManagementQueryRepository userQueryRepository;
    private UserNotificationApplicationService notificationService;
    private OpencodeCapacityNotificationService service;

    @BeforeEach
    void setUp() {
        userQueryRepository = org.mockito.Mockito.mock(UserManagementQueryRepository.class);
        notificationService = org.mockito.Mockito.mock(UserNotificationApplicationService.class);
        service = new OpencodeCapacityNotificationService(userQueryRepository, notificationService);
        when(userQueryRepository.findPage(any(), eq(new PageRequest(1, PageRequest.MAX_SIZE))))
                .thenReturn(new PageResponse<>(
                        List.of(user(ACTIVE_ADMIN, UserStatus.ACTIVE), user(INACTIVE_ADMIN, UserStatus.INACTIVE)),
                        1,
                        PageRequest.MAX_SIZE,
                        2));
    }

    @Test
    void reachingEightyPercentNotifiesOnlyActiveSuperAdmins() {
        service.reconcile(snapshot(79), snapshot(80), TRACE_ID);

        ArgumentCaptor<UserManagementQuery> query = ArgumentCaptor.forClass(UserManagementQuery.class);
        verify(userQueryRepository).findPage(query.capture(), eq(new PageRequest(1, PageRequest.MAX_SIZE)));
        assertThat(query.getValue().role()).isEqualTo(Dictionary.ROLE_SUPER_ADMIN);
        verify(notificationService).syncOpencodeCapacityWarning(
                ACTIVE_ADMIN,
                new LinuxServerId("server-a"),
                new OpencodeContainerId("container-a"),
                100,
                80,
                TRACE_ID);
        verify(notificationService, never()).syncOpencodeCapacityWarning(
                eq(INACTIVE_ADMIN), any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    void reachingEightyPercentReadsEverySuperAdminPage() {
        when(userQueryRepository.findPage(any(), eq(new PageRequest(1, PageRequest.MAX_SIZE))))
                .thenReturn(new PageResponse<>(
                        List.of(user(ACTIVE_ADMIN, UserStatus.ACTIVE)),
                        1,
                        PageRequest.MAX_SIZE,
                        PageRequest.MAX_SIZE + 1L));
        when(userQueryRepository.findPage(any(), eq(new PageRequest(2, PageRequest.MAX_SIZE))))
                .thenReturn(new PageResponse<>(
                        List.of(user(SECOND_PAGE_ADMIN, UserStatus.ACTIVE)),
                        2,
                        PageRequest.MAX_SIZE,
                        PageRequest.MAX_SIZE + 1L));

        service.reconcile(snapshot(79), snapshot(80), TRACE_ID);

        verify(notificationService).syncOpencodeCapacityWarning(
                eq(ACTIVE_ADMIN), any(), any(), anyInt(), anyInt(), eq(TRACE_ID));
        verify(notificationService).syncOpencodeCapacityWarning(
                eq(SECOND_PAGE_ADMIN), any(), any(), anyInt(), anyInt(), eq(TRACE_ID));
    }

    @Test
    void highBandHeartbeatDoesNotRepeatAndSeventyPercentHysteresisKeepsWarning() {
        service.reconcile(snapshot(79), snapshot(80), TRACE_ID);
        service.reconcile(snapshot(80), snapshot(95), TRACE_ID);
        service.reconcile(snapshot(85), snapshot(75), TRACE_ID);

        verify(userQueryRepository).findPage(any(), any());
        verify(notificationService).syncOpencodeCapacityWarning(
                eq(ACTIVE_ADMIN), any(), any(), anyInt(), anyInt(), any());
        verify(notificationService, never()).invalidateOpencodeCapacityWarning(any(), any());
    }

    @Test
    void firstHighObservationAfterJavaRestartConvergesWarningDespiteHighRedisSnapshot() {
        service.reconcile(snapshot(95), snapshot(96), TRACE_ID);

        verify(notificationService).syncOpencodeCapacityWarning(
                eq(ACTIVE_ADMIN), any(), any(), anyInt(), anyInt(), eq(TRACE_ID));
    }

    @Test
    void firstLowObservationAfterJavaRestartConvergesStaleWarningInvalidation() {
        service.reconcile(snapshot(60), snapshot(60), TRACE_ID);

        verify(notificationService).invalidateOpencodeCapacityWarning(
                new OpencodeContainerId("container-a"), TRACE_ID);
    }

    @Test
    void fallingBelowSeventyPercentInvalidatesContainerWarning() {
        service.reconcile(snapshot(75), snapshot(69), TRACE_ID);

        verify(notificationService).invalidateOpencodeCapacityWarning(
                new OpencodeContainerId("container-a"), TRACE_ID);
        verify(userQueryRepository, never()).findPage(any(), any());
    }

    @Test
    void notificationFailureDoesNotBreakManagerHeartbeatPath() {
        when(userQueryRepository.findPage(any(), any()))
                .thenThrow(new IllegalStateException("notification database unavailable"));

        assertThatCode(() -> service.reconcile(null, snapshot(80), TRACE_ID))
                .doesNotThrowAnyException();
    }

    private ManagerRuntimeSnapshot snapshot(int currentProcesses) {
        OpencodeContainerId containerId = new OpencodeContainerId("container-a");
        LinuxServerId linuxServerId = new LinuxServerId("server-a");
        OpencodeContainer container = new OpencodeContainer(
                containerId,
                linuxServerId,
                "worker-a",
                14096,
                15095,
                100,
                currentProcesses,
                OpencodeContainerStatus.READY,
                NOW,
                NOW,
                NOW,
                TRACE_ID);
        OpencodeContainerManager manager = new OpencodeContainerManager(
                new ContainerManagerId("mgr_1234567890abcdef"),
                containerId,
                linuxServerId,
                "opencode-manager.v1",
                ManagerConnectionStatus.CONNECTED,
                Map.of(),
                NOW,
                NOW,
                NOW,
                TRACE_ID);
        return new ManagerRuntimeSnapshot(container, manager, List.of());
    }

    private User user(UserId userId, UserStatus status) {
        return new User(
                userId,
                "ucid-" + userId.value(),
                "用户" + userId.value(),
                "password-hash",
                null,
                null,
                null,
                status,
                NOW,
                NOW);
    }
}
