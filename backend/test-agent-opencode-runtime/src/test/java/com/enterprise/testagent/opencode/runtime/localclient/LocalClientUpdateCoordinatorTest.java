package com.enterprise.testagent.opencode.runtime.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.localclient.LocalClientUpdateDirection;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientProcessStatus;
import com.enterprise.testagent.domain.localclient.LocalClientVersionModels;
import com.enterprise.testagent.domain.localclient.LocalClientVersionRepository;
import com.enterprise.testagent.domain.notification.UserNotification;
import com.enterprise.testagent.domain.notification.UserNotificationActionType;
import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.notification.UserNotificationRepository;
import com.enterprise.testagent.domain.notification.UserNotificationStatus;
import com.enterprise.testagent.domain.notification.UserNotificationType;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.notification.LocalClientUpdateNotificationInvalidationService;
import com.enterprise.testagent.notification.UserNotificationApplicationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 验证版本切换只作用于当下仍在线且通过代次校验的本地客户端。 */
class LocalClientUpdateCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private static final UserId USER_ID = new UserId("usr_local_update_test");
    private static final LocalClientInstanceId INSTANCE_ID =
            new LocalClientInstanceId("lci_local_update_test");
    private static final UserNotificationId NOTIFICATION_ID =
            new UserNotificationId("ntf_local_update_test");
    private static final String TARGET_VERSION = "20260820190000";

    @Test
    void preparedHandlerKeepsNetworkSendOutsideTransactionProxy() throws NoSuchMethodException {
        AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();

        assertThat(attributes.getTransactionAttribute(
                        LocalClientUpdateCoordinator.class.getMethod(
                                "handlePrepared",
                                UserId.class,
                                LocalClientInstanceId.class,
                                long.class,
                                LocalClientPayloads.UpdatePrepared.class,
                                String.class),
                        LocalClientUpdateCoordinator.class))
                .as("UPDATE_APPLY/UPDATE_CANCEL 网络发送前不应保留方法级事务")
                .isNull();
    }

    @Test
    void userRequestedUpdateRejectsInstanceThatWentOfflineAfterNotification() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        UserNotificationRepository notificationRepository = mock(UserNotificationRepository.class);
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, "20260820180000")));
        when(notificationRepository.findByIdForRecipient(NOTIFICATION_ID, USER_ID))
                .thenReturn(Optional.of(updateNotification()));
        when(versions.findGlobalPolicy()).thenReturn(Optional.of(new LocalClientVersionModels.GlobalPolicy(
                TARGET_VERSION, 7, USER_ID, NOW)));
        when(versions.findUserPolicy(USER_ID)).thenReturn(Optional.empty());
        when(versions.findRelease(TARGET_VERSION)).thenReturn(Optional.of(release("a".repeat(64))));
        when(connections.find(INSTANCE_ID)).thenReturn(Optional.empty());

        LocalClientUpdateCoordinator coordinator = coordinator(
                versions, instances, connections, notificationRepository);

        assertThatThrownBy(() -> coordinator.createUserRequestedUpdate(
                        USER_ID,
                        INSTANCE_ID,
                        NOTIFICATION_ID,
                        TARGET_VERSION,
                        "trace_user_update"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void userRequestedUpdateInvalidatesClickedNotificationWhenGenerationRaces() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        UserNotificationRepository notificationRepository = mock(UserNotificationRepository.class);
        LocalClientUpdateNotificationInvalidationService invalidation =
                mock(LocalClientUpdateNotificationInvalidationService.class);
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, "20260820180000")));
        when(notificationRepository.findByIdForRecipient(NOTIFICATION_ID, USER_ID))
                .thenReturn(Optional.of(updateNotification()));
        when(versions.findGlobalPolicy()).thenReturn(Optional.of(new LocalClientVersionModels.GlobalPolicy(
                TARGET_VERSION, 7, USER_ID, NOW)));
        when(versions.findUserPolicy(USER_ID)).thenReturn(Optional.empty());
        when(versions.findRelease(TARGET_VERSION)).thenReturn(Optional.of(release("a".repeat(64))));
        when(connections.find(INSTANCE_ID)).thenReturn(
                Optional.of(route(INSTANCE_ID, USER_ID, 7)),
                Optional.of(route(INSTANCE_ID, USER_ID, 8)));

        LocalClientUpdateCoordinator coordinator = coordinator(
                versions,
                instances,
                connections,
                notificationRepository,
                new LocalClientConnectionRegistry(),
                mock(UserNotificationApplicationService.class),
                invalidation);

        assertThatThrownBy(() -> coordinator.createUserRequestedUpdate(
                        USER_ID, INSTANCE_ID, NOTIFICATION_ID, TARGET_VERSION, "trace_generation_race"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(invalidation).invalidate(
                USER_ID, NOTIFICATION_ID, "OFFLINE_OR_STALE_GENERATION", "trace_generation_race");
    }

    @Test
    void stalePolicyInvalidatesOnlyClickedNotificationThroughIndependentBoundary() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        UserNotificationRepository notificationRepository = mock(UserNotificationRepository.class);
        LocalClientUpdateNotificationInvalidationService invalidation =
                mock(LocalClientUpdateNotificationInvalidationService.class);
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, "20260820180000")));
        when(notificationRepository.findByIdForRecipient(NOTIFICATION_ID, USER_ID))
                .thenReturn(Optional.of(updateNotification()));
        when(versions.findGlobalPolicy()).thenReturn(Optional.of(new LocalClientVersionModels.GlobalPolicy(
                "20260820200000", 8, USER_ID, NOW)));
        when(versions.findUserPolicy(USER_ID)).thenReturn(Optional.empty());

        LocalClientUpdateCoordinator coordinator = coordinator(
                versions,
                instances,
                mock(LocalClientConnectionStore.class),
                notificationRepository,
                new LocalClientConnectionRegistry(),
                mock(UserNotificationApplicationService.class),
                invalidation);

        assertThatThrownBy(() -> coordinator.createUserRequestedUpdate(
                        USER_ID, INSTANCE_ID, NOTIFICATION_ID, TARGET_VERSION, "trace_stale_policy"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(invalidation).invalidate(USER_ID, NOTIFICATION_ID, "STALE_POLICY", "trace_stale_policy");
    }

    @Test
    void allOnlineRolloutSnapshotsOnlyCapableInstancesAndUsesUserOverride() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientInstanceId legacyId = new LocalClientInstanceId("lci_local_update_legacy");
        LocalClientInstance capable = instance(true, "20260820180000");
        LocalClientInstance legacy = instance(legacyId, USER_ID, false, "0.1.0");
        when(instances.findAll()).thenReturn(List.of(capable, legacy));
        when(connections.find(INSTANCE_ID)).thenReturn(Optional.of(route(INSTANCE_ID, USER_ID, 7)));
        when(connections.find(legacyId)).thenReturn(Optional.of(route(legacyId, USER_ID, 9)));
        when(versions.findGlobalPolicy()).thenReturn(Optional.of(new LocalClientVersionModels.GlobalPolicy(
                "20260820200000", 6, USER_ID, NOW)));
        when(versions.findUserPolicy(USER_ID)).thenReturn(Optional.of(new LocalClientVersionModels.UserPolicy(
                USER_ID, "20260820170000", 8, USER_ID, NOW)));
        when(versions.findRelease("20260820170000"))
                .thenReturn(Optional.of(release("20260820170000", "linux", "arm64", "a".repeat(64))));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LocalClientVersionModels.Attempt>> attempts = ArgumentCaptor.forClass(List.class);

        LocalClientUpdateCoordinator.RolloutResult result = coordinator(
                versions,
                instances,
                connections,
                mock(UserNotificationRepository.class))
                .createRollout(
                        LocalClientVersionModels.RolloutScope.ALL_ONLINE,
                        null,
                        USER_ID,
                        "trace_all_online");

        verify(versions).insertAttempts(attempts.capture());
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(attempts.getValue()).singleElement().satisfies(attempt -> {
            assertThat(attempt.clientInstanceId()).isEqualTo(INSTANCE_ID);
            assertThat(attempt.policyRevision()).isEqualTo(8);
            assertThat(attempt.targetVersion()).isEqualTo("20260820170000");
            assertThat(attempt.direction()).isEqualTo(LocalClientUpdateDirection.ROLLBACK);
        });
    }

    @Test
    void allOnlineRolloutDoesNotSendWindowsReleaseToLinuxInstance() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        when(instances.findAll()).thenReturn(List.of(instance(true, "20260820180000")));
        when(connections.find(INSTANCE_ID)).thenReturn(Optional.of(route(INSTANCE_ID, USER_ID, 7)));
        when(versions.findGlobalPolicy()).thenReturn(Optional.of(new LocalClientVersionModels.GlobalPolicy(
                TARGET_VERSION, 9, USER_ID, NOW)));
        when(versions.findUserPolicy(USER_ID)).thenReturn(Optional.empty());
        when(versions.findRelease(TARGET_VERSION))
                .thenReturn(Optional.of(release(TARGET_VERSION, "windows", "x64", "a".repeat(64))));

        LocalClientUpdateCoordinator.RolloutResult result = coordinator(
                versions, instances, connections, mock(UserNotificationRepository.class))
                .createRollout(
                        LocalClientVersionModels.RolloutScope.ALL_ONLINE,
                        null,
                        USER_ID,
                        "trace_cross_platform");

        assertThat(result.attemptCount()).isZero();
        verify(versions).insertAttempts(List.of());
    }

    @Test
    void versionCheckCannotSendPolicyWhenPersistedInstanceHasNoSelfUpdateCapability() {
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientConnectionRegistry registry = new LocalClientConnectionRegistry();
        List<LocalClientFrame> sent = new CopyOnWriteArrayList<>();
        registry.register(INSTANCE_ID, USER_ID, 7, "grant-fingerprint", sender(sent));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(false, "0.1.0")));

        assertThatThrownBy(() -> coordinator(
                        versions,
                        instances,
                        mock(LocalClientConnectionStore.class),
                        mock(UserNotificationRepository.class),
                        registry)
                .handleVersionCheck(
                        USER_ID,
                        INSTANCE_ID,
                        7,
                        new LocalClientPayloads.VersionCheck(
                                INSTANCE_ID.value(), "0.1.0", null, "2.0.18", List.of(), NOW),
                        "trace_legacy_version_check"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(sent).isEmpty();
        verifyNoInteractions(versions);
    }

    @Test
    void versionCheckWithoutConfiguredPolicyKeepsConnectionOpenWithoutSendingEmptyPolicy() {
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientConnectionRegistry registry = new LocalClientConnectionRegistry();
        UserNotificationApplicationService notifications = mock(UserNotificationApplicationService.class);
        List<LocalClientFrame> sent = new CopyOnWriteArrayList<>();
        registry.register(INSTANCE_ID, USER_ID, 7, "grant-fingerprint", sender(sent));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, "20260823213628")));
        when(versions.findGlobalPolicy()).thenReturn(Optional.empty());
        when(versions.findUserPolicy(USER_ID)).thenReturn(Optional.empty());

        coordinator(
                versions,
                instances,
                mock(LocalClientConnectionStore.class),
                mock(UserNotificationRepository.class),
                registry,
                notifications)
                .handleVersionCheck(
                        USER_ID,
                        INSTANCE_ID,
                        7,
                        new LocalClientPayloads.VersionCheck(
                                INSTANCE_ID.value(), "20260823213628", "1", "2.0.18",
                                List.of("SELF_UPDATE_V1"), NOW),
                        "trace_no_version_policy");

        assertThat(sent).isEmpty();
        verify(notifications).invalidateLocalClientUpdate(
                USER_ID, INSTANCE_ID.value(), "POLICY_SATISFIED", "trace_no_version_policy");
    }

    @Test
    void compensationCancelsAttemptWhenOnlineGenerationChanged() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientVersionModels.Attempt attempt = attempt(
                LocalClientVersionModels.AttemptStatus.PENDING, null, null);
        when(versions.findDispatchableAttempts(100, 0, NOW)).thenReturn(List.of(attempt));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, attempt.currentVersion())));
        when(connections.find(INSTANCE_ID)).thenReturn(Optional.of(route(INSTANCE_ID, USER_ID, 8)));
        when(versions.transitionAttempt(
                attempt.commandId(), "PENDING", "CANCELLED", null, "STALE_GENERATION", NOW))
                .thenReturn(true);
        stubRolloutLock(versions, attempt.rolloutId(), LocalClientVersionModels.RolloutStatus.RUNNING);

        coordinator(versions, instances, connections,
                mock(UserNotificationRepository.class)).compensatePendingAttempts();

        verify(versions).transitionAttempt(
                attempt.commandId(), "PENDING", "CANCELLED", null, "STALE_GENERATION", NOW);
    }

    @Test
    void compensationTerminalMutationRunsInsideActualTransaction() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientVersionModels.Attempt attempt = attempt(
                LocalClientVersionModels.AttemptStatus.PENDING, null, null);
        AtomicBoolean transactionObserved = new AtomicBoolean();
        when(versions.findDispatchableAttempts(100, 0, NOW)).thenReturn(List.of(attempt));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, attempt.currentVersion())));
        when(connections.find(INSTANCE_ID)).thenReturn(Optional.of(route(INSTANCE_ID, USER_ID, 8)));
        when(versions.transitionAttempt(
                attempt.commandId(), "PENDING", "CANCELLED", null, "STALE_GENERATION", NOW))
                .thenAnswer(ignored -> {
                    transactionObserved.set(TransactionSynchronizationManager.isActualTransactionActive());
                    return true;
                });
        stubRolloutLock(versions, attempt.rolloutId(), LocalClientVersionModels.RolloutStatus.RUNNING);

        coordinator(versions, instances, connections,
                mock(UserNotificationRepository.class)).compensatePendingAttempts();

        assertThat(transactionObserved).isTrue();
    }

    @Test
    void compensationScanAndConnectionLookupStayOutsideTerminalTransaction() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientVersionModels.Attempt attempt = attempt(
                LocalClientVersionModels.AttemptStatus.PENDING, null, null);
        when(versions.findDispatchableAttempts(100, 0, NOW)).thenAnswer(ignored -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return List.of(attempt);
        });
        when(connections.find(INSTANCE_ID)).thenAnswer(ignored -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Optional.of(route(INSTANCE_ID, USER_ID, 8));
        });
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, attempt.currentVersion())));
        when(versions.transitionAttempt(
                attempt.commandId(), "PENDING", "CANCELLED", null, "STALE_GENERATION", NOW))
                .thenReturn(true);
        stubRolloutLock(versions, attempt.rolloutId(), LocalClientVersionModels.RolloutStatus.RUNNING);
        LocalClientUpdateCoordinator coordinator = coordinator(
                versions, instances, connections, mock(UserNotificationRepository.class));

        coordinator.compensatePendingAttempts();

        verify(versions).transitionAttempt(
                attempt.commandId(), "PENDING", "CANCELLED", null, "STALE_GENERATION", NOW);
    }

    @Test
    void compensationCancelsAttemptWhenRegisteredInstanceLostSelfUpdateCapability() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientVersionModels.Attempt attempt = attempt(
                LocalClientVersionModels.AttemptStatus.PENDING, null, null);
        when(versions.findDispatchableAttempts(100, 0, NOW)).thenReturn(List.of(attempt));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(false, attempt.currentVersion())));
        when(connections.find(INSTANCE_ID)).thenReturn(Optional.of(route(INSTANCE_ID, USER_ID, 7)));
        when(versions.transitionAttempt(
                attempt.commandId(), "PENDING", "CANCELLED", null, "CAPABILITY_UNAVAILABLE", NOW))
                .thenReturn(true);
        stubRolloutLock(versions, attempt.rolloutId(), LocalClientVersionModels.RolloutStatus.RUNNING);

        coordinator(versions, instances, connections,
                mock(UserNotificationRepository.class)).compensatePendingAttempts();

        verify(versions).transitionAttempt(
                attempt.commandId(), "PENDING", "CANCELLED", null, "CAPABILITY_UNAVAILABLE", NOW);
        verify(versions, never()).transitionAttempt(
                attempt.commandId(), "PENDING", "SENT", null, null, NOW);
    }

    @Test
    void offlineAttemptRetriesWithinDeadlineAndTimesOutWithoutStarvingLaterAttempts() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientVersionModels.Attempt recentOffline = attempt(
                "lcuc_recent_offline", INSTANCE_ID, LocalClientVersionModels.AttemptStatus.PENDING,
                NOW.minusSeconds(60), NOW.minusSeconds(60));
        LocalClientInstanceId expiredId = new LocalClientInstanceId("lci_local_update_expired");
        LocalClientVersionModels.Attempt expiredOffline = attempt(
                "lcuc_expired_offline", expiredId, LocalClientVersionModels.AttemptStatus.PENDING,
                NOW.minusSeconds(1900), NOW.minusSeconds(1900));
        LocalClientInstanceId laterId = new LocalClientInstanceId("lci_local_update_later");
        LocalClientVersionModels.Attempt laterOnline = attempt(
                "lcuc_later_online", laterId, LocalClientVersionModels.AttemptStatus.PENDING,
                NOW.minusSeconds(30), NOW.minusSeconds(30));
        when(versions.findDispatchableAttempts(100, 0, NOW)).thenReturn(List.of(
                expiredOffline, recentOffline, laterOnline));
        when(versions.findRelease(TARGET_VERSION)).thenReturn(Optional.of(release("a".repeat(64))));
        when(instances.findById(expiredId)).thenReturn(Optional.of(instance(
                expiredId, USER_ID, true, expiredOffline.currentVersion())));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(
                INSTANCE_ID, USER_ID, true, recentOffline.currentVersion())));
        when(instances.findById(laterId)).thenReturn(Optional.of(instance(
                laterId, USER_ID, true, laterOnline.currentVersion())));
        when(connections.find(expiredId)).thenReturn(Optional.empty());
        when(connections.find(INSTANCE_ID)).thenReturn(Optional.empty());
        when(connections.find(laterId)).thenReturn(Optional.of(route(laterId, USER_ID, 7)));
        when(versions.transitionAttempt(
                expiredOffline.commandId(), "PENDING", "FAILED", null, "DELIVERY_DEADLINE_EXCEEDED", NOW))
                .thenReturn(true);
        when(versions.transitionAttempt(laterOnline.commandId(), "PENDING", "SENT", null, null, NOW))
                .thenReturn(true);
        stubRolloutLock(versions, expiredOffline.rolloutId(), LocalClientVersionModels.RolloutStatus.RUNNING);

        coordinator(versions, instances, connections,
                mock(UserNotificationRepository.class)).compensatePendingAttempts();

        verify(versions).transitionAttempt(
                expiredOffline.commandId(), "PENDING", "FAILED", null, "DELIVERY_DEADLINE_EXCEEDED", NOW);
        verify(versions, never()).transitionAttempt(
                recentOffline.commandId(), "PENDING", "FAILED", null, "DELIVERY_DEADLINE_EXCEEDED", NOW);
        verify(versions).transitionAttempt(laterOnline.commandId(), "PENDING", "SENT", null, null, NOW);
    }

    @Test
    void onlineSentAttemptPastDeadlineFailsWithoutResendingAndCompletesRollout() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientConnectionRegistry registry = new LocalClientConnectionRegistry();
        List<LocalClientFrame> sent = new CopyOnWriteArrayList<>();
        registry.register(INSTANCE_ID, USER_ID, 7, "grant-fingerprint", sender(sent));
        LocalClientVersionModels.Attempt expired = attempt(
                "lcuc_online_expired", INSTANCE_ID, LocalClientVersionModels.AttemptStatus.SENT,
                NOW.minusSeconds(1900), NOW.minusSeconds(1800));
        LocalClientVersionModels.Attempt failed = withStatus(
                expired, LocalClientVersionModels.AttemptStatus.FAILED, "DELIVERY_DEADLINE_EXCEEDED");
        when(versions.findDispatchableAttempts(100, 0, NOW)).thenReturn(List.of(expired));
        when(connections.find(INSTANCE_ID)).thenReturn(Optional.of(route(INSTANCE_ID, USER_ID, 7)));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, expired.currentVersion())));
        when(versions.transitionAttempt(
                expired.commandId(), "SENT", "FAILED", null, "DELIVERY_DEADLINE_EXCEEDED", NOW))
                .thenReturn(true);
        when(versions.findRolloutForUpdate(expired.rolloutId())).thenReturn(Optional.of(rollout(
                expired.rolloutId(), LocalClientVersionModels.RolloutStatus.RUNNING)));
        when(versions.findAttemptsByRollout(expired.rolloutId())).thenReturn(List.of(failed));

        coordinator(
                versions,
                instances,
                connections,
                mock(UserNotificationRepository.class),
                registry)
                .compensatePendingAttempts();

        verify(versions).transitionAttempt(
                expired.commandId(), "SENT", "FAILED", null, "DELIVERY_DEADLINE_EXCEEDED", NOW);
        verify(versions).completeRollout(expired.rolloutId(), "PARTIAL_FAILED", NOW);
        assertThat(sent).isEmpty();
    }

    @Test
    void pendingAttemptRecoveredOnlineAfterDeadlineFailsBeforeFirstSend() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientConnectionRegistry registry = new LocalClientConnectionRegistry();
        List<LocalClientFrame> sent = new CopyOnWriteArrayList<>();
        registry.register(INSTANCE_ID, USER_ID, 7, "grant-fingerprint", sender(sent));
        LocalClientVersionModels.Attempt expired = attempt(
                "lcuc_recovered_expired", INSTANCE_ID, LocalClientVersionModels.AttemptStatus.PENDING,
                NOW.minusSeconds(1900), NOW.minusSeconds(1900));
        LocalClientVersionModels.Attempt failed = withStatus(
                expired, LocalClientVersionModels.AttemptStatus.FAILED, "DELIVERY_DEADLINE_EXCEEDED");
        when(versions.findDispatchableAttempts(100, 0, NOW)).thenReturn(List.of(expired));
        when(connections.find(INSTANCE_ID)).thenReturn(Optional.of(route(INSTANCE_ID, USER_ID, 7)));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, expired.currentVersion())));
        when(versions.transitionAttempt(
                expired.commandId(), "PENDING", "FAILED", null, "DELIVERY_DEADLINE_EXCEEDED", NOW))
                .thenReturn(true);
        when(versions.findRolloutForUpdate(expired.rolloutId())).thenReturn(Optional.of(rollout(
                expired.rolloutId(), LocalClientVersionModels.RolloutStatus.RUNNING)));
        when(versions.findAttemptsByRollout(expired.rolloutId())).thenReturn(List.of(failed));

        coordinator(
                versions,
                instances,
                connections,
                mock(UserNotificationRepository.class),
                registry)
                .compensatePendingAttempts();

        verify(versions).transitionAttempt(
                expired.commandId(), "PENDING", "FAILED", null, "DELIVERY_DEADLINE_EXCEEDED", NOW);
        verify(versions, never()).transitionAttempt(
                expired.commandId(), "PENDING", "SENT", null, null, NOW);
        verify(versions).completeRollout(expired.rolloutId(), "PARTIAL_FAILED", NOW);
        assertThat(sent).isEmpty();
    }

    @Test
    void compensationContinuesWithNextPageAfterHundredOfflineAttempts() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        LocalClientVersionModels.Attempt first = attempt(
                "lcuc_page_first", INSTANCE_ID, LocalClientVersionModels.AttemptStatus.PENDING,
                NOW.minusSeconds(60), NOW.minusSeconds(60));
        LocalClientInstanceId laterId = new LocalClientInstanceId("lci_local_update_page_later");
        LocalClientVersionModels.Attempt later = attempt(
                "lcuc_page_later", laterId, LocalClientVersionModels.AttemptStatus.PENDING,
                NOW.minusSeconds(30), NOW.minusSeconds(30));
        when(versions.findDispatchableAttempts(100, 0, NOW))
                .thenReturn(java.util.Collections.nCopies(100, first));
        when(versions.findDispatchableAttempts(100, 100, NOW)).thenReturn(List.of(later));
        when(versions.findRelease(TARGET_VERSION)).thenReturn(Optional.of(release("a".repeat(64))));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(
                INSTANCE_ID, USER_ID, true, first.currentVersion())));
        when(instances.findById(laterId)).thenReturn(Optional.of(instance(
                laterId, USER_ID, true, later.currentVersion())));
        when(connections.find(INSTANCE_ID)).thenReturn(Optional.empty());
        when(connections.find(laterId)).thenReturn(Optional.of(route(laterId, USER_ID, 7)));
        when(versions.transitionAttempt(later.commandId(), "PENDING", "SENT", null, null, NOW))
                .thenReturn(true);

        coordinator(versions, instances, connections,
                mock(UserNotificationRepository.class)).compensatePendingAttempts();

        verify(versions).findDispatchableAttempts(100, 0, NOW);
        verify(versions).findDispatchableAttempts(100, 100, NOW);
        verify(versions).transitionAttempt(later.commandId(), "PENDING", "SENT", null, null, NOW);
    }

    @Test
    void preparedAttemptIsCancelledWhenPolicyChangedAfterDownload() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientConnectionStore connections = mock(LocalClientConnectionStore.class);
        UserNotificationApplicationService notifications = mock(UserNotificationApplicationService.class);
        LocalClientConnectionRegistry registry = new LocalClientConnectionRegistry();
        List<LocalClientFrame> sent = new CopyOnWriteArrayList<>();
        registry.register(INSTANCE_ID, USER_ID, 7, "grant-fingerprint", sender(sent));
        String digest = "a".repeat(64);
        LocalClientVersionModels.Attempt attempt = attempt(
                LocalClientVersionModels.AttemptStatus.PREPARING, null, null);
        when(versions.findAttempt(attempt.commandId())).thenReturn(Optional.of(attempt));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, attempt.currentVersion())));
        when(versions.findRelease(TARGET_VERSION)).thenReturn(Optional.of(release(digest)));
        when(versions.findGlobalPolicy()).thenReturn(Optional.of(new LocalClientVersionModels.GlobalPolicy(
                "20260820200000", 8, USER_ID, NOW)));
        when(versions.findUserPolicy(USER_ID)).thenReturn(Optional.empty());
        when(versions.transitionAttempt(
                attempt.commandId(), "PREPARING", "CANCELLED", null, "POLICY_OR_RELEASE_CHANGED", NOW))
                .thenReturn(true);
        stubRolloutLock(versions, attempt.rolloutId(), LocalClientVersionModels.RolloutStatus.RUNNING);

        coordinator(
                versions,
                instances,
                connections,
                mock(UserNotificationRepository.class),
                registry,
                notifications)
                .handlePrepared(
                        USER_ID,
                        INSTANCE_ID,
                        7,
                        new LocalClientPayloads.UpdatePrepared(
                                attempt.commandId(),
                                INSTANCE_ID.value(),
                                7,
                                7,
                                TARGET_VERSION,
                                "UPDATE",
                                digest,
                                NOW),
                        "trace_prepared");

        assertThat(sent).singleElement().extracting(LocalClientFrame::type)
                .isEqualTo(LocalClientFrameType.UPDATE_CANCEL);
        verify(notifications, never()).invalidateLocalClientUpdate(
                USER_ID, INSTANCE_ID.value(), "CANCELLED", "trace_prepared");
    }

    @Test
    void terminalStatusIsIdempotentAfterNewVersionReconnects() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientVersionModels.Attempt attempt = attempt(
                LocalClientVersionModels.AttemptStatus.SUCCEEDED, "a".repeat(64), NOW);
        when(versions.findAttempt(attempt.commandId())).thenReturn(Optional.of(attempt));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, TARGET_VERSION)));

        coordinator(
                versions,
                instances,
                mock(LocalClientConnectionStore.class),
                mock(UserNotificationRepository.class))
                .handleStatus(
                        USER_ID,
                        INSTANCE_ID,
                        8,
                        new LocalClientPayloads.UpdateStatus(
                                attempt.commandId(),
                                INSTANCE_ID.value(),
                                7,
                                7,
                                TARGET_VERSION,
                                "UPDATE",
                                "SUCCEEDED",
                                null,
                                NOW),
                        "trace_terminal_retry");

        verify(versions, never()).transitionAttempt(
                anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void lateSucceededCorrectsDeadlineFailureAndRollupToCompleted() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        UserNotificationApplicationService notifications = mock(UserNotificationApplicationService.class);
        LocalClientVersionModels.Attempt timedOut = new LocalClientVersionModels.Attempt(
                "lcuc_late_succeeded", "lcrl_late_succeeded", INSTANCE_ID, USER_ID, 7, 7,
                "20260820180000", TARGET_VERSION, LocalClientUpdateDirection.UPDATE,
                LocalClientVersionModels.AttemptStatus.FAILED, "a".repeat(64),
                "DELIVERY_DEADLINE_EXCEEDED", NOW.minusSeconds(1900), NOW.minusSeconds(100), NOW.minusSeconds(100));
        LocalClientVersionModels.Attempt corrected = withStatus(
                timedOut, LocalClientVersionModels.AttemptStatus.SUCCEEDED, null);
        when(versions.findAttempt(timedOut.commandId())).thenReturn(Optional.of(timedOut));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, TARGET_VERSION)));
        when(versions.transitionAttempt(
                timedOut.commandId(), "FAILED", "SUCCEEDED", timedOut.releaseDigest(), null, NOW)).thenReturn(true);
        when(versions.findRolloutForUpdate(timedOut.rolloutId())).thenReturn(Optional.of(rollout(
                timedOut.rolloutId(), LocalClientVersionModels.RolloutStatus.PARTIAL_FAILED)));
        when(versions.findAttemptsByRollout(timedOut.rolloutId())).thenReturn(List.of(corrected));

        coordinator(
                        versions,
                        instances,
                        mock(LocalClientConnectionStore.class),
                        mock(UserNotificationRepository.class),
                        new LocalClientConnectionRegistry(),
                        notifications)
                .handleStatus(
                        USER_ID,
                        INSTANCE_ID,
                        8,
                        new LocalClientPayloads.UpdateStatus(
                                timedOut.commandId(), INSTANCE_ID.value(), 7, 7, TARGET_VERSION, "UPDATE",
                                "SUCCEEDED", null, NOW),
                        "trace_late_succeeded");

        verify(versions).transitionAttempt(
                timedOut.commandId(), "FAILED", "SUCCEEDED", timedOut.releaseDigest(), null, NOW);
        verify(instances).updateLastUpdateStatus(INSTANCE_ID, "SUCCEEDED", TARGET_VERSION, NOW);
        verify(notifications).invalidateLocalClientUpdate(
                USER_ID, INSTANCE_ID.value(), "SUCCEEDED", "trace_late_succeeded");
        InOrder rollupOrder = inOrder(versions);
        rollupOrder.verify(versions).findRolloutForUpdate(timedOut.rolloutId());
        rollupOrder.verify(versions).findAttemptsByRollout(timedOut.rolloutId());
        rollupOrder.verify(versions).completeRollout(timedOut.rolloutId(), "COMPLETED", NOW);
    }

    @Test
    void lateAutoRollbackCorrectsDeadlineFailureAndKeepsNotification() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        UserNotificationApplicationService notifications = mock(UserNotificationApplicationService.class);
        LocalClientVersionModels.Attempt timedOut = new LocalClientVersionModels.Attempt(
                "lcuc_late_rollback", "lcrl_late_rollback", INSTANCE_ID, USER_ID, 7, 7,
                "20260820180000", TARGET_VERSION, LocalClientUpdateDirection.UPDATE,
                LocalClientVersionModels.AttemptStatus.FAILED, "a".repeat(64),
                "DELIVERY_DEADLINE_EXCEEDED", NOW.minusSeconds(1900), NOW.minusSeconds(100), NOW.minusSeconds(100));
        LocalClientVersionModels.Attempt corrected = withStatus(
                timedOut, LocalClientVersionModels.AttemptStatus.AUTO_ROLLED_BACK, "STARTUP_FAILED");
        when(versions.findAttempt(timedOut.commandId())).thenReturn(Optional.of(timedOut));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, timedOut.currentVersion())));
        when(versions.transitionAttempt(
                timedOut.commandId(), "FAILED", "AUTO_ROLLED_BACK", timedOut.releaseDigest(), "STARTUP_FAILED", NOW))
                .thenReturn(true);
        when(versions.findRolloutForUpdate(timedOut.rolloutId())).thenReturn(Optional.of(rollout(
                timedOut.rolloutId(), LocalClientVersionModels.RolloutStatus.PARTIAL_FAILED)));
        when(versions.findAttemptsByRollout(timedOut.rolloutId())).thenReturn(List.of(corrected));

        coordinator(
                        versions,
                        instances,
                        mock(LocalClientConnectionStore.class),
                        mock(UserNotificationRepository.class),
                        new LocalClientConnectionRegistry(),
                        notifications)
                .handleStatus(
                        USER_ID,
                        INSTANCE_ID,
                        8,
                        new LocalClientPayloads.UpdateStatus(
                                timedOut.commandId(), INSTANCE_ID.value(), 7, 7, TARGET_VERSION, "UPDATE",
                                "AUTO_ROLLED_BACK", "STARTUP_FAILED", NOW),
                        "trace_late_rollback");

        verify(instances).updateLastUpdateStatus(INSTANCE_ID, "AUTO_ROLLED_BACK", TARGET_VERSION, NOW);
        verify(notifications, never()).invalidateLocalClientUpdate(
                USER_ID, INSTANCE_ID.value(), "AUTO_ROLLED_BACK", "trace_late_rollback");
        verify(versions).completeRollout(timedOut.rolloutId(), "PARTIAL_FAILED", NOW);
    }

    @Test
    void conflictingOrdinaryTerminalStatusIsRejectedWithoutRewrite() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientVersionModels.Attempt failed = new LocalClientVersionModels.Attempt(
                "lcuc_ordinary_failed", "lcrl_ordinary_failed", INSTANCE_ID, USER_ID, 7, 7,
                "20260820180000", TARGET_VERSION, LocalClientUpdateDirection.UPDATE,
                LocalClientVersionModels.AttemptStatus.FAILED, "a".repeat(64),
                "VERIFY_FAILED", NOW.minusSeconds(60), NOW.minusSeconds(30), NOW.minusSeconds(30));
        when(versions.findAttempt(failed.commandId())).thenReturn(Optional.of(failed));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, TARGET_VERSION)));

        assertThatThrownBy(() -> coordinator(
                        versions,
                        instances,
                        mock(LocalClientConnectionStore.class),
                        mock(UserNotificationRepository.class))
                .handleStatus(
                        USER_ID,
                        INSTANCE_ID,
                        8,
                        new LocalClientPayloads.UpdateStatus(
                                failed.commandId(), INSTANCE_ID.value(), 7, 7, TARGET_VERSION, "UPDATE",
                                "SUCCEEDED", null, NOW),
                        "trace_terminal_conflict"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(versions, never()).transitionAttempt(
                anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void deadlineCorrectionCasRaceAcceptsOnlySamePersistedTerminal() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientVersionModels.Attempt timedOut = new LocalClientVersionModels.Attempt(
                "lcuc_late_race", "lcrl_late_race", INSTANCE_ID, USER_ID, 7, 7,
                "20260820180000", TARGET_VERSION, LocalClientUpdateDirection.UPDATE,
                LocalClientVersionModels.AttemptStatus.FAILED, "a".repeat(64),
                "DELIVERY_DEADLINE_EXCEEDED", NOW.minusSeconds(1900), NOW.minusSeconds(100), NOW.minusSeconds(100));
        LocalClientVersionModels.Attempt persistedRollback = withStatus(
                timedOut, LocalClientVersionModels.AttemptStatus.AUTO_ROLLED_BACK, "STARTUP_FAILED");
        when(versions.findAttempt(timedOut.commandId())).thenReturn(
                Optional.of(timedOut), Optional.of(persistedRollback));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, TARGET_VERSION)));
        when(versions.transitionAttempt(
                timedOut.commandId(), "FAILED", "SUCCEEDED", timedOut.releaseDigest(), null, NOW))
                .thenReturn(false);

        assertThatThrownBy(() -> coordinator(
                        versions,
                        instances,
                        mock(LocalClientConnectionStore.class),
                        mock(UserNotificationRepository.class))
                .handleStatus(
                        USER_ID,
                        INSTANCE_ID,
                        8,
                        new LocalClientPayloads.UpdateStatus(
                                timedOut.commandId(), INSTANCE_ID.value(), 7, 7, TARGET_VERSION, "UPDATE",
                                "SUCCEEDED", null, NOW),
                        "trace_late_race"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        verify(instances, never()).updateLastUpdateStatus(any(), anyString(), anyString(), any());
    }

    @Test
    void terminalRollupFailsWithNotFoundWhenRolloutLockTargetIsMissing() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientVersionModels.Attempt timedOut = new LocalClientVersionModels.Attempt(
                "lcuc_missing_rollout", "lcrl_missing_rollout", INSTANCE_ID, USER_ID, 7, 7,
                "20260820180000", TARGET_VERSION, LocalClientUpdateDirection.UPDATE,
                LocalClientVersionModels.AttemptStatus.FAILED, "a".repeat(64),
                "DELIVERY_DEADLINE_EXCEEDED", NOW.minusSeconds(1900), NOW.minusSeconds(100), NOW.minusSeconds(100));
        when(versions.findAttempt(timedOut.commandId())).thenReturn(Optional.of(timedOut));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, TARGET_VERSION)));
        when(versions.transitionAttempt(
                timedOut.commandId(), "FAILED", "SUCCEEDED", timedOut.releaseDigest(), null, NOW)).thenReturn(true);
        when(versions.findRolloutForUpdate(timedOut.rolloutId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coordinator(
                        versions,
                        instances,
                        mock(LocalClientConnectionStore.class),
                        mock(UserNotificationRepository.class))
                .handleStatus(
                        USER_ID,
                        INSTANCE_ID,
                        8,
                        new LocalClientPayloads.UpdateStatus(
                                timedOut.commandId(), INSTANCE_ID.value(), 7, 7, TARGET_VERSION, "UPDATE",
                                "SUCCEEDED", null, NOW),
                        "trace_missing_rollout"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(versions, never()).findAttemptsByRollout(timedOut.rolloutId());
        verify(versions, never()).completeRollout(eq(timedOut.rolloutId()), anyString(), any());
    }

    @Test
    void terminalServiceFailsClosedWhenTransactionManagerDoesNotActivateTransaction() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientVersionModels.Attempt applying = attempt(
                LocalClientVersionModels.AttemptStatus.APPLYING, "a".repeat(64), null);
        PlatformTransactionManager brokenTransactionManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
                // 故意不建立 Spring 活动事务，模拟事务门面误装配。
            }

            @Override
            public void rollback(TransactionStatus status) {
                // 故意不建立 Spring 活动事务，模拟事务门面误装配。
            }
        };
        LocalClientUpdateTerminalService terminalService = new LocalClientUpdateTerminalService(
                versions,
                instances,
                mock(UserNotificationApplicationService.class),
                brokenTransactionManager,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> terminalService.finalizeAttempt(
                        applying,
                        LocalClientVersionModels.AttemptStatus.SUCCEEDED,
                        null,
                        "trace_missing_terminal_transaction"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("本地客户端更新终态缺少活动事务");

        verify(versions, never()).transitionAttempt(
                anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void terminalMutationRollbackIncludesAttemptAndInstanceWhenRolloutLockFails() {
        AtomicBoolean attemptPersisted = new AtomicBoolean();
        AtomicBoolean instancePersisted = new AtomicBoolean();
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        LocalClientVersionModels.Attempt applying = attempt(
                LocalClientVersionModels.AttemptStatus.APPLYING, "a".repeat(64), null);
        when(versions.transitionAttempt(
                applying.commandId(), "APPLYING", "SUCCEEDED", applying.releaseDigest(), null, NOW))
                .thenAnswer(ignored -> {
                    attemptPersisted.set(true);
                    return true;
                });
        org.mockito.Mockito.doAnswer(ignored -> {
                    instancePersisted.set(true);
                    return null;
                })
                .when(instances)
                .updateLastUpdateStatus(INSTANCE_ID, "SUCCEEDED", TARGET_VERSION, NOW);
        when(versions.findRolloutForUpdate(applying.rolloutId()))
                .thenThrow(new IllegalStateException("forced rollout lock failure"));
        RollbackAwareTransactionManager transactionManager = new RollbackAwareTransactionManager(
                () -> {
                    attemptPersisted.set(false);
                    instancePersisted.set(false);
                });
        LocalClientUpdateTerminalService terminalService = new LocalClientUpdateTerminalService(
                versions,
                instances,
                mock(UserNotificationApplicationService.class),
                transactionManager,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> terminalService.finalizeAttempt(
                        applying,
                        LocalClientVersionModels.AttemptStatus.SUCCEEDED,
                        null,
                        "trace_terminal_rollback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced rollout lock failure");

        assertThat(transactionManager.rolledBack()).isTrue();
        assertThat(attemptPersisted).isFalse();
        assertThat(instancePersisted).isFalse();
    }

    @Test
    void autoRollbackKeepsNotificationEligibleForNextVersionCheck() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientInstanceRepository instances = mock(LocalClientInstanceRepository.class);
        UserNotificationApplicationService notifications = mock(UserNotificationApplicationService.class);
        LocalClientConnectionRegistry registry = new LocalClientConnectionRegistry();
        registry.register(INSTANCE_ID, USER_ID, 8, "grant-fingerprint", sender(new CopyOnWriteArrayList<>()));
        LocalClientVersionModels.Attempt attempt = attempt(
                LocalClientVersionModels.AttemptStatus.APPLYING, "a".repeat(64), null);
        when(versions.findAttempt(attempt.commandId())).thenReturn(Optional.of(attempt));
        when(versions.transitionAttempt(
                attempt.commandId(), "APPLYING", "AUTO_ROLLED_BACK", attempt.releaseDigest(),
                "STARTUP_FAILED", NOW)).thenReturn(true);
        when(versions.findRolloutForUpdate(attempt.rolloutId())).thenReturn(Optional.of(rollout(
                attempt.rolloutId(), LocalClientVersionModels.RolloutStatus.RUNNING)));
        when(versions.findAttemptsByRollout(attempt.rolloutId())).thenReturn(List.of(new LocalClientVersionModels.Attempt(
                attempt.commandId(), attempt.rolloutId(), attempt.clientInstanceId(), attempt.userId(),
                attempt.connectionGeneration(), attempt.policyRevision(), attempt.currentVersion(),
                attempt.targetVersion(), attempt.direction(), LocalClientVersionModels.AttemptStatus.AUTO_ROLLED_BACK,
                attempt.releaseDigest(), "STARTUP_FAILED", attempt.createdAt(), NOW, NOW)));
        when(instances.findById(INSTANCE_ID)).thenReturn(Optional.of(instance(true, attempt.currentVersion())));
        when(versions.findGlobalPolicy()).thenReturn(Optional.of(new LocalClientVersionModels.GlobalPolicy(
                TARGET_VERSION, 7, USER_ID, NOW)));
        when(versions.findUserPolicy(USER_ID)).thenReturn(Optional.empty());
        when(versions.findRelease(TARGET_VERSION)).thenReturn(Optional.of(release("a".repeat(64))));

        coordinator(versions, instances, mock(LocalClientConnectionStore.class),
                mock(UserNotificationRepository.class), registry, notifications)
                .handleStatus(
                        USER_ID,
                        INSTANCE_ID,
                        8,
                        new LocalClientPayloads.UpdateStatus(
                                attempt.commandId(), INSTANCE_ID.value(), 7, 7, TARGET_VERSION, "UPDATE",
                                "AUTO_ROLLED_BACK", "STARTUP_FAILED", NOW),
                        "trace_rollback");

        verify(notifications, never()).invalidateLocalClientUpdate(
                USER_ID, INSTANCE_ID.value(), "AUTO_ROLLED_BACK", "trace_rollback");

        coordinator(versions, instances, mock(LocalClientConnectionStore.class),
                mock(UserNotificationRepository.class), registry, notifications)
                .handleVersionCheck(
                        USER_ID,
                        INSTANCE_ID,
                        8,
                        new LocalClientPayloads.VersionCheck(
                                INSTANCE_ID.value(), attempt.currentVersion(), "1", "2.0.18",
                                List.of("SELF_UPDATE_V1"), NOW),
                        "trace_after_rollback");

        verify(notifications).syncLocalClientUpdateAvailable(
                USER_ID,
                INSTANCE_ID.value(),
                "麒麟工作站",
                attempt.currentVersion(),
                TARGET_VERSION,
                "UPDATE",
                7,
                "trace_after_rollback");
    }

    @Test
    void applyingProgressIsAcceptedOnlyForOriginalGeneration() {
        LocalClientVersionRepository versions = mock(LocalClientVersionRepository.class);
        LocalClientVersionModels.Attempt attempt = attempt(
                LocalClientVersionModels.AttemptStatus.APPLYING, "a".repeat(64), null);
        when(versions.findAttempt(attempt.commandId())).thenReturn(Optional.of(attempt));

        coordinator(
                versions,
                mock(LocalClientInstanceRepository.class),
                mock(LocalClientConnectionStore.class),
                mock(UserNotificationRepository.class))
                .handleStatus(
                        USER_ID,
                        INSTANCE_ID,
                        7,
                        new LocalClientPayloads.UpdateStatus(
                                attempt.commandId(),
                                INSTANCE_ID.value(),
                                7,
                                7,
                                TARGET_VERSION,
                                "UPDATE",
                                "APPLYING",
                                null,
                                NOW),
                        "trace_applying");

        verify(versions, never()).transitionAttempt(
                anyString(), anyString(), anyString(), any(), any(), any());
    }

    private LocalClientUpdateCoordinator coordinator(
            LocalClientVersionRepository versions,
            LocalClientInstanceRepository instances,
            LocalClientConnectionStore connections,
            UserNotificationRepository notificationRepository) {
        return coordinator(
                versions, instances, connections, notificationRepository, new LocalClientConnectionRegistry());
    }

    private LocalClientUpdateCoordinator coordinator(
            LocalClientVersionRepository versions,
            LocalClientInstanceRepository instances,
            LocalClientConnectionStore connections,
            UserNotificationRepository notificationRepository,
            LocalClientConnectionRegistry registry) {
        return coordinator(
                versions,
                instances,
                connections,
                notificationRepository,
                registry,
                mock(UserNotificationApplicationService.class));
    }

    private LocalClientUpdateCoordinator coordinator(
            LocalClientVersionRepository versions,
            LocalClientInstanceRepository instances,
            LocalClientConnectionStore connections,
            UserNotificationRepository notificationRepository,
            LocalClientConnectionRegistry registry,
            UserNotificationApplicationService notifications) {
        return coordinator(
                versions,
                instances,
                connections,
                notificationRepository,
                registry,
                notifications,
                mock(LocalClientUpdateNotificationInvalidationService.class));
    }

    private LocalClientUpdateCoordinator coordinator(
            LocalClientVersionRepository versions,
            LocalClientInstanceRepository instances,
            LocalClientConnectionStore connections,
            UserNotificationRepository notificationRepository,
            LocalClientConnectionRegistry registry,
            UserNotificationApplicationService notifications,
            LocalClientUpdateNotificationInvalidationService invalidation) {
        ServerBroadcastPublisher publisher = mock(ServerBroadcastPublisher.class);
        BackendInstanceIdentity identity = mock(BackendInstanceIdentity.class);
        when(identity.backendProcessId()).thenReturn("bjp_local_update_test");
        when(identity.instanceId()).thenReturn("backend-local-update-test");
        when(identity.linuxServerId()).thenReturn("linux-local-update-test");
        LocalClientUpdateTerminalService terminalService = new LocalClientUpdateTerminalService(
                versions,
                instances,
                notifications,
                new ActualTransactionManager(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new LocalClientUpdateCoordinator(
                versions,
                instances,
                connections,
                registry,
                publisher,
                identity,
                notifications,
                notificationRepository,
                invalidation,
                terminalService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 仅为单元测试提供真实 Spring 事务同步边界，不访问数据库。 */
    private static final class ActualTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // AbstractPlatformTransactionManager 负责登记 actualTransactionActive。
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // mock repository 无需真实资源提交。
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // mock repository 无需真实资源回滚。
        }
    }

    /** 模拟事务资源回滚，用于证明门面不会提交 rollout 汇总前的半套写入。 */
    private static final class RollbackAwareTransactionManager extends AbstractPlatformTransactionManager {

        private final Runnable rollbackAction;
        private boolean rolledBack;

        private RollbackAwareTransactionManager(Runnable rollbackAction) {
            this.rollbackAction = rollbackAction;
        }

        private boolean rolledBack() {
            return rolledBack;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // AbstractPlatformTransactionManager 负责登记 actualTransactionActive。
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // 本用例预期不提交。
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
            rollbackAction.run();
        }
    }

    private LocalClientInstance instance(boolean selfUpdateSupported, String version) {
        return instance(INSTANCE_ID, USER_ID, selfUpdateSupported, version);
    }

    private LocalClientInstance instance(
            LocalClientInstanceId instanceId,
            UserId userId,
            boolean selfUpdateSupported,
            String version) {
        return new LocalClientInstance(
                instanceId,
                userId,
                "麒麟工作站",
                "linux",
                "arm64",
                version,
                "2.0.18",
                selfUpdateSupported ? "1" : null,
                selfUpdateSupported ? List.of("SELF_UPDATE_V1") : List.of(),
                selfUpdateSupported,
                null,
                null,
                null,
                NOW.minusSeconds(3600),
                NOW,
                NOW,
                null);
    }

    private LocalClientConnectionRoute route(
            LocalClientInstanceId instanceId,
            UserId userId,
            long generation) {
        return new LocalClientConnectionRoute(
                instanceId,
                userId,
                new BackendProcessId("bjp_local_update_test"),
                generation,
                "127.0.0.1:12345",
                List.of("127.0.0.1"),
                4096,
                LocalClientProcessStatus.RUNNING,
                1234L,
                NOW.minusSeconds(60),
                true,
                NOW.minusSeconds(120),
                NOW);
    }

    private LocalClientVersionModels.Attempt attempt(
            LocalClientVersionModels.AttemptStatus status,
            String releaseDigest,
            Instant completedAt) {
        return new LocalClientVersionModels.Attempt(
                "lcuc_local_update_test",
                "lcrl_local_update_test",
                INSTANCE_ID,
                USER_ID,
                7,
                7,
                "20260820180000",
                TARGET_VERSION,
                LocalClientUpdateDirection.UPDATE,
                status,
                releaseDigest,
                null,
                NOW.minusSeconds(60),
                NOW,
                completedAt);
    }

    private LocalClientVersionModels.Attempt attempt(
            String commandId,
            LocalClientInstanceId instanceId,
            LocalClientVersionModels.AttemptStatus status,
            Instant createdAt,
            Instant updatedAt) {
        return new LocalClientVersionModels.Attempt(
                commandId,
                "lcrl_local_update_test",
                instanceId,
                USER_ID,
                7,
                7,
                "20260820180000",
                TARGET_VERSION,
                LocalClientUpdateDirection.UPDATE,
                status,
                null,
                null,
                createdAt,
                updatedAt,
                null);
    }

    private LocalClientVersionModels.Attempt withStatus(
            LocalClientVersionModels.Attempt attempt,
            LocalClientVersionModels.AttemptStatus status,
            String errorCode) {
        return new LocalClientVersionModels.Attempt(
                attempt.commandId(),
                attempt.rolloutId(),
                attempt.clientInstanceId(),
                attempt.userId(),
                attempt.connectionGeneration(),
                attempt.policyRevision(),
                attempt.currentVersion(),
                attempt.targetVersion(),
                attempt.direction(),
                status,
                attempt.releaseDigest(),
                errorCode,
                attempt.createdAt(),
                NOW,
                NOW);
    }

    private LocalClientVersionModels.Rollout rollout(
            String rolloutId,
            LocalClientVersionModels.RolloutStatus status) {
        return new LocalClientVersionModels.Rollout(
                rolloutId,
                LocalClientVersionModels.RolloutScope.ALL_ONLINE,
                null,
                status,
                USER_ID,
                NOW.minusSeconds(3600),
                status == LocalClientVersionModels.RolloutStatus.RUNNING ? null : NOW.minusSeconds(60));
    }

    private void stubRolloutLock(
            LocalClientVersionRepository versions,
            String rolloutId,
            LocalClientVersionModels.RolloutStatus status) {
        when(versions.findRolloutForUpdate(rolloutId)).thenReturn(Optional.of(rollout(rolloutId, status)));
    }

    private LocalClientVersionModels.Release release(String digest) {
        return release(TARGET_VERSION, "linux", "arm64", digest);
    }

    private LocalClientVersionModels.Release release(
            String version,
            String platform,
            String architecture,
            String digest) {
        return new LocalClientVersionModels.Release(
                version,
                platform,
                architecture,
                1,
                1,
                "local-opencode-client.v1",
                "http://downloads.example/releases/" + version + "/manifest.json",
                digest,
                "manifest-signature",
                true,
                NOW.minusSeconds(3600),
                NOW,
                List.of(
                        artifact("CLIENT_JAR", "b".repeat(64)),
                        artifact("JDK", "c".repeat(64)),
                        artifact("OPENCODE", "d".repeat(64))));
    }

    private LocalClientVersionModels.Artifact artifact(String kind, String digest) {
        return new LocalClientVersionModels.Artifact(
                kind,
                "http://downloads.example/releases/" + TARGET_VERSION + "/" + kind.toLowerCase(),
                1024,
                digest,
                "artifact-signature");
    }

    private LocalClientConnectionSender sender(List<LocalClientFrame> sent) {
        return new LocalClientConnectionSender() {
            @Override
            public void send(LocalClientFrame frame) {
                sent.add(frame);
            }

            @Override
            public void close(String reason) {
                // 当前用例仅观察协调器下发帧。
            }
        };
    }

    private UserNotification updateNotification() {
        return new UserNotification(
                NOTIFICATION_ID,
                USER_ID,
                UserNotificationType.LOCAL_CLIENT_UPDATE_AVAILABLE,
                null,
                "本地客户端有可用版本",
                "麒麟工作站可更新",
                UserNotificationActionType.LOCAL_CLIENT_UPDATE,
                INSTANCE_ID.value(),
                "LOCAL_CLIENT_UPDATE:" + INSTANCE_ID.value() + ":" + TARGET_VERSION + ":7",
                UserNotificationStatus.ACTIVE,
                null,
                NOW.plusSeconds(86400),
                null,
                null,
                "trace_notification",
                NOW,
                NOW);
    }
}
