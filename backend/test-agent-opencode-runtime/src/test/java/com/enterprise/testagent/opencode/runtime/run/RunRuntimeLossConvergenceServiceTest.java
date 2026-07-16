package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentCancelCommand;
import com.enterprise.testagent.agent.runtime.AgentCancelResult;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunSummaryPersistencePort;
import com.enterprise.testagent.domain.run.RunSummaryStatus;
import com.enterprise.testagent.domain.run.RunTerminalProjection;
import com.enterprise.testagent.domain.run.RunTerminalProjectionResult;
import com.enterprise.testagent.domain.run.RunTerminalRetry;
import com.enterprise.testagent.domain.run.RunTerminalRetryStore;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class RunRuntimeLossConvergenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T08:00:00Z");
    private static final RunId RUN_ID = new RunId("run_0123456789abcdef0123456789abcdef");

    @Test
    void exposesThirtySecondGraceAndSkipsConvergenceWhenRedisManifestRecovered() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort persistencePort = mock(RunSummaryPersistencePort.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.of(manifest()));
        RunRuntimeLossConvergenceService service = service(runtimeStore, persistencePort);

        RunRuntimeLossConvergenceResult result = service.converge(request(), runtime, node());

        assertThat(RunRuntimeLossConvergenceService.GRACE_PERIOD).isEqualTo(Duration.ofSeconds(30));
        assertThat(result.outcome())
                .isEqualTo(RunRuntimeLossConvergenceResult.Outcome.RUNTIME_RECOVERED);
        assertThat(result.remoteCancellationAttempted()).isFalse();
        assertThat(result.remoteStopConfirmed()).isFalse();
        verifyNoInteractions(runtime, persistencePort);
    }

    @Test
    void recoveredTerminalManifestStillPersistsMissingDatabaseTerminalWithoutRemoteCancel() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort persistencePort = mock(RunSummaryPersistencePort.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.of(terminalManifest()));
        when(persistencePort.persistTerminal(any())).thenReturn(RunTerminalProjectionResult.APPLIED);
        RunRuntimeLossConvergenceService service = service(runtimeStore, persistencePort);

        RunRuntimeLossConvergenceResult result = service.converge(request(), runtime, node());

        assertThat(result.outcome()).isEqualTo(RunRuntimeLossConvergenceResult.Outcome.TERMINAL_APPLIED);
        assertThat(result.remoteCancellationAttempted()).isFalse();
        assertThat(result.remoteStopConfirmed()).isFalse();
        verifyNoInteractions(runtime);
        ArgumentCaptor<RunTerminalProjection> projection = ArgumentCaptor.forClass(RunTerminalProjection.class);
        verify(persistencePort).persistTerminal(projection.capture());
        assertThat(projection.getValue().status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(projection.getValue().terminalSource()).isEqualTo("REDIS_TERMINAL_RECOVERY");
        assertThat(projection.getValue().terminalReasonCode()).isEqualTo("RUNTIME_STATE_LOST_AFTER_TERMINAL");
        assertThat(projection.getValue().safeErrorMessage()).isNull();
        assertThat(projection.getValue().lastEventSeq()).isEqualTo(9L);
        assertThat(projection.getValue().detailsExpiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void persistsFailedProjectionWithOnlyFixedFallbackSummariesWhenRedisRemainsUnavailable() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort persistencePort = mock(RunSummaryPersistencePort.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtimeStore.findManifest(RUN_ID)).thenThrow(new IllegalStateException("redis unavailable"));
        when(runtime.cancelSession(any())).thenReturn(Mono.just(new AgentCancelResult(true)));
        when(persistencePort.persistTerminal(any())).thenReturn(RunTerminalProjectionResult.APPLIED);
        RunRuntimeLossConvergenceService service = service(runtimeStore, persistencePort);

        RunRuntimeLossConvergenceResult result = service.converge(request(), runtime, node());

        assertThat(result.outcome())
                .isEqualTo(RunRuntimeLossConvergenceResult.Outcome.TERMINAL_APPLIED);
        assertThat(result.remoteCancellationAttempted()).isTrue();
        assertThat(result.remoteStopConfirmed()).isTrue();

        ArgumentCaptor<AgentCancelCommand> cancelCommand = ArgumentCaptor.forClass(AgentCancelCommand.class);
        verify(runtime).cancelSession(cancelCommand.capture());
        assertThat(cancelCommand.getValue().remoteSessionId()).isEqualTo("remote-runtime-loss");
        assertThat(cancelCommand.getValue().directory()).isEqualTo("/srv/workspaces/runtime-loss");
        assertThat(cancelCommand.getValue().workspace()).isNull();

        ArgumentCaptor<RunTerminalProjection> projection = ArgumentCaptor.forClass(RunTerminalProjection.class);
        verify(persistencePort).persistTerminal(projection.capture());
        assertSafeRuntimeLostProjection(projection.getValue(), true);
    }

    @Test
    void remoteCancellationFailureDoesNotPreventSafeTerminalPersistence() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort persistencePort = mock(RunSummaryPersistencePort.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.empty());
        when(runtime.cancelSession(any())).thenReturn(Mono.error(new IllegalStateException("cancel failed")));
        when(persistencePort.persistTerminal(any())).thenReturn(RunTerminalProjectionResult.VERSION_CONFLICT);
        RunRuntimeLossConvergenceService service = service(runtimeStore, persistencePort);

        RunRuntimeLossConvergenceResult result = service.converge(request(), runtime, node());

        assertThat(result.outcome())
                .isEqualTo(RunRuntimeLossConvergenceResult.Outcome.TERMINAL_VERSION_CONFLICT);
        assertThat(result.remoteCancellationAttempted()).isTrue();
        assertThat(result.remoteStopConfirmed()).isFalse();
        ArgumentCaptor<RunTerminalProjection> projection = ArgumentCaptor.forClass(RunTerminalProjection.class);
        verify(persistencePort).persistTerminal(projection.capture());
        assertSafeRuntimeLostProjection(projection.getValue(), false);
    }

    @Test
    void databaseFailureQueuesOnlySafeProjectionForRetry() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort persistencePort = mock(RunSummaryPersistencePort.class);
        RunTerminalRetryStore retryStore = mock(RunTerminalRetryStore.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtimeStore.findManifest(RUN_ID)).thenThrow(new IllegalStateException("redis unavailable"));
        when(runtime.cancelSession(any())).thenReturn(Mono.just(new AgentCancelResult(false)));
        when(persistencePort.persistTerminal(any())).thenThrow(new IllegalStateException("database unavailable"));
        RunRuntimeLossConvergenceService service = new RunRuntimeLossConvergenceService(
                runtimeStore,
                persistencePort,
                retryStore,
                Clock.fixed(NOW, ZoneOffset.UTC));

        RunRuntimeLossConvergenceResult result = service.converge(request(), runtime, node());

        assertThat(result.outcome())
                .isEqualTo(RunRuntimeLossConvergenceResult.Outcome.TERMINAL_PENDING_DB);
        assertThat(result.remoteCancellationAttempted()).isTrue();
        assertThat(result.remoteStopConfirmed()).isFalse();
        verify(runtimeStore, never()).findInput(any());
        verify(runtimeStore, never()).replayAfter(any(), anyLong(), anyInt());
        ArgumentCaptor<RunTerminalRetry> retry = ArgumentCaptor.forClass(RunTerminalRetry.class);
        verify(retryStore).save(retry.capture());
        assertSafeRuntimeLostProjection(retry.getValue().projection(), false);
    }

    @Test
    void skipsRemoteCancellationWhenSessionCreationNeverCompleted() {
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort persistencePort = mock(RunSummaryPersistencePort.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtimeStore.findManifest(RUN_ID)).thenThrow(new IllegalStateException("redis unavailable"));
        when(persistencePort.persistTerminal(any())).thenReturn(RunTerminalProjectionResult.APPLIED);
        RunRuntimeLossConvergenceService service = service(runtimeStore, persistencePort);
        RunRuntimeLossRequest request = new RunRuntimeLossRequest(
                RUN_ID,
                new SessionId("ses_runtime_loss"),
                new UserId("usr_runtime_loss"),
                "opencode",
                "msg_dispatch_runtime_loss",
                null,
                "/srv/workspaces/runtime-loss",
                ConversationSourceType.MANUAL,
                null,
                "trace_runtime_loss");

        RunRuntimeLossConvergenceResult result = service.converge(request, runtime, node());

        assertThat(result.outcome()).isEqualTo(RunRuntimeLossConvergenceResult.Outcome.TERMINAL_APPLIED);
        assertThat(result.remoteCancellationAttempted()).isFalse();
        assertThat(result.remoteStopConfirmed()).isFalse();
        verifyNoInteractions(runtime);
        ArgumentCaptor<RunTerminalProjection> projection = ArgumentCaptor.forClass(RunTerminalProjection.class);
        verify(persistencePort).persistTerminal(projection.capture());
        assertThat(projection.getValue().rootRemoteSessionId()).isNull();
    }

    private RunRuntimeLossConvergenceService service(
            RunRuntimeStore runtimeStore,
            RunSummaryPersistencePort persistencePort) {
        return new RunRuntimeLossConvergenceService(
                runtimeStore,
                persistencePort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RunRuntimeLossRequest request() {
        return new RunRuntimeLossRequest(
                RUN_ID,
                new SessionId("ses_runtime_loss"),
                new UserId("usr_runtime_loss"),
                "opencode",
                "msg_dispatch_runtime_loss",
                "remote-runtime-loss",
                "/srv/workspaces/runtime-loss",
                ConversationSourceType.SCHEDULED_TASK,
                "task_runtime_loss",
                "trace_runtime_loss");
    }

    private ExecutionNode node() {
        return new ExecutionNode(
                new ExecutionNodeId("node_runtime_loss"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                4,
                100,
                NOW,
                Set.of("opencode"),
                NOW.minusSeconds(3600),
                NOW,
                "trace_runtime_loss");
    }

    private RunRuntimeManifest manifest() {
        return new RunRuntimeManifest(
                RUN_ID,
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_runtime_loss"),
                new SessionId("ses_runtime_loss"),
                new WorkspaceId("wrk_runtime_loss"),
                "opencode",
                "request_runtime_loss",
                "msg_dispatch_runtime_loss",
                "server-a",
                "backend-a",
                "node_runtime_loss",
                "process-runtime-loss",
                "remote-runtime-loss",
                RunStatus.RUNNING,
                1,
                0,
                0,
                0,
                false,
                0,
                0,
                null,
                null,
                null,
                NOW.plusSeconds(3600),
                NOW.minusSeconds(60),
                NOW.minusSeconds(30));
    }

    private RunRuntimeManifest terminalManifest() {
        return new RunRuntimeManifest(
                RUN_ID,
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_runtime_loss"),
                new SessionId("ses_runtime_loss"),
                new WorkspaceId("wrk_runtime_loss"),
                "opencode",
                "request_runtime_loss",
                "msg_dispatch_runtime_loss",
                "server-a",
                "backend-a",
                "node_runtime_loss",
                "process-runtime-loss",
                "remote-runtime-loss",
                RunStatus.SUCCEEDED,
                2,
                9,
                1,
                0,
                false,
                9,
                4096,
                null,
                null,
                null,
                NOW.plusSeconds(3600),
                NOW.minusSeconds(60),
                NOW.minusSeconds(1));
    }

    private void assertSafeRuntimeLostProjection(RunTerminalProjection projection, boolean remoteStopConfirmed) {
        assertThat(projection.runId()).isEqualTo(RUN_ID);
        assertThat(projection.status()).isEqualTo(RunStatus.FAILED);
        assertThat(projection.expectedStatusVersion()).isEqualTo(1L);
        assertThat(projection.terminalSource()).isEqualTo("RUNTIME_STATE_LOST");
        assertThat(projection.terminalReasonCode()).isEqualTo("RUNTIME_STATE_LOST");
        assertThat(projection.safeErrorMessage()).isEqualTo("运行态不可用，当前对话已安全终止");
        assertThat(projection.remoteStopConfirmed()).isEqualTo(remoteStopConfirmed);
        assertThat(projection.lastEventSeq()).isZero();
        assertThat(projection.detailsExpiresAt()).isEqualTo(NOW);
        assertThat(projection.updatedAt()).isEqualTo(NOW);
        assertThat(projection.rootRemoteSessionId()).isEqualTo("remote-runtime-loss");
        assertThat(projection.lastRemoteMessageId()).isNull();
        assertThat(projection.lastRemotePartId()).isNull();
        assertThat(projection.tokenUsage().isEmpty()).isTrue();
        assertThat(projection.costUsd()).isNull();
        assertThat(projection.sourceType()).isEqualTo(ConversationSourceType.SCHEDULED_TASK);
        assertThat(projection.sourceRefId()).isEqualTo("task_runtime_loss");
        assertThat(projection.summaries())
                .extracting(summary -> summary.role())
                .containsExactly(SessionMessageRole.USER, SessionMessageRole.ASSISTANT);
        assertThat(projection.summaries())
                .allSatisfy(summary -> assertThat(summary.summaryStatus()).isEqualTo(RunSummaryStatus.FALLBACK));
        assertThat(projection.summaries())
                .extracting(summary -> summary.content())
                .containsExactly("用户请求摘要不可用", "助手回答摘要不可用");
        assertThat(projection.summaries())
                .extracting(summary -> summary.remoteMessageId())
                .containsOnlyNulls();
    }
}
