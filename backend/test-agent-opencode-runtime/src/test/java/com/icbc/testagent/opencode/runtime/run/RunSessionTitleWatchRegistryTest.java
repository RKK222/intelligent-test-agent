package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeCommand;
import com.icbc.testagent.agent.runtime.AgentRuntimeResult;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventScopeContext;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.session.SessionTitleUpdateRepository;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.event.RunEventAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.Test;

class RunSessionTitleWatchRegistryTest {

    private static final Instant NOW = Instant.parse("2026-07-10T05:00:00Z");
    private static final SessionId SESSION_ID = new SessionId("ses_1234567890abcdef");
    private static final RunId RUN_ID = new RunId("run_1234567890abcdef");
    private static final String REMOTE_SESSION_ID = "ses_remote1234567890abcdef";
    private static final String TRACE_ID = "trace_1234567890abcdef";

    @Test
    void registersFirstRunWithImmutableRuntimeRouteAndOnlyTitleWaitCanBeClosedByNextRun() {
        RunSessionTitleWatchRegistry registry = new RunSessionTitleWatchRegistry();

        RunSessionTitleWatchRegistry.TitleWatchToken token = registry.register(
                SESSION_ID,
                RUN_ID,
                runtime(),
                node(),
                "/tmp/workspace",
                "workspace",
                REMOTE_SESSION_ID,
                "首条消息临时标题");

        assertThat(token.state()).isEqualTo(RunSessionTitleWatchRegistry.State.ACTIVE);
        assertThat(token.runtime().agentId()).isEqualTo("opencode");
        assertThat(token.node()).isEqualTo(node());
        assertThat(token.directory()).isEqualTo("/tmp/workspace");
        assertThat(token.workspace()).isEqualTo("workspace");
        assertThat(token.remoteSessionId()).isEqualTo(REMOTE_SESSION_ID);
        assertThat(registry.closeTitleWaitForNextRun(SESSION_ID)).isFalse();
        assertThat(token.state()).isEqualTo(RunSessionTitleWatchRegistry.State.ACTIVE);

        assertThat(registry.enterTitleWait(token)).isTrue();
        assertThat(registry.closeTitleWaitForNextRun(SESSION_ID)).isTrue();
        assertThat(token.state()).isEqualTo(RunSessionTitleWatchRegistry.State.CLOSED);
        assertThat(token.cancellationSignal().block(Duration.ofSeconds(1))).isNull();
    }

    @Test
    void ignoresExpiredTokenAndUsesCasBeforePublishingNativeTitleConfirmation() {
        RunSessionTitleWatchRegistry registry = new RunSessionTitleWatchRegistry();
        SessionTitleUpdateRepository titleUpdates = mock(SessionTitleUpdateRepository.class);
        RunEventAppender appender = mock(RunEventAppender.class);
        RunSessionTitleWatchService service = new RunSessionTitleWatchService(registry, titleUpdates, appender, new RunEventPersistencePolicy());
        RunSessionTitleWatchRegistry.TitleWatchToken token = service.registerFirstRun(
                SESSION_ID,
                RUN_ID,
                runtime(),
                node(),
                "/tmp/workspace",
                "workspace",
                REMOTE_SESSION_ID,
                "首条消息临时标题");
        service.enterTitleWait(token);
        registry.close(token);

        RunEventDraft ignored = service.synchronizeNativeTitle(rootSessionUpdated("原生标题"));

        assertThat(ignored.payload()).doesNotContainKeys("platformSessionTitleSynchronized", "platformSessionTitle");
        org.mockito.Mockito.verifyNoInteractions(titleUpdates, appender);
    }

