package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.run.RunDiffCounts;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeReplay;
import com.enterprise.testagent.domain.run.RunRuntimeSnapshot;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunSummaryPersistencePort;
import com.enterprise.testagent.domain.run.RunTerminalProjection;
import com.enterprise.testagent.domain.run.RunTerminalProjectionPending;
import com.enterprise.testagent.domain.run.RunTerminalProjectionResult;
import com.enterprise.testagent.domain.run.RunTerminalRetry;
import com.enterprise.testagent.domain.run.RunTerminalRetryState;
import com.enterprise.testagent.domain.run.RunTerminalRetryStore;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.run.summary.RunConversationSummarizer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 验证关系型终态事务失败时只把已清洗投影放入 Redis 重试队列。 */
class RunTerminalProjectionPendingDbTest {

    private static final Instant NOW = Instant.parse("2026-07-11T02:00:00Z");
    private static final RunId RUN_ID = new RunId("run_terminal_pending_db");

    @Test
    void marksTerminalPendingDbAndSavesSanitizedProjection() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort persistence = mock(RunSummaryPersistencePort.class);
        RunTerminalRetryStore retryStore = mock(RunTerminalRetryStore.class);
        RunRuntimeManifest manifest = manifest();
        RunTerminalProjectionPending pending = pending();
        when(runtimeStore.findTerminalProjectionPending(RUN_ID)).thenReturn(Optional.of(pending));
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.of(manifest));
        when(runtimeStore.findInput(RUN_ID)).thenReturn(Optional.of(new RunRuntimeInput(
                RUN_ID,
                "执行回归 secret=raw-secret <context>内部上下文</context>",
                List.of(),
                "msg_dispatch_pending",
                NOW.minusSeconds(10))));
        when(runtimeStore.replayAfter(RUN_ID, 0, RunRuntimeStore.MAX_DURABLE_EVENTS))
                .thenReturn(new RunRuntimeReplay(
                        manifest,
                        new RunRuntimeSnapshot(RUN_ID, 3, 3, 0, List.of(), NOW),
                        List.of(),
                        false,
                        null));
        when(runtimeStore.diffCounts(RUN_ID)).thenReturn(RunDiffCounts.empty());
        when(persistence.persistTerminal(any())).thenThrow(new IllegalStateException("database unavailable"));
        RunTerminalProjectionService service = new RunTerminalProjectionService(
                runtimeStore,
                persistence,
                new RunConversationSummarizer(),
                retryStore,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RunTerminalProjectionResult result = service.project(pending);

        assertThat(result).isEqualTo(RunTerminalProjectionResult.TERMINAL_PENDING_DB);
        ArgumentCaptor<RunTerminalRetry> retryCaptor = ArgumentCaptor.forClass(RunTerminalRetry.class);
        verify(retryStore).save(retryCaptor.capture());
        RunTerminalRetry retry = retryCaptor.getValue();
        assertThat(retry.terminalProjectionVersion()).isEqualTo(7L);
        assertThat(retry.state()).isEqualTo(RunTerminalRetryState.TERMINAL_PENDING_DB);
        assertThat(retry.nextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        RunTerminalProjection projection = retry.projection();
        assertThat(projection.safeErrorMessage()).contains("[REDACTED]").doesNotContain("raw-secret");
        assertThat(projection.summaries()).allSatisfy(summary ->
                assertThat(summary.content()).doesNotContain("raw-secret", "内部上下文"));
        verify(runtimeStore, never()).ackTerminalProjection(RUN_ID, 7L);
    }

    @Test
    void acknowledgesOutboxAfterPostgresqlApplied() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort persistence = mock(RunSummaryPersistencePort.class);
        RunRuntimeManifest manifest = manifest();
        RunTerminalProjectionPending pending = pending();
        when(runtimeStore.findTerminalProjectionPending(RUN_ID)).thenReturn(Optional.of(pending));
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.of(manifest));
        when(runtimeStore.findInput(RUN_ID)).thenReturn(Optional.of(new RunRuntimeInput(
                RUN_ID, "执行回归", List.of(), "msg_dispatch_pending", NOW.minusSeconds(10))));
        when(runtimeStore.replayAfter(RUN_ID, 0, RunRuntimeStore.MAX_DURABLE_EVENTS))
                .thenReturn(new RunRuntimeReplay(
                        manifest,
                        new RunRuntimeSnapshot(RUN_ID, 3, 3, 0, List.of(), NOW),
                        List.of(),
                        false,
                        null));
        when(runtimeStore.diffCounts(RUN_ID)).thenReturn(RunDiffCounts.empty());
        when(persistence.persistTerminal(any())).thenReturn(RunTerminalProjectionResult.APPLIED);
        RunTerminalProjectionService service = new RunTerminalProjectionService(
                runtimeStore, persistence, new RunConversationSummarizer());

        assertThat(service.project(pending)).isEqualTo(RunTerminalProjectionResult.APPLIED);

        verify(runtimeStore).ackTerminalProjection(RUN_ID, 7L);
    }

    private RunTerminalProjectionPending pending() {
        return new RunTerminalProjectionPending(
                RUN_ID,
                "server-terminal",
                7L,
                RunStatus.SUCCEEDED,
                "REMOTE_ROOT",
                "COMPLETED",
                "Bearer raw-secret",
                false,
                "trace_terminal_pending_db",
                NOW);
    }

    private RunRuntimeManifest manifest() {
        return new RunRuntimeManifest(
                RUN_ID,
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_terminal_pending_db"),
                new SessionId("ses_terminal_pending_db"),
                new WorkspaceId("wrk_terminal_pending_db"),
                "opencode",
                "request-terminal-pending",
                "msg_dispatch_pending",
                "server-terminal",
                "bjp_terminal",
                "node_ocp_terminal",
                "ocp_terminal",
                "remote-root",
                RunStatus.SUCCEEDED,
                2,
                3,
                1,
                0,
                false,
                3,
                1_024,
                null,
                null,
                null,
                NOW.plusSeconds(86_400),
                NOW.minusSeconds(20),
                NOW);
    }
}
