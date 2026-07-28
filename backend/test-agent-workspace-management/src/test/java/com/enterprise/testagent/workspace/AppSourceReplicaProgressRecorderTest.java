package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStep;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceStepStatus;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.common.error.PlatformException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppSourceReplicaProgressRecorderTest {

    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_progress");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");

    @Test
    void failureMarksCurrentFailedAndAllFollowingStepsSkippedThroughLeaseFence() {
        AppSourceRepository repository = mock(AppSourceRepository.class);
        AppSourceOperation operation = operation();
        AppSourceReplica claimed = claimed();
        List<AppSourceOperationStep> steps = AppSourceReplicaStepCatalog.pendingSteps(
                operation.operationId(), SERVER_ID, NOW.minusSeconds(30));
        when(repository.findSteps(operation.operationId())).thenReturn(steps);
        when(repository.lockReplicaLeaseForUpdate(
                REPOSITORY_ID, 2L, SERVER_ID, "worker-a", NOW)).thenReturn(true);
        when(repository.updateStepIfReplicaLease(
                any(), eq(REPOSITORY_ID), eq(2L), eq(SERVER_ID), eq("worker-a"), eq(NOW)))
                .thenReturn(true);
        AppSourceReplicaProgressRecorder recorder = new AppSourceReplicaProgressRecorder(repository);

        recorder.failCurrentAndSkipFollowing(
                operation, claimed, "worker-a", AppSourceReplicaStepCatalog.STAGING, NOW);

        ArgumentCaptor<AppSourceOperationStep> updated = ArgumentCaptor.forClass(AppSourceOperationStep.class);
        verify(repository, org.mockito.Mockito.times(10)).updateStepIfReplicaLease(
                updated.capture(), eq(REPOSITORY_ID), eq(2L), eq(SERVER_ID), eq("worker-a"), eq(NOW));
        assertThat(updated.getAllValues()).first().satisfies(step -> {
            assertThat(step.stepCode()).isEqualTo(AppSourceReplicaStepCatalog.STAGING);
            assertThat(step.status()).isEqualTo(AppSourceStepStatus.FAILED);
        });
        assertThat(updated.getAllValues().subList(1, updated.getAllValues().size()))
                .allSatisfy(step -> assertThat(step.status()).isEqualTo(AppSourceStepStatus.SKIPPED));
        assertThat(updated.getAllValues()).extracting(AppSourceOperationStep::safeSummary)
                .allSatisfy(summary -> assertThat(summary)
                        .doesNotContain("/data/", "git@", "stderr", "privateKey"));
    }

    @Test
    void newLeaseResetsWholeHistoricAttemptButOldOwnerCannotReset() {
        AppSourceRepository repository = mock(AppSourceRepository.class);
        AppSourceOperation operation = operation();
        AppSourceReplica claimed = claimed();
        List<AppSourceOperationStep> historic = AppSourceReplicaStepCatalog.pendingSteps(
                        operation.operationId(), SERVER_ID, NOW.minusSeconds(30)).stream()
                .map(step -> new AppSourceOperationStep(
                        step.stepId(), step.operationId(), step.scope(), step.linuxServerId(),
                        step.stepCode(), step.sequence(), AppSourceStepStatus.SUCCEEDED,
                        "旧 attempt 摘要", NOW.minusSeconds(20), NOW.minusSeconds(10), NOW.minusSeconds(10)))
                .toList();
        when(repository.findSteps(operation.operationId())).thenReturn(historic);
        when(repository.lockReplicaLeaseForUpdate(
                REPOSITORY_ID, 2L, SERVER_ID, "worker-new", NOW)).thenReturn(true);
        when(repository.resetStepIfReplicaLease(
                any(), eq(REPOSITORY_ID), eq(2L), eq(SERVER_ID), eq("worker-new"), eq(NOW)))
                .thenReturn(true);
        AppSourceReplicaProgressRecorder recorder = new AppSourceReplicaProgressRecorder(repository);

        recorder.resetForAttempt(operation, claimed, "worker-new", NOW);

        ArgumentCaptor<AppSourceOperationStep> reset = ArgumentCaptor.forClass(AppSourceOperationStep.class);
        verify(repository, org.mockito.Mockito.times(AppSourceReplicaStepCatalog.codes().size()))
                .resetStepIfReplicaLease(
                        reset.capture(), eq(REPOSITORY_ID), eq(2L), eq(SERVER_ID), eq("worker-new"), eq(NOW));
        assertThat(reset.getAllValues()).allSatisfy(step -> {
            assertThat(step.status()).isEqualTo(AppSourceStepStatus.PENDING);
            assertThat(step.startedAt()).isNull();
            assertThat(step.completedAt()).isNull();
            assertThat(step.safeSummary()).startsWith("等待：");
        });
        assertThatThrownBy(() -> recorder.resetForAttempt(operation, claimed, "worker-old", NOW))
                .isInstanceOf(PlatformException.class);
    }

    private AppSourceOperation operation() {
        return new AppSourceOperation(
                "op-progress", new ApplicationId("app-1"), REPOSITORY_ID, 1L, 2L,
                new UserId("user-1"), AppSourceOperationType.UPDATE, "hash",
                AppSourceOperationStatus.RUNNING, "trace-progress", NOW.minusSeconds(30), null);
    }

    private AppSourceReplica claimed() {
        return new AppSourceReplica(
                REPOSITORY_ID, 2L, SERVER_ID, null, AppSourceReplicaStatus.RUNNING,
                "worker-a", NOW.plusSeconds(60), 1, null, null, null,
                NOW.minusSeconds(30), NOW);
    }
}