    @Test
    void nativeTitleUsesCasAndClosesWaitingStreamInTheSameConfirmationEvent() {
        RunSessionTitleWatchRegistry registry = new RunSessionTitleWatchRegistry();
        SessionTitleUpdateRepository titleUpdates = mock(SessionTitleUpdateRepository.class);
        RunSessionTitleWatchService service = new RunSessionTitleWatchService(
                registry,
                titleUpdates,
                mock(RunEventAppender.class),
                new RunEventPersistencePolicy());
        RunSessionTitleWatchRegistry.TitleWatchToken token = service.registerFirstRun(
                SESSION_ID,
                RUN_ID,
                runtime(),
                node(),
                "/tmp/workspace",
                "workspace",
                REMOTE_SESSION_ID,
                "首条消息临时标题");
        service.enterTitleWait(token);
        when(titleUpdates.updateTitleIfCurrent(eq(SESSION_ID), eq("首条消息临时标题"), eq("原生标题"), any(), eq(TRACE_ID)))
                .thenReturn(true);

        RunEventDraft synchronizedDraft = service.synchronizeNativeTitle(rootSessionUpdated("原生标题"));

        verify(titleUpdates).updateTitleIfCurrent(eq(SESSION_ID), eq("首条消息临时标题"), eq("原生标题"), any(), eq(TRACE_ID));
        assertThat(synchronizedDraft.payload())
                .containsEntry("platformSessionTitleSynchronized", true)
                .containsEntry("platformSessionTitle", "原生标题")
                .containsEntry("platformSessionTitlePending", false)
                .containsEntry("platformSessionTitleWatchClosed", true);
        assertThat(token.state()).isEqualTo(RunSessionTitleWatchRegistry.State.CLOSED);
        assertThat(token.cancellationSignal().block(Duration.ofSeconds(1))).isNull();
    }

    @Test
    void completedTitleAgentMessageReadsTheFrozenRemoteSessionBeforeRouting() {
        RunSessionTitleWatchRegistry registry = new RunSessionTitleWatchRegistry();
        SessionTitleUpdateRepository titleUpdates = mock(SessionTitleUpdateRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        RunSessionTitleWatchService service = new RunSessionTitleWatchService(
                registry,
                titleUpdates,
                mock(RunEventAppender.class),
                new RunEventPersistencePolicy(),
                sessions);
        RunSessionTitleWatchRegistry.TitleWatchToken token = service.registerFirstRun(
                SESSION_ID,
                RUN_ID,
                runtime,
                node(),
                "/tmp/workspace",
                "workspace",
                REMOTE_SESSION_ID,
                "首条消息临时标题");
        service.enterTitleWait(token);
        when(runtime.runtime(any(AgentRuntimeCommand.class))).thenReturn(Mono.just(new AgentRuntimeResult(
                new ObjectMapper().createObjectNode().set("info", new ObjectMapper().createObjectNode().put("title", "原生标题")))));
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(platformSession(REMOTE_SESSION_ID)));
        when(titleUpdates.updateTitleIfCurrent(eq(SESSION_ID), eq("首条消息临时标题"), eq("原生标题"), any(), eq(TRACE_ID)))
                .thenReturn(true);

        RunEventDraft finalTitle = service.completeTitleAgentMessage(token, completedTitleMessage()).orElseThrow();
        RunEventDraft synchronizedTitle = service.synchronizeNativeTitle(finalTitle);

        org.mockito.ArgumentCaptor<AgentRuntimeCommand> command = org.mockito.ArgumentCaptor.forClass(AgentRuntimeCommand.class);
        verify(runtime).runtime(command.capture());
        assertThat(command.getValue().node()).isEqualTo(node());
        assertThat(command.getValue().directory()).isEqualTo("/tmp/workspace");
        assertThat(command.getValue().workspace()).isEqualTo("workspace");
        assertThat(command.getValue().path()).isEqualTo("/session/" + REMOTE_SESSION_ID);
        assertThat(finalTitle.type()).isEqualTo(RunEventType.SESSION_UPDATED);
        assertThat(synchronizedTitle.payload()).containsEntry("platformSessionTitle", "原生标题");
    }

