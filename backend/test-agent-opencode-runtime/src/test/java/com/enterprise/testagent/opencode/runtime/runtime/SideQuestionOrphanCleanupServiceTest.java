package com.enterprise.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.NullNode;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.agent.AgentSessionBindingRepository;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeRepository;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionRepository;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

/** 验证旁路孤儿回收只使用内部映射的原执行节点，并以终态 CAS 保证重复执行幂等。 */
class SideQuestionOrphanCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T04:00:00Z");
    private static final String TRACE_ID = "trace_sideorphan1234567890";
    private static final RunId RUN_ID = new RunId("run_sideorphan1234567890");
    private static final SessionId SESSION_ID = new SessionId("ses_sideorphan1234567890");

    @Test
    void deletesMappedTemporarySessionOnOriginalNodeThenWritesSingleFailedTerminal() {
        RunRepository runs = mock(RunRepository.class);
        AgentRuntimeTargetResolver targets = mock(AgentRuntimeTargetResolver.class);
        SideQuestionTerminalService terminal = mock(SideQuestionTerminalService.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        ExecutionNode originalNode = node("node_sideorphan_original01", "http://original:4096");
        Run stale = staleRun();
        when(runs.findStaleActiveSideQuestionRuns(NOW.minusSeconds(600), 200)).thenReturn(List.of(stale));
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(stale));
        when(targets.mappedSideQuestionSessionTarget("opencode", SESSION_ID, TRACE_ID))
                .thenReturn(new AgentRuntimeTargetResolver.SessionRuntimeTarget(
                        runtime, originalNode, "/workspace/original", "ses_remote_sideorphan01"));
        when(runtime.runtime(any())).thenReturn(Mono.just(new AgentRuntimeResult(NullNode.getInstance())));
        when(terminal.fail(RUN_ID, SideQuestionOrphanCleanupService.ORPHAN_MESSAGE, TRACE_ID)).thenReturn(true);
        SideQuestionOrphanCleanupService service = service(runs, targets, terminal);

        SideQuestionOrphanCleanupService.Result result = service.cleanup(TRACE_ID, () -> false);

        assertThat(result.cleanedCount()).isEqualTo(1);
        ArgumentCaptor<AgentRuntimeCommand> command = ArgumentCaptor.forClass(AgentRuntimeCommand.class);
        verify(runtime).runtime(command.capture());
        assertThat(command.getValue().node()).isEqualTo(originalNode);
        assertThat(command.getValue().method()).isEqualTo("DELETE");
        assertThat(command.getValue().path()).isEqualTo("/session/ses_remote_sideorphan01");
        assertThat(command.getValue().directory()).isEqualTo("/workspace/original");
        verify(terminal).fail(RUN_ID, SideQuestionOrphanCleanupService.ORPHAN_MESSAGE, TRACE_ID);
    }

    @Test
    void terminalCasAndReloadMakeRepeatedCleanupIdempotent() {
        RunRepository runs = mock(RunRepository.class);
        AgentRuntimeTargetResolver targets = mock(AgentRuntimeTargetResolver.class);
        SideQuestionTerminalService terminal = mock(SideQuestionTerminalService.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        Run stale = staleRun();
        Run failed = stale.fail(NOW.minusSeconds(1));
        when(runs.findStaleActiveSideQuestionRuns(NOW.minusSeconds(600), 200)).thenReturn(List.of(stale));
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(stale), Optional.of(failed));
        when(targets.mappedSideQuestionSessionTarget("opencode", SESSION_ID, TRACE_ID))
                .thenReturn(new AgentRuntimeTargetResolver.SessionRuntimeTarget(
                        runtime, node("node_sideorphan_original01", "http://original:4096"),
                        "/workspace/original", "ses_remote_sideorphan01"));
        when(runtime.runtime(any())).thenReturn(Mono.just(new AgentRuntimeResult(NullNode.getInstance())));
        when(terminal.fail(any(), any(), any())).thenReturn(true);
        SideQuestionOrphanCleanupService service = service(runs, targets, terminal);

        assertThat(service.cleanup(TRACE_ID, () -> false).cleanedCount()).isEqualTo(1);
        assertThat(service.cleanup(TRACE_ID, () -> false).cleanedCount()).isZero();

        verify(runtime, times(1)).runtime(any());
        verify(terminal, times(1)).fail(RUN_ID, SideQuestionOrphanCleanupService.ORPHAN_MESSAGE, TRACE_ID);
    }

    @Test
    void deleteFailureLeavesRunActiveSoNextScanCanRetry() {
        RunRepository runs = mock(RunRepository.class);
        AgentRuntimeTargetResolver targets = mock(AgentRuntimeTargetResolver.class);
        SideQuestionTerminalService terminal = mock(SideQuestionTerminalService.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        Run stale = staleRun();
        when(runs.findStaleActiveSideQuestionRuns(NOW.minusSeconds(600), 200)).thenReturn(List.of(stale));
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(stale));
        when(targets.mappedSideQuestionSessionTarget("opencode", SESSION_ID, TRACE_ID))
                .thenReturn(new AgentRuntimeTargetResolver.SessionRuntimeTarget(
                        runtime, node("node_sideorphan_original01", "http://original:4096"),
                        "/workspace/original", "ses_remote_sideorphan01"));
        when(runtime.runtime(any()))
                .thenReturn(Mono.error(new IllegalStateException("remote unavailable")))
                .thenReturn(Mono.just(new AgentRuntimeResult(NullNode.getInstance())));
        when(terminal.fail(any(), any(), any())).thenReturn(true);
        SideQuestionOrphanCleanupService service = service(runs, targets, terminal);

        assertThat(service.cleanup(TRACE_ID, () -> false).deleteFailedCount()).isEqualTo(1);
        assertThat(service.cleanup(TRACE_ID, () -> false).cleanedCount()).isEqualTo(1);

        verify(runtime, times(2)).runtime(any());
        verify(terminal, times(1)).fail(RUN_ID, SideQuestionOrphanCleanupService.ORPHAN_MESSAGE, TRACE_ID);
    }

    @Test
    void alreadyDeletedRemoteSessionStillConvergesRunIdempotently() {
        RunRepository runs = mock(RunRepository.class);
        AgentRuntimeTargetResolver targets = mock(AgentRuntimeTargetResolver.class);
        SideQuestionTerminalService terminal = mock(SideQuestionTerminalService.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        Run stale = staleRun();
        when(runs.findStaleActiveSideQuestionRuns(NOW.minusSeconds(600), 200)).thenReturn(List.of(stale));
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(stale));
        when(targets.mappedSideQuestionSessionTarget("opencode", SESSION_ID, TRACE_ID))
                .thenReturn(new AgentRuntimeTargetResolver.SessionRuntimeTarget(
                        runtime, node("node_sideorphan_original01", "http://original:4096"),
                        "/workspace/original", "ses_remote_sideorphan01"));
        when(runtime.runtime(any())).thenReturn(Mono.error(new PlatformException(
                ErrorCode.OPENCODE_BAD_GATEWAY,
                "opencode 服务响应异常",
                Map.of("status", 404))));
        when(terminal.fail(RUN_ID, SideQuestionOrphanCleanupService.ORPHAN_MESSAGE, TRACE_ID)).thenReturn(true);

        SideQuestionOrphanCleanupService.Result result = service(runs, targets, terminal)
                .cleanup(TRACE_ID, () -> false);

        assertThat(result.cleanedCount()).isEqualTo(1);
        assertThat(result.deleteFailedCount()).isZero();
        verify(terminal).fail(RUN_ID, SideQuestionOrphanCleanupService.ORPHAN_MESSAGE, TRACE_ID);
    }

    @Test
    void encodesPersistedRemoteSessionIdAsOneDeletePathSegment() {
        RunRepository runs = mock(RunRepository.class);
        AgentRuntimeTargetResolver targets = mock(AgentRuntimeTargetResolver.class);
        SideQuestionTerminalService terminal = mock(SideQuestionTerminalService.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        Run stale = staleRun();
        when(runs.findStaleActiveSideQuestionRuns(NOW.minusSeconds(600), 200)).thenReturn(List.of(stale));
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(stale));
        when(targets.mappedSideQuestionSessionTarget("opencode", SESSION_ID, TRACE_ID))
                .thenReturn(new AgentRuntimeTargetResolver.SessionRuntimeTarget(
                        runtime,
                        node("node_sideorphan_original01", "http://original:4096"),
                        "/workspace/original",
                        "ses_remote/side ?#"));
        when(runtime.runtime(any())).thenReturn(Mono.just(new AgentRuntimeResult(NullNode.getInstance())));
        when(terminal.fail(any(), any(), any())).thenReturn(true);

        service(runs, targets, terminal).cleanup(TRACE_ID, () -> false);

        ArgumentCaptor<AgentRuntimeCommand> command = ArgumentCaptor.forClass(AgentRuntimeCommand.class);
        verify(runtime).runtime(command.capture());
        assertThat(command.getValue().path()).isEqualTo("/session/ses_remote%2Fside%20%3F%23");
    }

    @Test
    void missingTemporaryMappingStillConvergesPlatformRunAndRecordsPossibleLeakWindow() {
        RunRepository runs = mock(RunRepository.class);
        AgentRuntimeTargetResolver targets = mock(AgentRuntimeTargetResolver.class);
        SideQuestionTerminalService terminal = mock(SideQuestionTerminalService.class);
        Run stale = staleRun();
        when(runs.findStaleActiveSideQuestionRuns(NOW.minusSeconds(600), 200)).thenReturn(List.of(stale));
        when(runs.findById(RUN_ID)).thenReturn(Optional.of(stale));
        when(targets.mappedSideQuestionSessionTarget("opencode", SESSION_ID, TRACE_ID))
                .thenThrow(new PlatformException(
                        ErrorCode.CONFLICT,
                        "Session 尚未保存远端 agent 会话映射",
                        Map.of(
                                "sessionId", SESSION_ID.value(),
                                "agentId", "opencode",
                                "reason", "REMOTE_SESSION_MAPPING_MISSING")));
        when(terminal.fail(RUN_ID, SideQuestionOrphanCleanupService.ORPHAN_MESSAGE, TRACE_ID)).thenReturn(true);

        SideQuestionOrphanCleanupService.Result result = service(runs, targets, terminal)
                .cleanup(TRACE_ID, () -> false);

        assertThat(result.mappingFailedCount()).isEqualTo(1);
        assertThat(result.cleanedCount()).isEqualTo(1);
        verify(terminal).fail(RUN_ID, SideQuestionOrphanCleanupService.ORPHAN_MESSAGE, TRACE_ID);
    }

    @Test
    void honorsCooperativeStopBeforeRemoteDelete() {
        RunRepository runs = mock(RunRepository.class);
        AgentRuntimeTargetResolver targets = mock(AgentRuntimeTargetResolver.class);
        SideQuestionTerminalService terminal = mock(SideQuestionTerminalService.class);
        when(runs.findStaleActiveSideQuestionRuns(NOW.minusSeconds(600), 200)).thenReturn(List.of(staleRun()));
        AtomicBoolean stop = new AtomicBoolean(true);

        SideQuestionOrphanCleanupService.Result result = service(runs, targets, terminal)
                .cleanup(TRACE_ID, stop::get);

        assertThat(result.stopped()).isTrue();
        verify(targets, never()).mappedSideQuestionSessionTarget(any(), any(), any());
        verify(terminal, never()).fail(any(), any(), any());
    }

    @Test
    void mappedTargetResolverUsesPersistedInternalBindingAndOriginalNode() {
        WorkspaceId workspaceId = new WorkspaceId("wrk_sideorphan1234567890");
        ExecutionNode originalNode = node("node_sideorphan_original01", "http://original:4096");
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.agentId()).thenReturn("opencode");
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        ExecutionNodeRepository nodes = mock(ExecutionNodeRepository.class);
        AgentSessionBindingRepository bindings = mock(AgentSessionBindingRepository.class);
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(
                new Workspace(workspaceId, "orphan", "/workspace/original", NOW)));
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(new Session(
                        SESSION_ID,
                        workspaceId,
                        "宠物旁路问答（内部）",
                        SessionStatus.ARCHIVED,
                        NOW.minusSeconds(900),
                        NOW.minusSeconds(700),
                        TRACE_ID)
                .withSource(ConversationSourceType.SIDE_QUESTION, "ses_sideorphan_main000001",
                        new UserId("usr_sideorphan1234567890"))));
        AgentSessionBinding persisted = new AgentSessionBinding(
                SESSION_ID,
                "opencode",
                "ses_remote_sideorphan01",
                originalNode.executionNodeId(),
                NOW.minusSeconds(700),
                NOW.minusSeconds(700),
                TRACE_ID);
        when(bindings.findBySessionIdAndAgentId(SESSION_ID, "opencode")).thenReturn(Optional.of(persisted));
        when(nodes.findById(originalNode.executionNodeId())).thenReturn(Optional.of(originalNode));
        AgentRuntimeTargetResolver resolver = new AgentRuntimeTargetResolver(
                workspaces,
                sessions,
                nodes,
                new AgentRuntimeRegistry(List.of(runtime)),
                bindings,
                null);

        AgentRuntimeTargetResolver.SessionRuntimeTarget target =
                resolver.mappedSideQuestionSessionTarget("opencode", SESSION_ID, TRACE_ID);

        assertThat(target.node()).isEqualTo(originalNode);
        assertThat(target.remoteSessionId()).isEqualTo("ses_remote_sideorphan01");
        assertThat(target.directory()).isEqualTo("/workspace/original");
        verify(nodes).findById(originalNode.executionNodeId());
    }

    private SideQuestionOrphanCleanupService service(
            RunRepository runs,
            AgentRuntimeTargetResolver targets,
            SideQuestionTerminalService terminal) {
        return new SideQuestionOrphanCleanupService(
                runs,
                targets,
                terminal,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Run staleRun() {
        return new Run(
                        RUN_ID,
                        SESSION_ID,
                        new WorkspaceId("wrk_sideorphan1234567890"),
                        RunStatus.RUNNING,
                        NOW.minusSeconds(900),
                        NOW.minusSeconds(700),
                        TRACE_ID)
                .withSource(
                        ConversationSourceType.SIDE_QUESTION,
                        "ses_sideorphan_main000001",
                        new UserId("usr_sideorphan1234567890"));
    }

    private ExecutionNode node(String nodeId, String baseUrl) {
        return new ExecutionNode(
                new ExecutionNodeId(nodeId),
                baseUrl,
                ExecutionNodeStatus.READY,
                0,
                5,
                NOW);
    }
}
