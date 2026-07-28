package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.appsource.AppSourceStepScope;
import com.enterprise.testagent.domain.appsource.AppSourceStepStatus;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class AppSourceReplicaResultRecorderTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_1");
    private static final LinuxServerId SERVER_A = new LinuxServerId("server-a");
    private static final LinuxServerId SERVER_B = new LinuxServerId("server-b");
    private static final LinuxServerId SERVER_C = new LinuxServerId("server-c");
    private static final String INDEX_SHA = "a".repeat(64);

    @Test
    void firstReadyReplicaPromotesPendingGenerationAndExpiresOldGeneration() {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        AppSourceReplicaResultRecorder recorder = new AppSourceReplicaResultRecorder(appSources, workspaces);
        AppSourceOperation operation = operation();
        AppSourceReplica claimed = replica(2L, SERVER_A, AppSourceReplicaStatus.RUNNING, null);
        AppSourceRepositorySlot slot = slot(1L, 2L, 7L);
        Workspace oldWorkspace = workspace("wrk_old", WorkspaceStatus.ACTIVE);
        Workspace newWorkspace = workspace("wrk_new", WorkspaceStatus.ACTIVE);

        when(appSources.updateReplicaIfLease(any(), eq("lease-a"), eq(NOW))).thenReturn(true);
        when(appSources.findSlotForUpdate(REPOSITORY_ID)).thenReturn(Optional.of(slot));
        when(appSources.findSnapshot(REPOSITORY_ID, 2L)).thenReturn(Optional.of(snapshot(2L, AppSourceSnapshotStatus.PENDING, null)));
        when(appSources.findSnapshot(REPOSITORY_ID, 1L)).thenReturn(Optional.of(snapshot(1L, AppSourceSnapshotStatus.ACTIVE, INDEX_SHA)));
        when(appSources.findReplica(REPOSITORY_ID, 1L, SERVER_A))
                .thenReturn(Optional.of(replica(1L, SERVER_A, AppSourceReplicaStatus.READY, oldWorkspace.workspaceId())));
        when(workspaces.findById(oldWorkspace.workspaceId())).thenReturn(Optional.of(oldWorkspace));
        when(appSources.updateSnapshotStatusAndIndex(
                        REPOSITORY_ID, 2L, AppSourceSnapshotStatus.PENDING,
                        AppSourceSnapshotStatus.ACTIVE, INDEX_SHA, NOW))
                .thenReturn(true);
        when(appSources.updateSlotIfVersion(any(), eq(7L))).thenReturn(true);
        when(appSources.findReplicas(REPOSITORY_ID, 2L)).thenReturn(List.of(
                replica(2L, SERVER_A, AppSourceReplicaStatus.READY, newWorkspace.workspaceId()),
                replica(2L, SERVER_B, AppSourceReplicaStatus.PENDING, null)));

        recorder.recordSuccess(operation, claimed, "lease-a", newWorkspace, INDEX_SHA, NOW);

        InOrder snapshotOrder = inOrder(appSources);
        snapshotOrder.verify(appSources).updateSnapshotStatusAndIndex(
                REPOSITORY_ID, 1L, AppSourceSnapshotStatus.ACTIVE,
                AppSourceSnapshotStatus.EXPIRED, INDEX_SHA, NOW);
        snapshotOrder.verify(appSources).updateSnapshotStatusAndIndex(
                REPOSITORY_ID, 2L, AppSourceSnapshotStatus.PENDING,
                AppSourceSnapshotStatus.ACTIVE, INDEX_SHA, NOW);
        verify(appSources).updateSnapshotStatusAndIndex(
                REPOSITORY_ID, 1L, AppSourceSnapshotStatus.ACTIVE,
                AppSourceSnapshotStatus.EXPIRED, INDEX_SHA, NOW);
        verify(appSources).makeCleanupDueNow(REPOSITORY_ID, 1L, NOW);
        ArgumentCaptor<AppSourceRepositorySlot> slotCaptor = ArgumentCaptor.forClass(AppSourceRepositorySlot.class);
        verify(appSources).updateSlotIfVersion(slotCaptor.capture(), eq(7L));
        assertThat(slotCaptor.getValue().activeGeneration()).isEqualTo(2L);
        assertThat(slotCaptor.getValue().pendingGeneration()).isNull();
        ArgumentCaptor<Workspace> archived = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaces, times(2)).save(archived.capture());
        assertThat(archived.getAllValues())
                .extracting(Workspace::status)
                .containsExactly(WorkspaceStatus.ACTIVE, WorkspaceStatus.ARCHIVED);
        verify(appSources, never()).updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.SUCCEEDED, NOW);
    }

    @Test
    void finalFailureAfterAnotherServerSucceededFinishesOperationAsPartialFailure() {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        AppSourceReplicaResultRecorder recorder = new AppSourceReplicaResultRecorder(appSources, workspaces);
        AppSourceOperation operation = operation();
        AppSourceReplica claimed = replica(2L, SERVER_B, AppSourceReplicaStatus.RUNNING, null);
        when(appSources.updateReplicaIfLease(any(), eq("lease-b"), eq(NOW))).thenReturn(true);
        when(appSources.findSlotForUpdate(REPOSITORY_ID)).thenReturn(Optional.of(slot(2L, null, 9L)));
        when(appSources.findReplicas(REPOSITORY_ID, 2L)).thenReturn(List.of(
                replica(2L, SERVER_A, AppSourceReplicaStatus.READY, new WorkspaceId("wrk_a")),
                replica(2L, SERVER_B, AppSourceReplicaStatus.FAILED, null)));

        recorder.recordFailure(operation, claimed, "lease-b", "GIT_FAILED", "源码同步失败", NOW);

        verify(appSources).updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.PARTIAL_FAILED, NOW);
        verify(appSources, never()).updateSnapshotStatusAndIndex(
                eq(REPOSITORY_ID), eq(2L), any(), eq(AppSourceSnapshotStatus.FAILED), any(), eq(NOW));
    }

    @Test
    void retryResultDoesNotFinishWhileAnotherTargetServerStepIsPending() {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        AppSourceReplicaProgressRecorder progress = mock(AppSourceReplicaProgressRecorder.class);
        AppSourceReplicaResultRecorder recorder = new AppSourceReplicaResultRecorder(appSources, workspaces, progress);
        AppSourceOperation operation = retryOperation();
        AppSourceReplica claimed = replica(2L, SERVER_B, AppSourceReplicaStatus.RUNNING, null);
        when(appSources.updateReplicaIfLease(any(), eq("lease-b"), eq(NOW))).thenReturn(true);
        when(appSources.findSlotForUpdate(REPOSITORY_ID)).thenReturn(Optional.of(slot(2L, null, 9L)));
        when(appSources.findReplicas(REPOSITORY_ID, 2L)).thenReturn(List.of(
                replica(2L, SERVER_A, AppSourceReplicaStatus.READY, new WorkspaceId("wrk_a")),
                replica(2L, SERVER_B, AppSourceReplicaStatus.FAILED, null),
                replica(2L, SERVER_C, AppSourceReplicaStatus.FAILED, null)));
        when(appSources.findSteps(operation.operationId())).thenReturn(List.of(
                retryStep(operation.operationId(), SERVER_B, AppSourceStepStatus.FAILED),
                retryStep(operation.operationId(), SERVER_C, AppSourceStepStatus.PENDING)));

        recorder.recordFailure(operation, claimed, "lease-b", "GIT_FAILED", "源码同步失败", NOW);

        verify(progress).failAfterLeaseCas(operation, claimed, NOW);
        verify(appSources, never()).updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.PARTIAL_FAILED, NOW);
        verify(appSources, never()).updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.FAILED, NOW);
    }

    @Test
    void failureLocksRepositorySlotAfterReplicaCasBeforeReadingAggregateState() {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        AppSourceReplicaResultRecorder recorder = new AppSourceReplicaResultRecorder(appSources, workspaces);
        AppSourceOperation operation = operation();
        AppSourceReplica claimed = replica(2L, SERVER_B, AppSourceReplicaStatus.RUNNING, null);
        when(appSources.updateReplicaIfLease(any(), eq("lease-b"), eq(NOW))).thenReturn(true);
        when(appSources.findSlotForUpdate(REPOSITORY_ID)).thenReturn(Optional.of(slot(2L, null, 9L)));
        when(appSources.findReplicas(REPOSITORY_ID, 2L)).thenReturn(List.of(
                replica(2L, SERVER_A, AppSourceReplicaStatus.READY, new WorkspaceId("wrk_a")),
                replica(2L, SERVER_B, AppSourceReplicaStatus.FAILED, null)));

        recorder.recordFailure(operation, claimed, "lease-b", "GIT_FAILED", "源码同步失败", NOW);

        InOrder order = inOrder(appSources);
        order.verify(appSources).updateReplicaIfLease(any(), eq("lease-b"), eq(NOW));
        order.verify(appSources).findSlotForUpdate(REPOSITORY_ID);
        order.verify(appSources).findReplicas(REPOSITORY_ID, 2L);
    }

    @Test
    void allReplicaFailuresKeepOldActiveGenerationAndClearOnlyPendingGeneration() {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        AppSourceReplicaResultRecorder recorder = new AppSourceReplicaResultRecorder(appSources, workspaces);
        AppSourceOperation operation = operation();
        AppSourceReplica claimed = replica(2L, SERVER_B, AppSourceReplicaStatus.RUNNING, null);
        AppSourceRepositorySlot slot = slot(1L, 2L, 9L);
        when(appSources.updateReplicaIfLease(any(), eq("lease-b"), eq(NOW))).thenReturn(true);
        when(appSources.findReplicas(REPOSITORY_ID, 2L)).thenReturn(List.of(
                replica(2L, SERVER_A, AppSourceReplicaStatus.FAILED, null),
                replica(2L, SERVER_B, AppSourceReplicaStatus.FAILED, null)));
        when(appSources.findSlotForUpdate(REPOSITORY_ID)).thenReturn(Optional.of(slot));
        when(appSources.updateSlotIfVersion(any(), eq(9L))).thenReturn(true);

        recorder.recordFailure(operation, claimed, "lease-b", "GIT_FAILED", "源码同步失败", NOW);

        verify(appSources).updateSnapshotStatusAndIndex(
                REPOSITORY_ID, 2L, AppSourceSnapshotStatus.PENDING,
                AppSourceSnapshotStatus.FAILED, null, NOW);
        ArgumentCaptor<AppSourceRepositorySlot> slotCaptor = ArgumentCaptor.forClass(AppSourceRepositorySlot.class);
        verify(appSources).updateSlotIfVersion(slotCaptor.capture(), eq(9L));
        assertThat(slotCaptor.getValue().activeGeneration()).isEqualTo(1L);
        assertThat(slotCaptor.getValue().pendingGeneration()).isNull();
        verify(appSources).updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.FAILED, NOW);
    }

    @Test
    void recoveryClearsPendingGenerationWhenHistoricOperationHasOnlyFailedReplicas() {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        AppSourceReplicaResultRecorder recorder = new AppSourceReplicaResultRecorder(appSources, workspaces);
        AppSourceOperation operation = operation();
        AppSourceRepositorySlot slot = slot(1L, 2L, 9L);
        when(appSources.findOperation(operation.operationId())).thenReturn(Optional.of(operation));
        when(appSources.findSlotForUpdate(REPOSITORY_ID)).thenReturn(Optional.of(slot));
        when(appSources.findReplicas(REPOSITORY_ID, 2L)).thenReturn(List.of(
                replica(2L, SERVER_A, AppSourceReplicaStatus.FAILED, null),
                replica(2L, SERVER_B, AppSourceReplicaStatus.FAILED, null)));
        when(appSources.findSteps(operation.operationId())).thenReturn(List.of(legacyPendingStep(SERVER_A)));
        when(appSources.upsertStep(any())).thenReturn(true);
        when(appSources.updateSlotIfVersion(any(), eq(9L))).thenReturn(true);

        recorder.recoverTerminalOperation(operation.operationId(), NOW);

        verify(appSources).updateSnapshotStatusAndIndex(
                REPOSITORY_ID, 2L, AppSourceSnapshotStatus.PENDING,
                AppSourceSnapshotStatus.FAILED, null, NOW);
        verify(appSources).updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.FAILED, NOW);
        ArgumentCaptor<AppSourceRepositorySlot> slotCaptor = ArgumentCaptor.forClass(AppSourceRepositorySlot.class);
        verify(appSources).updateSlotIfVersion(slotCaptor.capture(), eq(9L));
        assertThat(slotCaptor.getValue().pendingGeneration()).isNull();
        assertThat(slotCaptor.getValue().activeGeneration()).isEqualTo(1L);
        ArgumentCaptor<AppSourceOperationStep> legacyTerminal =
                ArgumentCaptor.forClass(AppSourceOperationStep.class);
        verify(appSources).upsertStep(legacyTerminal.capture());
        assertThat(legacyTerminal.getValue().status()).isEqualTo(AppSourceStepStatus.FAILED);
        assertThat(legacyTerminal.getValue().safeSummary()).isEqualTo("执行失败：兼容旧版服务器步骤");
    }

    @Test
    void recoveryCompletesLegacyPendingStepWhenHistoricReplicaIsReady() {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        AppSourceReplicaResultRecorder recorder = new AppSourceReplicaResultRecorder(appSources, workspaces);
        AppSourceOperation operation = operation();
        when(appSources.findOperation(operation.operationId())).thenReturn(Optional.of(operation));
        when(appSources.findSlotForUpdate(REPOSITORY_ID)).thenReturn(Optional.of(slot(2L, null, 10L)));
        when(appSources.findReplicas(REPOSITORY_ID, 2L)).thenReturn(List.of(
                replica(2L, SERVER_A, AppSourceReplicaStatus.READY, new WorkspaceId("wrk_ready"))));
        when(appSources.findSteps(operation.operationId())).thenReturn(List.of(legacyPendingStep(SERVER_A)));
        when(appSources.upsertStep(any())).thenReturn(true);

        recorder.recoverTerminalOperation(operation.operationId(), NOW);

        ArgumentCaptor<AppSourceOperationStep> legacyTerminal =
                ArgumentCaptor.forClass(AppSourceOperationStep.class);
        verify(appSources, times(2)).upsertStep(legacyTerminal.capture());
        assertThat(legacyTerminal.getAllValues()).extracting(AppSourceOperationStep::status)
                .containsExactly(AppSourceStepStatus.RUNNING, AppSourceStepStatus.SUCCEEDED);
        assertThat(legacyTerminal.getAllValues()).extracting(AppSourceOperationStep::safeSummary)
                .containsExactly("正在执行：兼容旧版服务器步骤", "已完成：兼容旧版服务器步骤");
        verify(appSources).updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.SUCCEEDED, NOW);
    }

    @Test
    void retryRecoveryRechecksPendingTargetStepsUnderSlotLock() {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        AppSourceReplicaProgressRecorder progress = mock(AppSourceReplicaProgressRecorder.class);
        AppSourceReplicaResultRecorder recorder = new AppSourceReplicaResultRecorder(appSources, workspaces, progress);
        AppSourceOperation operation = retryOperation();
        when(appSources.findOperation(operation.operationId())).thenReturn(Optional.of(operation));
        when(appSources.findSlotForUpdate(REPOSITORY_ID)).thenReturn(Optional.of(slot(2L, null, 10L)));
        when(appSources.findReplicas(REPOSITORY_ID, 2L)).thenReturn(List.of(
                replica(2L, SERVER_A, AppSourceReplicaStatus.READY, new WorkspaceId("wrk_a")),
                replica(2L, SERVER_B, AppSourceReplicaStatus.FAILED, null),
                replica(2L, SERVER_C, AppSourceReplicaStatus.FAILED, null)));
        when(appSources.findSteps(operation.operationId())).thenReturn(List.of(
                retryStep(operation.operationId(), SERVER_B, AppSourceStepStatus.FAILED),
                retryStep(operation.operationId(), SERVER_C, AppSourceStepStatus.PENDING)));

        recorder.recoverTerminalOperation(operation.operationId(), NOW);

        verify(appSources).findSlotForUpdate(REPOSITORY_ID);
        verify(progress, never()).completeAfterLeaseCas(any(), any(), any());
        verify(progress, never()).failAfterLeaseCas(any(), any(), any());
        verify(appSources, never()).updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.PARTIAL_FAILED, NOW);
        verify(appSources, never()).updateOperationStatus(
                operation.operationId(), AppSourceOperationStatus.RUNNING,
                AppSourceOperationStatus.FAILED, NOW);
    }

    @Test
    void replicaFinishingAfterSnapshotExpiryCannotPublishWorkspaceOrPromoteGeneration() {
        AppSourceRepository appSources = mock(AppSourceRepository.class);
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        AppSourceReplicaResultRecorder recorder = new AppSourceReplicaResultRecorder(appSources, workspaces);
        AppSourceOperation operation = operation();
        AppSourceReplica claimed = replica(2L, SERVER_A, AppSourceReplicaStatus.RUNNING, null);
        Workspace workspace = workspace("wrk_expired", WorkspaceStatus.ACTIVE);
        Instant afterExpiry = NOW.plusSeconds(7200);
        when(appSources.findSnapshot(REPOSITORY_ID, 2L))
                .thenReturn(Optional.of(snapshot(2L, AppSourceSnapshotStatus.PENDING, null)));

        assertThatThrownBy(() -> recorder.recordSuccess(
                        operation, claimed, "lease-a", workspace, INDEX_SHA, afterExpiry))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class);

        verify(workspaces, never()).save(any());
        verify(appSources, never()).updateReplicaIfLease(any(), any(), any());
    }

    private AppSourceOperation operation() {
        return new AppSourceOperation(
                "op-1", new ApplicationId("app-1"), REPOSITORY_ID, 1L, 2L,
                new UserId("user-1"), AppSourceOperationType.UPDATE, "hash",
                AppSourceOperationStatus.RUNNING, "trace-1", NOW.minusSeconds(60), null);
    }

    private AppSourceOperation retryOperation() {
        return new AppSourceOperation(
                "op-retry", new ApplicationId("app-1"), REPOSITORY_ID, 2L, 2L,
                new UserId("user-1"), AppSourceOperationType.RETRY_REPLICAS, "retry-hash",
                AppSourceOperationStatus.RUNNING, "trace-retry", NOW.minusSeconds(60), null);
    }

    private AppSourceRepositorySlot slot(Long active, Long pending, long version) {
        return new AppSourceRepositorySlot(
                REPOSITORY_ID, active, pending, 3L, "op-1", version,
                NOW.minusSeconds(120), NOW.minusSeconds(60));
    }

    private AppSourceSnapshot snapshot(long generation, AppSourceSnapshotStatus status, String indexSha) {
        return new AppSourceSnapshot(
                REPOSITORY_ID, generation, "orders", AppSourcePurpose.TEAM, new UserId("user-1"),
                "main", "deadbeef", List.of(new AppSourceSelectedPath(".", AppSourcePathType.DIRECTORY)),
                indexSha, NOW.minusSeconds(60), NOW.minusSeconds(60).plusSeconds(7200), status,
                NOW.minusSeconds(60), NOW.minusSeconds(60));
    }

    private AppSourceReplica replica(
            long generation, LinuxServerId serverId, AppSourceReplicaStatus status, WorkspaceId workspaceId) {
        return new AppSourceReplica(
                REPOSITORY_ID, generation, serverId, workspaceId, status,
                status == AppSourceReplicaStatus.RUNNING ? "lease" : null,
                status == AppSourceReplicaStatus.RUNNING ? NOW.plusSeconds(300) : null,
                1, null, null, null, NOW.minusSeconds(60), NOW);
    }

    private Workspace workspace(String id, WorkspaceStatus status) {
        return new Workspace(
                new WorkspaceId(id), "orders", "appsource:orders", status,
                NOW.minusSeconds(60), NOW.minusSeconds(60), SERVER_A.value(), "trace-1");
    }

    private AppSourceOperationStep legacyPendingStep(LinuxServerId serverId) {
        return new AppSourceOperationStep(
                "legacy:" + serverId.value(), "op-1", AppSourceStepScope.SERVER, serverId,
                "RETRY_QUEUED", 0, AppSourceStepStatus.PENDING,
                "等待旧版重试", null, null, NOW.minusSeconds(60));
    }

    private AppSourceOperationStep retryStep(
            String operationId, LinuxServerId serverId, AppSourceStepStatus status) {
        Instant startedAt = status == AppSourceStepStatus.PENDING ? null : NOW.minusSeconds(1);
        Instant completedAt = status == AppSourceStepStatus.PENDING ? null : NOW;
        return new AppSourceOperationStep(
                operationId + ":" + serverId.value(), operationId, AppSourceStepScope.SERVER, serverId,
                "QUEUED", 0, status, "低敏步骤摘要", startedAt, completedAt, NOW);
    }
}