    @Test
    void nativeTitleDoesNotUpdateWhenPlatformSessionNowPointsToAnotherRemoteSession() {
        RunSessionTitleWatchRegistry registry = new RunSessionTitleWatchRegistry();
        SessionTitleUpdateRepository titleUpdates = mock(SessionTitleUpdateRepository.class);
        SessionRepository sessions = mock(SessionRepository.class);
        RunSessionTitleWatchService service = new RunSessionTitleWatchService(
                registry,
                titleUpdates,
                mock(RunEventAppender.class),
                new RunEventPersistencePolicy(),
                sessions);
        RunSessionTitleWatchRegistry.TitleWatchToken token = service.registerFirstRun(
                SESSION_ID,
                RUN_ID,
                runtime(),
                node(),
                "/tmp/workspace",
                "workspace",
                REMOTE_SESSION_ID,
                "首条消息临时标题");
        service.enterTitleWait(token);
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(platformSession("ses_newremote1234567890abcdef")));

        RunEventDraft ignored = service.synchronizeNativeTitle(rootSessionUpdated("原生标题"));

        assertThat(ignored.payload()).doesNotContainKey("platformSessionTitleSynchronized");
        org.mockito.Mockito.verifyNoInteractions(titleUpdates);
        assertThat(token.state()).isEqualTo(RunSessionTitleWatchRegistry.State.CLOSED);
    }

    @Test
    void cancellingTitleWaitPublishesClosedMarkerForOriginalRun() {
        RunSessionTitleWatchRegistry registry = new RunSessionTitleWatchRegistry();
        RunEventAppender appender = mock(RunEventAppender.class);
        RunSessionTitleWatchService service = new RunSessionTitleWatchService(
                registry,
                mock(SessionTitleUpdateRepository.class),
                appender,
                new RunEventPersistencePolicy());
        RunSessionTitleWatchRegistry.TitleWatchToken token = service.registerFirstRun(
                SESSION_ID,
                RUN_ID,
                runtime(),
                node(),
                "/tmp/workspace",
                "workspace",
                REMOTE_SESSION_ID,
                "首条消息临时标题");
        service.enterTitleWait(token);

        assertThat(service.cancelForSession(SESSION_ID, TRACE_ID)).isTrue();

        org.mockito.ArgumentCaptor<RunEventDraft> event = org.mockito.ArgumentCaptor.forClass(RunEventDraft.class);
        verify(appender).append(event.capture());
        assertThat(event.getValue().runId()).isEqualTo(RUN_ID);
        assertThat(event.getValue().type()).isEqualTo(RunEventType.SESSION_UPDATED);
        assertThat(event.getValue().payload())
                .containsEntry("platformSessionTitlePending", false)
                .containsEntry("platformSessionTitleWatchClosed", true);
        assertThat(token.state()).isEqualTo(RunSessionTitleWatchRegistry.State.CLOSED);
    }

    private static RunEventDraft rootSessionUpdated(String title) {
        return new RunEventDraft(
                RUN_ID,
                RunEventType.SESSION_UPDATED,
                TRACE_ID,
                NOW,
                Map.of("rawType", "session.updated", "sessionID", REMOTE_SESSION_ID, "info", Map.of("title", title)),
                RunEventScopeContext.root(RUN_ID, REMOTE_SESSION_ID));
    }

    private static RunEventDraft completedTitleMessage() {
        return new RunEventDraft(
                RUN_ID,
                RunEventType.MESSAGE_UPDATED,
                TRACE_ID,
                NOW,
                Map.of(
                        "rawType", "message.updated",
                        "sessionID", REMOTE_SESSION_ID,
                        "info", Map.of(
                                "role", "assistant",
                                "agent", "title",
                                "time", Map.of("completed", NOW.toString()))),
                RunEventScopeContext.root(RUN_ID, REMOTE_SESSION_ID));
    }

    private static Session platformSession(String remoteSessionId) {
        return new Session(
                SESSION_ID,
                new WorkspaceId("wrk_1234567890abcdef"),
                "首条消息临时标题",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                TRACE_ID,
                remoteSessionId,
                node().executionNodeId());
    }

    private static AgentRuntime runtime() {
        return new AgentRuntime() {
            @Override
            public String agentId() {
                return "opencode";
            }
        };
    }

    private static ExecutionNode node() {
        return new ExecutionNode(
                new ExecutionNodeId("node_1234567890abcdef"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                NOW,
                Set.of("opencode"),
                NOW,
                NOW,
                TRACE_ID);
    }
}
