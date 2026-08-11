package com.enterprise.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.agent.runtime.AgentPromptPart;
import com.enterprise.testagent.agent.runtime.AgentReplayableTurn;
import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.domain.agent.AgentSessionBinding;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.run.ConversationRunContext;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.run.RunResend;
import com.enterprise.testagent.domain.run.RunResendReplayInput;
import com.enterprise.testagent.domain.run.RunResendReplayInputStore;
import com.enterprise.testagent.domain.run.RunResendRepository;
import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.domain.run.RunResendPolicy;
import com.enterprise.testagent.domain.run.RunResendSourceTurn;
import com.enterprise.testagent.domain.run.RunResendSourceTurnQuery;
import com.enterprise.testagent.domain.run.RunResendStatus;
import com.enterprise.testagent.domain.run.RunResendTrigger;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.event.RunEventAppender;
import com.enterprise.testagent.opencode.runtime.share.DelegatedOperationContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class RunResendApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-07T10:00:00Z");
    private static final UserId OWNER = new UserId("usr_resend_owner");
    private static final UserId SHARED_SENDER = new UserId("usr_resend_shared_sender");
    private static final UserId OTHER_MEMBER = new UserId("usr_resend_other_member");
    private static final SessionId SESSION_ID = new SessionId("ses_resend_service");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_resend_service");
    private static final SessionShareId SHARE_ID = new SessionShareId("shr_" + "c".repeat(64));
    private static final RunId SOURCE_RUN_ID = new RunId("run_resend_service_source");
    private static final String SOURCE_MESSAGE_ID = "msg_resend_service_source";

    private RunRepository runRepository;
    private RunResendRepository resendRepository;
    private RunResendReplayInputStore replayInputStore;
    private RunResendSourceTurnQuery sourceTurnQuery;
    private ConversationRunContextResolver contextResolver;
    private AgentRuntime runtime;
    private RunResendApplicationService service;

    @BeforeEach
    void setUp() {
        runRepository = mock(RunRepository.class);
        resendRepository = mock(RunResendRepository.class);
        replayInputStore = mock(RunResendReplayInputStore.class);
        sourceTurnQuery = mock(RunResendSourceTurnQuery.class);
        contextResolver = mock(ConversationRunContextResolver.class);
        runtime = mock(AgentRuntime.class);
        AgentRuntimeRegistry registry = mock(AgentRuntimeRegistry.class);
        when(registry.normalize("opencode")).thenReturn("opencode");
        when(registry.require("opencode")).thenReturn(runtime);
        when(resendRepository.findByOwnerAndClientRequestId(any(), any())).thenReturn(Optional.empty());
        when(resendRepository.findActiveBySession(SESSION_ID)).thenReturn(Optional.empty());
        when(resendRepository.insertSessionLock(any(), any(), any(), any())).thenReturn(true);
        when(runRepository.findById(SOURCE_RUN_ID)).thenReturn(Optional.of(sourceRun()));
        when(sourceTurnQuery.findLatestUserTurn(SESSION_ID)).thenReturn(Optional.of(
                new RunResendSourceTurn(SOURCE_RUN_ID, SOURCE_MESSAGE_ID)));
        when(contextResolver.resolve(any(), any(), any(), any())).thenReturn(Optional.of(context()));
        when(runtime.createDispatchMessageId()).thenReturn("msg_resend_service_replacement");
        when(runtime.loadReplayableTurn(any())).thenReturn(Mono.just(new AgentReplayableTurn(
                SOURCE_MESSAGE_ID,
                "重新检查",
                List.of(AgentPromptPart.text("重新检查")),
                "build",
                "openai",
                "gpt-5",
                "high")));
        service = new RunResendApplicationService(
                runRepository,
                resendRepository,
                replayInputStore,
                sourceTurnQuery,
                contextResolver,
                registry,
                mock(RunEventAppender.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void manualResendReservesPendingReplacementAndStoresExactInputBeforeRevert() {
        RunResend result = service.createManual(
                OWNER,
                "opencode",
                SESSION_ID,
                new CreateRunResendCommand(
                        SOURCE_MESSAGE_ID,
                        SOURCE_RUN_ID,
                        "context_token_1",
                        "request_resend_1"),
                "trace_resend_1");

        assertThat(result.sourceRunId()).isEqualTo(SOURCE_RUN_ID);
        assertThat(result.replacementRemoteMessageId()).isEqualTo("msg_resend_service_replacement");
        assertThat(result.totalAttempt()).isEqualTo(1);
        assertThat(result.automaticAttempt()).isZero();
        verify(replayInputStore).save(any());
        verify(runRepository).save(org.mockito.ArgumentMatchers.argThat(run ->
                run.runId().equals(result.replacementRunId())
                        && run.status() == RunStatus.PENDING
                        && run.sourceType() == ConversationSourceType.SCHEDULED_TASK));
    }

    @Test
    void rejectsStaleRemoteBoundaryBeforeReadingOrRevertingRemoteState() {
        assertThatThrownBy(() -> service.createManual(
                        OWNER,
                        "opencode",
                        SESSION_ID,
                        new CreateRunResendCommand(
                                "msg_stale_boundary",
                                SOURCE_RUN_ID,
                                "context_token_1",
                                "request_resend_2"),
                        "trace_resend_2"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("最后一条");
    }

    @Test
    void manualResendContinuesChainWithoutResettingAutomaticQuota() {
        RunResend previous = new RunResend(
                new RunResendId("rsd_previous_attempt"), SESSION_ID, OWNER,
                new RunId("run_previous_source"), SOURCE_RUN_ID,
                "msg_previous_source", SOURCE_MESSAGE_ID,
                RunResendTrigger.AUTOMATIC, 2, 2, RunResendPolicy.MAX_AUTOMATIC_ATTEMPTS,
                RunResendStatus.DISPATCHED, NOW.minusSeconds(60), "linux-resend-1",
                null, null, "request_previous", "trace_previous", null,
                NOW.minusSeconds(120), NOW.minusSeconds(60));
        when(resendRepository.findByReplacementRunId(SOURCE_RUN_ID)).thenReturn(Optional.of(previous));

        RunResend result = service.createManual(
                OWNER, "opencode", SESSION_ID,
                new CreateRunResendCommand(
                        SOURCE_MESSAGE_ID, SOURCE_RUN_ID, "context_token_1", "request_resend_chain"),
                "trace_resend_chain");

        assertThat(result.totalAttempt()).isEqualTo(3);
        assertThat(result.automaticAttempt()).isEqualTo(2);
    }

    @Test
    void sharedSenderCanEditLastMessageAndPreservesOtherReplayParts() {
        when(runRepository.findById(SOURCE_RUN_ID)).thenReturn(Optional.of(
                sourceRun().withMessageSender(SHARED_SENDER, "ucid_resend_shared", true)));
        when(runtime.loadReplayableTurn(any())).thenReturn(Mono.just(new AgentReplayableTurn(
                SOURCE_MESSAGE_ID,
                "原始问题",
                List.of(
                        AgentPromptPart.text("原始问题", java.util.Map.of("kind", "original")),
                        AgentPromptPart.file(
                                "file:///tmp/report.txt", "text/plain", "report.txt",
                                java.util.Map.of("kind", "attachment")),
                        AgentPromptPart.agent("review", java.util.Map.of("kind", "mention"))),
                "build",
                "openai",
                "gpt-5",
                "high")));

        RunResend result = service.createManual(
                sharedContext(SHARED_SENDER, true),
                "opencode",
                SESSION_ID,
                new CreateRunResendCommand(
                        SOURCE_MESSAGE_ID,
                        SOURCE_RUN_ID,
                        "context_token_shared",
                        "request_resend_shared",
                        "修改后的问题"),
                "trace_resend_shared");

        assertThat(result.requesterUserId()).isEqualTo(SHARED_SENDER);
        assertThat(result.requestedBySharedUser()).isTrue();
        ArgumentCaptor<RunResendReplayInput> inputCaptor = ArgumentCaptor.forClass(RunResendReplayInput.class);
        verify(replayInputStore).save(inputCaptor.capture());
        RunResendReplayInput replayInput = inputCaptor.getValue();
        assertThat(replayInput.prompt()).isEqualTo("修改后的问题");
        assertThat(replayInput.parts()).satisfiesExactly(
                part -> assertThat(part)
                        .containsEntry("type", "text")
                        .containsEntry("text", "修改后的问题")
                        .containsEntry("source", java.util.Map.of("kind", "original")),
                part -> assertThat(part)
                        .containsEntry("type", "file")
                        .containsEntry("filename", "report.txt")
                        .containsEntry("source", java.util.Map.of("kind", "attachment")),
                part -> assertThat(part)
                        .containsEntry("type", "agent")
                        .containsEntry("agentName", "review")
                        .containsEntry("source", java.util.Map.of("kind", "mention")));
        verify(runRepository).save(org.mockito.ArgumentMatchers.argThat(run ->
                run.runId().equals(result.replacementRunId())
                        && SHARED_SENDER.equals(run.messageSenderUserId())
                        && "ucid_resend_shared".equals(run.messageSenderUnifiedAuthId())
                        && run.messageSentBySharedUser()));
    }

    @Test
    void ownerCannotEditSharedMembersLastMessage() {
        CreateRunResendCommand command = new CreateRunResendCommand(
                SOURCE_MESSAGE_ID,
                SOURCE_RUN_ID,
                "context_token_shared",
                "request_resend_owner_denied");
        when(runRepository.findById(SOURCE_RUN_ID)).thenReturn(Optional.of(
                sourceRun().withMessageSender(SHARED_SENDER, "ucid_resend_shared", true)));

        assertThatThrownBy(() -> service.createManual(
                        OWNER, "opencode", SESSION_ID, command, "trace_resend_owner_denied"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> {
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                            assertThat(exception.getMessage()).contains("实际发送人");
                        });
    }

    @Test
    void sharedSenderCanEditAndResendTheirLastMessage() {
        CreateRunResendCommand command = new CreateRunResendCommand(
                SOURCE_MESSAGE_ID,
                SOURCE_RUN_ID,
                "context_token_shared",
                "request_resend_shared_sender",
                "发送人修改后的问题");
        when(runRepository.findById(SOURCE_RUN_ID)).thenReturn(Optional.of(
                sourceRun().withMessageSender(SHARED_SENDER, "ucid_resend_shared", true)));

        RunResend result = service.createManual(
                sharedContext(SHARED_SENDER, true),
                "opencode", SESSION_ID, command, "trace_resend_shared_sender");

        assertThat(result.requesterUserId()).isEqualTo(SHARED_SENDER);
        assertThat(result.requestedBySharedUser()).isTrue();
        assertThat(RunResendApplicationService.eventPayload(result))
                .containsEntry("requesterUserId", SHARED_SENDER.value())
                .containsEntry("requesterUnifiedAuthId", "ucid_resend_shared")
                .containsEntry("requestedBySharedUser", true);
        ArgumentCaptor<RunResendReplayInput> inputCaptor = ArgumentCaptor.forClass(RunResendReplayInput.class);
        verify(replayInputStore).save(inputCaptor.capture());
        assertThat(inputCaptor.getValue().prompt()).isEqualTo("发送人修改后的问题");
        verify(runRepository).save(org.mockito.ArgumentMatchers.argThat(run ->
                run.runId().equals(result.replacementRunId())
                        && SHARED_SENDER.equals(run.messageSenderUserId())
                        && run.messageSentBySharedUser()));
    }

    @Test
    void sharedResendRejectsMemberWhoDidNotSendTheLastMessage() {
        CreateRunResendCommand command = new CreateRunResendCommand(
                SOURCE_MESSAGE_ID,
                SOURCE_RUN_ID,
                "context_token_shared",
                "request_resend_other_member_denied");
        when(runRepository.findById(SOURCE_RUN_ID)).thenReturn(Optional.of(
                sourceRun().withMessageSender(SHARED_SENDER, "ucid_resend_shared", true)));

        assertThatThrownBy(() -> service.createManual(
                        sharedContext(OTHER_MEMBER, true),
                        "opencode", SESSION_ID, command, "trace_resend_other_member"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> {
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                            assertThat(exception.getMessage()).contains("实际发送人");
                        });
    }

    private Run sourceRun() {
        return new Run(
                SOURCE_RUN_ID,
                SESSION_ID,
                WORKSPACE_ID,
                RunStatus.FAILED,
                NOW.minusSeconds(60),
                NOW.minusSeconds(1),
                "trace_source")
                .withSource(ConversationSourceType.SCHEDULED_TASK, "net_resend_source", OWNER)
                .withRuntimeSelection("build", "openai/gpt-5");
    }

    private ConversationRunContext context() {
        Workspace workspace = new Workspace(
                WORKSPACE_ID, "resend", "/tmp/resend", WorkspaceStatus.ACTIVE,
                NOW.minusSeconds(120), NOW, "linux-resend-1", "trace_workspace");
        Session session = new Session(
                SESSION_ID, WORKSPACE_ID, "resend", SessionStatus.ACTIVE, NOW.minusSeconds(120), NOW,
                "trace_session", "ses_remote_resend", new ExecutionNodeId("node_process-resend"), false)
                .withSource(ConversationSourceType.SCHEDULED_TASK, "net_resend_source", OWNER);
        ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_process-resend"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                4,
                NOW);
        AgentSessionBinding binding = new AgentSessionBinding(
                SESSION_ID, "opencode", "ses_remote_resend", node.executionNodeId(), NOW, NOW, "trace_binding");
        return new ConversationRunContext(
                OWNER, "opencode", "process-resend", "linux-resend-1",
                session, workspace, node, binding, 1, NOW.plusSeconds(300));
    }

    private DelegatedOperationContext sharedContext(UserId actor, boolean canChat) {
        boolean ownerAccess = actor.equals(OWNER);
        boolean sharedSender = actor.equals(SHARED_SENDER);
        return new DelegatedOperationContext(
                SHARE_ID,
                5L,
                actor,
                sharedSender ? "ucid_resend_shared" : ownerAccess ? "ucid_resend_owner" : "ucid_resend_other",
                sharedSender ? "消息发送人" : ownerAccess ? "会话所属人" : "其他成员",
                OWNER,
                SESSION_ID,
                WORKSPACE_ID,
                canChat,
                !ownerAccess,
                ownerAccess,
                NOW.plusSeconds(3_600));
    }
}
