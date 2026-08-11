package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentMessageProbeResult;
import com.enterprise.testagent.agent.runtime.AgentRevertTurnResult;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.run.ConversationRunContext;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendDetailCleanupPort;
import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.domain.run.RunResendPolicy;
import com.enterprise.testagent.domain.run.RunResendReplayInput;
import com.enterprise.testagent.domain.run.RunResendReplayInputStore;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendStatus;
import com.enterprise.testagent.domain.run.RunResendTrigger;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.opencode.runtime.session.SessionMessageRealtimeHub;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunResendExecutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-07T10:00:00Z");
    private static final RunResendId RESEND_ID = new RunResendId("rsd_execution_service");
    private static final RunId SOURCE_RUN_ID = new RunId("run_execution_source");
    private static final RunId REPLACEMENT_RUN_ID = new RunId("run_execution_replacement");
    private static final SessionId SESSION_ID = new SessionId("ses_execution_service");

    private RunResendRepository resendRepository;
    private RunResendReplayInputStore inputStore;
    private RunRepository runRepository;
    private AgentRuntime runtime;
    private RunApplicationService runApplicationService;
    private RunResendDetailCleanupPort cleanupPort;
    private ConversationContextApplicationService contextService;
    private RunEventAppender eventAppender;
    private SessionMessageRealtimeHub sessionMessageRealtimeHub;
    private RunResendExecutionService service;

    @BeforeEach
    void setUp() {
        resendRepository = org.mockito.Mockito.mock(RunResendRepository.class);
        inputStore = org.mockito.Mockito.mock(RunResendReplayInputStore.class);
        runRepository = org.mockito.Mockito.mock(RunRepository.class);
        runtime = org.mockito.Mockito.mock(AgentRuntime.class);
        runApplicationService = org.mockito.Mockito.mock(RunApplicationService.class);
        cleanupPort = org.mockito.Mockito.mock(RunResendDetailCleanupPort.class);
        contextService = org.mockito.Mockito.mock(ConversationContextApplicationService.class);
        eventAppender = org.mockito.Mockito.mock(RunEventAppender.class);
        sessionMessageRealtimeHub = org.mockito.Mockito.mock(SessionMessageRealtimeHub.class);
        AgentRuntimeRegistry registry = org.mockito.Mockito.mock(AgentRuntimeRegistry.class);
        when(registry.defaultAgentId()).thenReturn("opencode");
        when(registry.require("opencode")).thenReturn(runtime);
        when(resendRepository.findById(RESEND_ID)).thenReturn(Optional.of(waiting()));
        when(resendRepository.saveIfStatus(any(), any())).thenReturn(true);
        when(runRepository.findById(SOURCE_RUN_ID)).thenReturn(Optional.of(sourceRun()));
        when(runRepository.findById(REPLACEMENT_RUN_ID)).thenReturn(Optional.of(replacementRun()));
        service = new RunResendExecutionService(
                resendRepository,
                inputStore,
                runRepository,
                registry,
                runApplicationService,
                cleanupPort,
                contextService,
                eventAppender,
                Clock.fixed(NOW, ZoneOffset.UTC));
        service.configureSessionMessageRealtimeHub(sessionMessageRealtimeHub);
    }

    @Test
    void failsBeforeNativeRevertWhenReplayInputExpired() {
        when(inputStore.find(REPLACEMENT_RUN_ID)).thenReturn(Optional.empty());

        RunResend result = service.execute(RESEND_ID);

        assertThat(result.status()).isEqualTo(RunResendStatus.FAILED);
        verify(runtime, never()).revertTurn(any());
        verify(cleanupPort).purgePendingReplacementMessage(REPLACEMENT_RUN_ID, SESSION_ID, NOW);
        verify(resendRepository).deleteSessionLock(any(), any());
        verify(sessionMessageRealtimeHub).publishAfterCommit(argThat(change ->
                change.sourceRunId().equals(SOURCE_RUN_ID)
                        && change.replacementRunId().equals(REPLACEMENT_RUN_ID)
                        && change.revision().equals(NOW)));
    }

    @Test
    void responseLossRecoveryProbesStableMessageAndDoesNotDispatchTwice() {
        when(inputStore.find(REPLACEMENT_RUN_ID)).thenReturn(Optional.of(input()));
        ConversationContextApplicationService.IssuedConversationContext issued = issuedContext();
        when(contextService.bootstrap(any(), any(), any(), any())).thenReturn(issued);
        when(runtime.revertTurn(any())).thenReturn(reactor.core.publisher.Mono.just(new AgentRevertTurnResult(true)));
        when(runtime.probeMessage(any())).thenReturn(
                reactor.core.publisher.Mono.just(new AgentMessageProbeResult(true)));

        RunResend result = service.execute(RESEND_ID);

        assertThat(result.status()).isEqualTo(RunResendStatus.DISPATCHED);
        verify(runApplicationService, never()).startResendRun(any(), any(), any(), any(), any(), any());
        verify(cleanupPort).purgeSourceRun(SOURCE_RUN_ID, SESSION_ID, NOW);
        verify(sessionMessageRealtimeHub).publishAfterCommit(argThat(change ->
                change.sessionId().equals(SESSION_ID)
                        && change.sourceRunId().equals(SOURCE_RUN_ID)
                        && change.replacementRunId().equals(REPLACEMENT_RUN_ID)
                        && change.revision().equals(NOW)
                        && change.traceId().equals("trace_execution")
                        && change.occurredAt().equals(NOW)));
    }

    @Test
    void losingDispatchedCompareAndSetDoesNotPublishDuplicateStartedEvent() {
        when(inputStore.find(REPLACEMENT_RUN_ID)).thenReturn(Optional.of(input()));
        ConversationContextApplicationService.IssuedConversationContext issued = issuedContext();
        when(contextService.bootstrap(any(), any(), any(), any())).thenReturn(issued);
        when(runtime.revertTurn(any())).thenReturn(reactor.core.publisher.Mono.just(new AgentRevertTurnResult(true)));
        when(runtime.probeMessage(any())).thenReturn(reactor.core.publisher.Mono.just(new AgentMessageProbeResult(true)));
        when(resendRepository.saveIfStatus(any(), any())).thenReturn(true, true, false);

        RunResend result = service.execute(RESEND_ID);

        assertThat(result.status()).isEqualTo(RunResendStatus.WAITING);
        verify(cleanupPort).purgeSourceRun(SOURCE_RUN_ID, SESSION_ID, NOW);
        verify(resendRepository, never()).deleteSessionLock(any(), any());
        verify(eventAppender, never()).append(any(), any());
        verify(sessionMessageRealtimeHub, never()).publishAfterCommit(any());
    }

    @Test
    void unknownStableMessageProbeKeepsLockAndDoesNotDispatchOrUnrevert() {
        when(inputStore.find(REPLACEMENT_RUN_ID)).thenReturn(Optional.of(input()));
        ConversationContextApplicationService.IssuedConversationContext issued = issuedContext();
        when(contextService.bootstrap(any(), any(), any(), any())).thenReturn(issued);
        when(runtime.revertTurn(any())).thenReturn(reactor.core.publisher.Mono.just(new AgentRevertTurnResult(true)));
        when(runtime.probeMessage(any())).thenReturn(reactor.core.publisher.Mono.empty());

        assertThatThrownBy(() -> service.execute(RESEND_ID))
                .isInstanceOf(com.enterprise.testagent.common.error.PlatformException.class)
                .hasMessageContaining("状态未知");

        verify(runApplicationService, never()).startResendRun(any(), any(), any(), any(), any(), any());
        verify(runtime, never()).unrevertTurn(any());
        verify(resendRepository, never()).deleteSessionLock(any(), any());
    }

    private RunResend waiting() {
        return new RunResend(
                RESEND_ID,
                SESSION_ID,
                new UserId("usr_execution_service"),
                SOURCE_RUN_ID,
                REPLACEMENT_RUN_ID,
                "msg_execution_source",
                "msg_execution_replacement",
                RunResendTrigger.AUTOMATIC,
                1,
                1,
                RunResendPolicy.MAX_AUTOMATIC_ATTEMPTS,
                RunResendStatus.WAITING,
                NOW,
                "linux-execution-1",
                null,
                null,
                "request_execution_1",
                "trace_execution",
                null,
                NOW,
                NOW);
    }

    private Run sourceRun() {
        return new Run(
                SOURCE_RUN_ID,
                new SessionId("ses_execution_service"),
                new WorkspaceId("wrk_execution_service"),
                RunStatus.FAILED,
                NOW.minusSeconds(60),
                NOW,
                "trace_source")
                .withSource(ConversationSourceType.SCHEDULED_TASK, "net_execution", new UserId("usr_execution_service"));
    }

    private Run replacementRun() {
        return new Run(
                REPLACEMENT_RUN_ID,
                new SessionId("ses_execution_service"),
                new WorkspaceId("wrk_execution_service"),
                RunStatus.PENDING,
                NOW,
                NOW,
                "trace_execution")
                .withSource(ConversationSourceType.SCHEDULED_TASK, "net_execution", new UserId("usr_execution_service"));
    }

    private RunResendReplayInput input() {
        return new RunResendReplayInput(
                REPLACEMENT_RUN_ID,
                "重新执行",
                List.of(Map.of("type", "text", "text", "重新执行")),
                "msg_execution_replacement",
                "build",
                "openai",
                "gpt-5",
                "high",
                NOW,
                NOW.plusSeconds(3600));
    }

    private ConversationContextApplicationService.IssuedConversationContext issuedContext() {
        ConversationRunContext context = org.mockito.Mockito.mock(ConversationRunContext.class);
        ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_execution_service"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                4,
                NOW);
        when(context.executionNodeSnapshot()).thenReturn(node);
        when(context.remoteSessionId()).thenReturn("ses_remote_execution");
        when(context.trustedWorkspaceRoot()).thenReturn("/tmp/execution");
        when(context.linuxServerId()).thenReturn("linux-execution-1");
        return new ConversationContextApplicationService.IssuedConversationContext("context_execution", context);
    }
}
