package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.event.RunSessionScopeRepository;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunOwnerLease;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.event.RunEventAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

class RedisSummaryRunRecoveryTakeoverExecutorTest {

    private static final Instant NOW = Instant.parse("2026-07-11T08:00:00Z");
    private static final RunId RUN_ID = new RunId("run_takeover_executor");

    @Test
    void resumesSseAndProjectsTerminalWithoutCallingPromptAsync() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        RunOwnerLease lease = new RunOwnerLease(RUN_ID, "bjp_new", 2L, NOW.plusSeconds(15));
        when(store.renewOwnerLease(lease)).thenReturn(Optional.of(lease));
        when(store.findInput(RUN_ID)).thenReturn(Optional.of(new RunRuntimeInput(
                RUN_ID, "secret prompt", List.of(), "msg_dispatch", NOW,
                "/trusted/workspace", "http://127.0.0.1:4096")));
        RunRecoveryExecutionNodeResolver nodes = mock(RunRecoveryExecutionNodeResolver.class);
        when(nodes.resolve(any(), any())).thenReturn(Optional.of(mock(ExecutionNode.class)));
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.agentId()).thenReturn("opencode");
        when(runtime.streamRunEvents(any())).thenReturn(Flux.just(new RunEventDraft(
                RUN_ID, RunEventType.RUN_SUCCEEDED, "trace_takeover", NOW, Map.of("status", "SUCCEEDED"))));
        RunEventAppender appender = mock(RunEventAppender.class);
        RunTerminalProjectionService terminal = mock(RunTerminalProjectionService.class);
        RunOwnerLeaseSupervisor supervisor = new RunOwnerLeaseSupervisor(
                store, Clock.fixed(NOW, ZoneOffset.UTC));
        RedisSummaryRunRecoveryTakeoverExecutor executor = new RedisSummaryRunRecoveryTakeoverExecutor(
                store,
                new AgentRuntimeRegistry(List.of(runtime)),
                nodes,
                supervisor,
                mock(RunSessionScopeRepository.class),
                RunSessionScopeRuntimeCache.disabled(),
                appender,
                new RunEventPersistencePolicy(),
                terminal);

        assertThat(executor.resumeAcceptedRun(manifest(), lease, "trace_takeover")).isTrue();

        verify(appender, org.mockito.Mockito.timeout(2_000)).append(
                any(RunEventDraft.class), eq(RunStorageMode.REDIS_SUMMARY), eq(lease));
        verify(terminal, org.mockito.Mockito.timeout(2_000)).project(
                eq(RUN_ID), eq(RunStatus.SUCCEEDED), eq("RECOVERY_REMOTE_ROOT"),
                eq("SUCCEEDED"), eq(null), eq(false), eq("trace_takeover"));
        verify(runtime, never()).startRun(any());
        verify(store, org.mockito.Mockito.timeout(2_000)).releaseOwnerLease(any());
    }

    @Test
    void leaseRenewalRedisFailureSchedulesRuntimeLossConvergence() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        RunOwnerLease lease = new RunOwnerLease(RUN_ID, "bjp_new", 8L, NOW.plusSeconds(15));
        when(store.renewOwnerLease(lease))
                .thenReturn(Optional.of(lease))
                .thenThrow(new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "redis unavailable"));
        when(store.findInput(RUN_ID)).thenReturn(Optional.of(new RunRuntimeInput(
                RUN_ID, "secret prompt", List.of(), "msg_dispatch", NOW,
                "/trusted/workspace", "http://127.0.0.1:4096")));
        ExecutionNode node = mock(ExecutionNode.class);
        RunRecoveryExecutionNodeResolver nodes = mock(RunRecoveryExecutionNodeResolver.class);
        when(nodes.resolve(any(), any())).thenReturn(Optional.of(node));
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.agentId()).thenReturn("opencode");
        when(runtime.streamRunEvents(any())).thenReturn(Flux.never());
        RunOwnerLeaseSupervisor supervisor = new RunOwnerLeaseSupervisor(
                store, Clock.fixed(NOW, ZoneOffset.UTC));
        RunRuntimeLossConvergenceScheduler lossScheduler = mock(RunRuntimeLossConvergenceScheduler.class);
        RunEventAppender appender = mock(RunEventAppender.class);
        RunTerminalProjectionService terminal = mock(RunTerminalProjectionService.class);
        RedisSummaryRunRecoveryTakeoverExecutor executor = new RedisSummaryRunRecoveryTakeoverExecutor(
                store,
                new AgentRuntimeRegistry(List.of(runtime)),
                nodes,
                supervisor,
                mock(RunSessionScopeRepository.class),
                RunSessionScopeRuntimeCache.disabled(),
                appender,
                new RunEventPersistencePolicy(),
                terminal,
                lossScheduler);

        assertThat(executor.resumeAcceptedRun(manifest(), lease, "trace_takeover_loss")).isTrue();
        supervisor.renewOwnedLeases();

        verify(lossScheduler, org.mockito.Mockito.timeout(2_000)).schedule(
                org.mockito.ArgumentMatchers.<RunRuntimeLossRequest>argThat(request ->
                        request.runId().equals(RUN_ID)
                                && request.remoteSessionId().equals("remote-takeover")),
                any(AgentRuntime.class),
                eq(node));
        verify(appender, never()).append(
                org.mockito.ArgumentMatchers.argThat(draft -> draft.type() == RunEventType.RUN_FAILED),
                eq(RunStorageMode.REDIS_SUMMARY),
                any(RunOwnerLease.class));
        verify(terminal, never()).project(
                any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void unacceptedDispatchIsFailedWithoutCallingPromptOrRemoteCancel() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        RunOwnerLease lease = new RunOwnerLease(RUN_ID, "bjp_new", 9L, NOW.plusSeconds(15));
        when(store.renewOwnerLease(lease)).thenReturn(Optional.of(lease));
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.agentId()).thenReturn("opencode");
        RunEventAppender appender = mock(RunEventAppender.class);
        RunTerminalProjectionService terminal = mock(RunTerminalProjectionService.class);
        RunOwnerLeaseSupervisor supervisor = new RunOwnerLeaseSupervisor(
                store, Clock.fixed(NOW, ZoneOffset.UTC));
        RedisSummaryRunRecoveryTakeoverExecutor executor = new RedisSummaryRunRecoveryTakeoverExecutor(
                store,
                new AgentRuntimeRegistry(List.of(runtime)),
                mock(RunRecoveryExecutionNodeResolver.class),
                supervisor,
                mock(RunSessionScopeRepository.class),
                RunSessionScopeRuntimeCache.disabled(),
                appender,
                new RunEventPersistencePolicy(),
                terminal);

        assertThat(executor.failUnacceptedRun(manifest(), lease, "trace_not_accepted")).isTrue();

        ArgumentCaptor<RunEventDraft> draft = ArgumentCaptor.forClass(RunEventDraft.class);
        verify(appender).append(draft.capture(), eq(RunStorageMode.REDIS_SUMMARY), eq(lease));
        assertThat(draft.getValue().type()).isEqualTo(RunEventType.RUN_FAILED);
        assertThat(draft.getValue().payload())
                .containsEntry("terminalReasonCode", "DISPATCH_NOT_ACCEPTED")
                .containsEntry("remoteStopConfirmed", false);
        verify(terminal).project(
                RUN_ID,
                RunStatus.FAILED,
                "RECOVERY_DISPATCH_PROBE",
                "DISPATCH_NOT_ACCEPTED",
                "远端未接收本次请求，对话已安全终止",
                false,
                "trace_not_accepted");
        verify(runtime, never()).startRun(any());
        verify(runtime, never()).cancelSession(any());
        verify(store).releaseOwnerLease(lease);
    }

    private RunRuntimeManifest manifest() {
        return new RunRuntimeManifest(
                RUN_ID, RunStorageMode.REDIS_SUMMARY, new UserId("usr_takeover"),
                new SessionId("ses_takeover"), new WorkspaceId("wrk_takeover"), "opencode",
                "req_takeover", "msg_dispatch", "server-a", "bjp_old", "node_takeover",
                "ocp_takeover", "remote-takeover", RunStatus.RUNNING,
                1L, 2L, 1L, 0L, false, 2L, 100L,
                null, null, null, NOW.plusSeconds(3600), NOW.minusSeconds(60), NOW);
    }
}
