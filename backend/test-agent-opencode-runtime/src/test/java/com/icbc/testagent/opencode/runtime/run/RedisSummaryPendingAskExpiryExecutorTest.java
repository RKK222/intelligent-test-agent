package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.agent.runtime.AgentCancelResult;
import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunOwnerLease;
import com.icbc.testagent.domain.run.RunRuntimeInput;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.event.RunEventAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class RedisSummaryPendingAskExpiryExecutorTest {

    private static final Instant NOW = Instant.parse("2026-07-11T09:00:00Z");
    private static final RunId RUN_ID = new RunId("run_pending_expiry_executor");

    @Test
    void cancelsAndPersistsTerminalOnlyWhileFencingLeaseIsOwned() {
        RunRuntimeStore store = mock(RunRuntimeStore.class);
        AgentRuntimeRegistry runtimes = mock(AgentRuntimeRegistry.class);
        RunRecoveryExecutionNodeResolver nodes = mock(RunRecoveryExecutionNodeResolver.class);
        RunOwnerLeaseSupervisor leases = mock(RunOwnerLeaseSupervisor.class);
        RunEventAppender appender = mock(RunEventAppender.class);
        RunTerminalProjectionService projection = mock(RunTerminalProjectionService.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        ExecutionNode node = mock(ExecutionNode.class);
        RunOwnerLease lease = new RunOwnerLease(RUN_ID, "bjp_owner", 7L, NOW.plusSeconds(15));
        RunOwnerLeaseSupervisor.OwnershipHandle ownership =
                mock(RunOwnerLeaseSupervisor.OwnershipHandle.class);
        RunRuntimeManifest manifest = manifest();
        RunRuntimeInput input = new RunRuntimeInput(
                RUN_ID, "不得读取该原文", List.of(), "msg_pending", NOW.minusSeconds(60),
                "/srv/workspaces/pending", "http://127.0.0.1:4096");
        when(store.findInput(RUN_ID)).thenReturn(Optional.of(input));
        when(nodes.resolve(manifest.executionNodeId(), input.executionNodeBaseUrl()))
                .thenReturn(Optional.of(node));
        when(leases.adopt(lease)).thenReturn(Optional.of(ownership));
        when(ownership.lease()).thenReturn(lease);
        when(runtimes.require("opencode")).thenReturn(runtime);
        when(runtime.cancelSession(any())).thenReturn(Mono.just(new AgentCancelResult(true)));
        RedisSummaryPendingAskExpiryExecutor executor = new RedisSummaryPendingAskExpiryExecutor(
                store, runtimes, nodes, leases, appender, projection,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(executor.expirePendingAsk(manifest, lease, "trace_pending_expiry")).isTrue();

        verify(leases, times(2)).requireOwned(ownership);
        ArgumentCaptor<RunEventDraft> event = ArgumentCaptor.forClass(RunEventDraft.class);
        verify(appender).append(
                event.capture(),
                org.mockito.Mockito.eq(RunStorageMode.REDIS_SUMMARY),
                org.mockito.Mockito.eq(lease));
        assertThat(event.getValue().type()).isEqualTo(com.icbc.testagent.domain.event.RunEventType.RUN_CANCELLED);
        assertThat(event.getValue().payload()).containsEntry("reason", "PENDING_ASK_EXPIRED");
        verify(projection).project(
                RUN_ID,
                RunStatus.CANCELLED,
                "PENDING_ASK_EXPIRED",
                "PENDING_ASK_EXPIRED",
                "等待用户处理已超过 7 天，当前对话已安全终止",
                true,
                "trace_pending_expiry");
        verify(leases).release(ownership);
    }

    private RunRuntimeManifest manifest() {
        return new RunRuntimeManifest(
                RUN_ID, RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_pending_expiry"), new SessionId("ses_pending_expiry"),
                new WorkspaceId("wrk_pending_expiry"), "opencode", "request_pending_expiry",
                "msg_pending", "server-a", "bjp_old", "node_pending_expiry",
                "ocp_pending_expiry", "remote-pending-expiry", RunStatus.RUNNING,
                1L, 2L, 1L, 0L, false, 2L, 512L,
                "QUESTION", "evt_question", NOW.minus(java.time.Duration.ofDays(7)),
                NOW.plusSeconds(300), NOW.minus(java.time.Duration.ofDays(7)), NOW.minusSeconds(60));
    }
}
