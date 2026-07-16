package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.run.RunDiffCounts;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunSummaryPersistencePort;
import com.enterprise.testagent.domain.run.RunTerminalProjection;
import com.enterprise.testagent.domain.run.RunTerminalProjectionResult;
import com.enterprise.testagent.domain.run.RunTerminalRetry;
import com.enterprise.testagent.domain.run.RunTerminalRetryStore;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.TokenUsage;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRunId;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.scheduler.ScheduledTaskContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证终态投影重试的幂等收敛、失败退避和 scheduler 接入。 */
class RunTerminalProjectionRetryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T02:00:05Z");
    private static final RunId RUN_ID = new RunId("run_terminal_retry_service");

    @Test
    void deletesRetryAfterAppliedOrVersionConflict() {
        RunTerminalRetryStore store = mock(RunTerminalRetryStore.class);
        RunSummaryPersistencePort persistence = mock(RunSummaryPersistencePort.class);
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunTerminalRetry applied = pending(NOW.minusSeconds(5), RUN_ID.value(), 7L);
        RunTerminalRetry conflict = pending(NOW.minusSeconds(6), "run_terminal_retry_conflict", 8L);
        when(store.findDue(NOW, 200)).thenReturn(List.of(applied, conflict));
        when(persistence.persistTerminal(applied.projection())).thenReturn(RunTerminalProjectionResult.APPLIED);
        when(persistence.persistTerminal(conflict.projection()))
                .thenReturn(RunTerminalProjectionResult.VERSION_CONFLICT);
        RunTerminalProjectionRetryService service = new RunTerminalProjectionRetryService(
                store, persistence, runtimeStore, Clock.fixed(NOW, ZoneOffset.UTC));

        RunTerminalProjectionRetryService.Result result = service.retryDue(() -> false);

        assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(result.versionConflictCount()).isEqualTo(1);
        verify(store).delete(applied);
        verify(store).delete(conflict);
        verify(runtimeStore).ackTerminalProjection(applied.projection().runId(), 7L);
        verify(runtimeStore).ackTerminalProjection(conflict.projection().runId(), 8L);
    }

    @Test
    void reschedulesDatabaseFailureWithNextStrictDelay() {
        RunTerminalRetryStore store = mock(RunTerminalRetryStore.class);
        RunSummaryPersistencePort persistence = mock(RunSummaryPersistencePort.class);
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunTerminalRetry due = pending(NOW.minusSeconds(5), RUN_ID.value(), 7L);
        when(store.findDue(NOW, 200)).thenReturn(List.of(due));
        when(persistence.persistTerminal(any())).thenThrow(new IllegalStateException("db unavailable"));
        RunTerminalProjectionRetryService service = new RunTerminalProjectionRetryService(
                store, persistence, runtimeStore, Clock.fixed(NOW, ZoneOffset.UTC));

        RunTerminalProjectionRetryService.Result result = service.retryDue(() -> false);

        assertThat(result.rescheduledCount()).isEqualTo(1);
        ArgumentCaptor<RunTerminalRetry> captor = ArgumentCaptor.forClass(RunTerminalRetry.class);
        verify(store).save(captor.capture());
        assertThat(captor.getValue().failedAttempts()).isEqualTo(2);
        assertThat(captor.getValue().nextAttemptAt()).isEqualTo(NOW.plusSeconds(15));
        verify(store, never()).delete(due);
        verify(runtimeStore, never()).ackTerminalProjection(RUN_ID, 7L);
    }

    @Test
    void deletesExpiredRetryWithoutTouchingPostgresql() {
        RunTerminalRetryStore store = mock(RunTerminalRetryStore.class);
        RunSummaryPersistencePort persistence = mock(RunSummaryPersistencePort.class);
        RunTerminalRetry expired = pending(NOW.minusSeconds(5));
        expired = new RunTerminalRetry(
                expired.projection(),
                expired.state(),
                expired.failedAttempts(),
                NOW.minus(Duration.ofHours(24)),
                NOW.minusSeconds(1),
                NOW);
        when(store.findDue(NOW, 200)).thenReturn(List.of(expired));
        RunTerminalProjectionRetryService service = new RunTerminalProjectionRetryService(
                store, persistence, Clock.fixed(NOW, ZoneOffset.UTC));

        RunTerminalProjectionRetryService.Result result = service.retryDue(() -> false);

        assertThat(result.expiredCount()).isEqualTo(1);
        verify(store).delete(expired);
        verify(persistence, never()).persistTerminal(any());
    }

    @Test
    void handlerRegistersFiveSecondScanAndReportsStructuredCounts() {
        RunTerminalProjectionRetryService service = mock(RunTerminalProjectionRetryService.class);
        when(service.retryDue(any())).thenReturn(new RunTerminalProjectionRetryService.Result(3, 1, 1, 1, 0));
        RunTerminalProjectionRetryTaskHandler handler = new RunTerminalProjectionRetryTaskHandler(service);

        assertThat(handler.taskKey())
                .isEqualTo(new ScheduledTaskKey("opencode-runtime.terminal-projection-retry"));
        assertThat(handler.cronExpression()).isEqualTo("*/5 * * * * *");
        assertThat(handler.lockTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(handler.run(context()).result())
                .containsEntry("scannedCount", 3)
                .containsEntry("rescheduledCount", 1);
    }

    private RunTerminalRetry pending(Instant failedAt) {
        return pending(failedAt, RUN_ID.value());
    }

    private RunTerminalRetry pending(Instant failedAt, String runId) {
        return RunTerminalRetry.pending(projection(new RunId(runId), failedAt), failedAt);
    }

    private RunTerminalRetry pending(Instant failedAt, String runId, long terminalProjectionVersion) {
        return RunTerminalRetry.pending(
                projection(new RunId(runId), failedAt), failedAt, terminalProjectionVersion);
    }

    private RunTerminalProjection projection(RunId runId, Instant failedAt) {
        return new RunTerminalProjection(
                runId,
                new SessionId("ses_terminal_retry_service"),
                RunStatus.SUCCEEDED,
                1,
                "REMOTE_ROOT",
                "COMPLETED",
                null,
                false,
                3,
                failedAt.plus(Duration.ofHours(24)),
                "remote-root",
                RunDiffCounts.empty(),
                null,
                null,
                TokenUsage.empty(),
                BigDecimal.ZERO,
                "trace_terminal_retry_service",
                failedAt,
                "opencode",
                ConversationSourceType.MANUAL,
                null,
                new UserId("usr_terminal_retry_service"),
                List.of());
    }

    private ScheduledTaskContext context() {
        return new ScheduledTaskContext(
                new ScheduledTaskRunId("str_terminal_retry_service"),
                new ScheduledTaskKey("opencode-runtime.terminal-projection-retry"),
                null,
                ScheduledTaskTriggerType.CRON,
                null,
                NOW,
                "trace_terminal_retry_service",
                Map.of());
    }
}
