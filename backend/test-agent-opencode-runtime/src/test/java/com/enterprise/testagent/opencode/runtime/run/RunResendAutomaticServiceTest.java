package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentPromptPart;
import com.enterprise.testagent.agent.runtime.AgentReplayableTurn;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.run.ConversationRunContext;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.domain.run.RunResendPolicy;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendSourceTurn;
import com.enterprise.testagent.domain.run.RunResendSourceTurnQuery;
import com.enterprise.testagent.domain.run.RunResendStatus;
import com.enterprise.testagent.domain.run.RunResendTrigger;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeStore;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class RunResendAutomaticServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-07T10:00:00Z");
    private static final UserId OWNER = new UserId("usr_auto_resend");
    private static final SessionId SESSION = new SessionId("ses_auto_resend");
    private static final RunId SOURCE = new RunId("run_auto_resend_source");

    private RunResendApplicationService applicationService;
    private RunResendRepository repository;
    private RunResendSourceTurnQuery sourceTurnQuery;
    private RunRuntimeStore runtimeStore;
    private RunResendAutomaticService service;

    @BeforeEach
    void setUp() {
        applicationService = mock(RunResendApplicationService.class);
        repository = mock(RunResendRepository.class);
        sourceTurnQuery = mock(RunResendSourceTurnQuery.class);
        runtimeStore = mock(RunRuntimeStore.class);
        ConversationContextApplicationService contextService = mock(ConversationContextApplicationService.class);
        AgentRuntimeRegistry registry = mock(AgentRuntimeRegistry.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(repository.findBySourceRunId(any())).thenReturn(Optional.empty());
        when(repository.findActiveBySession(SESSION)).thenReturn(Optional.empty());
        when(repository.findByReplacementRunId(any())).thenReturn(Optional.empty());
        when(sourceTurnQuery.findLatestUserTurn(SESSION)).thenReturn(Optional.of(
                new RunResendSourceTurn(SOURCE, "msg_auto_source")));
        when(registry.defaultAgentId()).thenReturn("opencode");
        when(registry.require("opencode")).thenReturn(runtime);
        when(contextService.bootstrap(any(), eq("opencode"), eq(SESSION), any()))
                .thenReturn(new ConversationContextApplicationService.IssuedConversationContext(
                        "context-auto", context()));
        when(runtime.loadReplayableTurn(any())).thenReturn(Mono.just(new AgentReplayableTurn(
                "msg_auto_source", "retry", List.of(AgentPromptPart.text("retry")),
                "build", "openai", "gpt-5", "high")));
        RunResend reserved = resend(1, 1);
        when(applicationService.reserveAutomatic(
                any(), any(), any(), any(), any(), any(), anyInt(), anyInt(),
                any(), any(), any())).thenReturn(reserved);
        service = new RunResendAutomaticService(
                applicationService, repository, sourceTurnQuery, runtimeStore,
                contextService, registry, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void schedulesFirstAutomaticRetryAfterOneMinute() {
        assertThat(service.schedule(sourceRun(), "trace_auto")).isPresent();

        ArgumentCaptor<Instant> executeAt = ArgumentCaptor.forClass(Instant.class);
        verify(applicationService).reserveAutomatic(
                eq(OWNER), eq("opencode"), any(), eq("msg_auto_source"), any(), eq("linux-auto"),
                eq(1), eq(1), executeAt.capture(), eq("auto-resend:" + SOURCE.value()), eq("trace_auto"));
        assertThat(executeAt.getValue()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void manualAttemptDoesNotResetAutomaticLimit() {
        when(repository.findByReplacementRunId(SOURCE)).thenReturn(Optional.of(resend(4, 3)));

        assertThat(service.schedule(sourceRun(), "trace_auto")).isEmpty();

        verify(applicationService, never()).reserveAutomatic(
                any(), any(), any(), any(), any(), any(), anyInt(), anyInt(),
                any(), any(), any());
    }

    @Test
    void redisSummarySourceUsesRuntimeInputMessageBoundary() {
        when(sourceTurnQuery.findLatestUserTurn(SESSION)).thenReturn(Optional.empty());
        when(runtimeStore.findInput(SOURCE)).thenReturn(Optional.of(new RunRuntimeInput(
                SOURCE, "retry", List.of(), "msg_runtime_source", NOW.minusSeconds(30))));

        assertThat(service.schedule(sourceRun(), "trace_auto")).isPresent();

        verify(applicationService).reserveAutomatic(
                eq(OWNER), eq("opencode"), any(), eq("msg_runtime_source"), any(), eq("linux-auto"),
                eq(1), eq(1), any(), eq("auto-resend:" + SOURCE.value()), eq("trace_auto"));
    }

    @Test
    void ignoresManualAndCancelledRuns() {
        Run manual = sourceRun().withSource(ConversationSourceType.MANUAL, null, OWNER);
        Run cancelled = sourceRun().applyTerminalFact(RunStatus.CANCELLED, NOW);

        assertThat(service.schedule(manual, "trace_auto")).isEmpty();
        assertThat(service.schedule(cancelled, "trace_auto")).isEmpty();
    }

    private Run sourceRun() {
        return new Run(
                SOURCE, SESSION, new WorkspaceId("wrk_auto_resend"), RunStatus.FAILED,
                NOW.minusSeconds(30), NOW, "trace_source")
                .withSource(ConversationSourceType.SCHEDULED_TASK, "night-task-1", OWNER);
    }

    private RunResend resend(int total, int automatic) {
        return new RunResend(
                new RunResendId("rsd_auto_resend_" + total), SESSION, OWNER,
                new RunId("run_previous_" + total), new RunId("run_replacement_" + total),
                "msg_previous", "msg_replacement", RunResendTrigger.MANUAL,
                total, automatic, RunResendPolicy.MAX_AUTOMATIC_ATTEMPTS, RunResendStatus.DISPATCHED,
                NOW, "linux-auto", null, null, "request-auto-" + total, "trace_auto",
                null, NOW, NOW);
    }

    private ConversationRunContext context() {
        WorkspaceId workspaceId = new WorkspaceId("wrk_auto_resend");
        Workspace workspace = new Workspace(
                workspaceId, "auto", "/tmp/auto", WorkspaceStatus.ACTIVE,
                NOW.minusSeconds(60), NOW, "linux-auto", "trace_workspace");
        Session session = new Session(
                SESSION, workspaceId, "auto", SessionStatus.ACTIVE, NOW.minusSeconds(60), NOW,
                "trace_session", "ses_remote_auto", new ExecutionNodeId("node_process-auto"), false)
                .withSource(ConversationSourceType.SCHEDULED_TASK, "night-task-1", OWNER);
        ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_process-auto"), "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY, 0, 4, NOW);
        AgentSessionBinding binding = new AgentSessionBinding(
                SESSION, "opencode", "ses_remote_auto", node.executionNodeId(), NOW, NOW, "trace_binding");
        return new ConversationRunContext(
                OWNER, "opencode", "process-auto", "linux-auto",
                session, workspace, node, binding, 1, NOW.plusSeconds(300));
    }
}
