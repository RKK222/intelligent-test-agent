package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventId;
import com.icbc.testagent.domain.event.RunEventRepository;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.session.SessionTitleUpdateRepository;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.event.RunEventAppender;
import com.icbc.testagent.opencode.runtime.runtime.OpencodeRuntimeApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RunSessionTitleFallbackServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    @Test
    void fallbackUpdatesFirstRunTitleWhenNativeEventDidNotArrive() {
        SessionRepository sessions = mock(SessionRepository.class);
        SessionMessageRepository messages = mock(SessionMessageRepository.class);
        SessionTitleUpdateRepository titleUpdates = mock(SessionTitleUpdateRepository.class);
        RunEventRepository events = mock(RunEventRepository.class);
        OpencodeRuntimeApplicationService runtime = mock(OpencodeRuntimeApplicationService.class);
        Run run = firstRun();
        Session session = session();
        when(sessions.findById(run.sessionId())).thenReturn(Optional.of(session));
        when(messages.findBySessionId(eq(run.sessionId()), any(PageRequest.class))).thenReturn(new PageResponse<>(
                List.of(firstUserMessage(run)), 1, 200, 1));
        when(events.findByRunIdAfter(run.runId(), 0, 200)).thenReturn(List.of());
        when(titleUpdates.updateTitleIfCurrent(any(), any(), any(), any(), any())).thenReturn(true);
        when(runtime.withAgent(eq("opencode"), eq(run.triggeredByUserId()), any()))
                .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(2)).get());
        when(runtime.generateNativeSessionTitle(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of("金额新增参数审核测试"));
        OpencodeSessionTitleProperties properties = new OpencodeSessionTitleProperties();
        RunSessionTitleFallbackService service = new RunSessionTitleFallbackService(
                sessions,
                messages,
                titleUpdates,
                events,
                new RunEventAppender(events),
                new RunEventPersistencePolicy(),
                runtime,
                properties);

        service.fallbackIfNeeded("opencode", run);

        verify(titleUpdates).updateTitleIfCurrent(
                eq(run.sessionId()),
                eq("测试金额新增参数审核"),
                eq("金额新增参数审核测试"),
                any(),
                eq("trace_1234567890abcdef"));
        verify(runtime).generateNativeSessionTitle(
                "wrk_1234567890abcdef",
                "测试金额新增参数审核",
                properties.getFallbackTimeout(),
                properties.getFallbackPollInterval(),
                "trace_1234567890abcdef");
        verify(events).append(any(RunEventDraft.class));
    }

    @Test
    void fallbackDoesNotRunWhenNativeTitleWasAlreadySynchronized() {
        SessionRepository sessions = mock(SessionRepository.class);
        SessionMessageRepository messages = mock(SessionMessageRepository.class);
        SessionTitleUpdateRepository titleUpdates = mock(SessionTitleUpdateRepository.class);
        RunEventRepository events = mock(RunEventRepository.class);
        OpencodeRuntimeApplicationService runtime = mock(OpencodeRuntimeApplicationService.class);
        Run run = firstRun();
        when(events.findByRunIdAfter(run.runId(), 0, 200)).thenReturn(List.of(new RunEvent(
                new RunEventId("evt_1234567890abcdef"),
                run.runId(),
                1,
                RunEventType.SESSION_UPDATED,
                "trace_1234567890abcdef",
                NOW,
                java.util.Map.of("platformSessionTitleSynchronized", true))));
        RunSessionTitleFallbackService service = new RunSessionTitleFallbackService(
                sessions,
                messages,
                titleUpdates,
                events,
                new RunEventAppender(events),
                new RunEventPersistencePolicy(),
                runtime,
                new OpencodeSessionTitleProperties());

        service.fallbackIfNeeded("opencode", run);

        verify(runtime, org.mockito.Mockito.never()).generateNativeSessionTitle(any(), any(), any(), any(), any());
    }

    @Test
    void fallbackDoesNotPublishWhenTitleChangedBeforeCompareAndSet() {
        SessionRepository sessions = mock(SessionRepository.class);
        SessionMessageRepository messages = mock(SessionMessageRepository.class);
        SessionTitleUpdateRepository titleUpdates = mock(SessionTitleUpdateRepository.class);
        RunEventRepository events = mock(RunEventRepository.class);
        OpencodeRuntimeApplicationService runtime = mock(OpencodeRuntimeApplicationService.class);
        Run run = firstRun();
        when(sessions.findById(run.sessionId())).thenReturn(Optional.of(session()));
        when(messages.findBySessionId(eq(run.sessionId()), any(PageRequest.class))).thenReturn(new PageResponse<>(
                List.of(firstUserMessage(run)), 1, 200, 1));
        when(events.findByRunIdAfter(run.runId(), 0, 200)).thenReturn(List.of());
        when(runtime.withAgent(eq("opencode"), eq(run.triggeredByUserId()), any()))
                .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(2)).get());
        when(runtime.generateNativeSessionTitle(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of("金额新增参数审核测试"));
        when(titleUpdates.updateTitleIfCurrent(any(), any(), any(), any(), any())).thenReturn(false);
        RunSessionTitleFallbackService service = new RunSessionTitleFallbackService(
                sessions,
                messages,
                titleUpdates,
                events,
                new RunEventAppender(events),
                new RunEventPersistencePolicy(),
                runtime,
                new OpencodeSessionTitleProperties());

        service.fallbackIfNeeded("opencode", run);

        verify(events, org.mockito.Mockito.never()).append(any(RunEventDraft.class));
    }

    private static Run firstRun() {
        return new Run(
                        new RunId("run_1234567890abcdef"),
                        new SessionId("ses_1234567890abcdef"),
                        new WorkspaceId("wrk_1234567890abcdef"),
                        RunStatus.SUCCEEDED,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef")
                .withSource(null, null, new UserId("usr_1234567890abcdef"))
                .withRuntimeSelection("opencode", null);
    }

    private static Session session() {
        return new Session(
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                "测试金额新增参数审核",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef",
                "ses_remote1234567890abcdef",
                new com.icbc.testagent.domain.node.ExecutionNodeId("node_1234567890abcdef"));
    }

    private static SessionMessage firstUserMessage(Run run) {
        return new SessionMessage(
                new SessionMessageId("msg_1234567890abcdef"),
                run.sessionId(),
                SessionMessageRole.USER,
                "测试金额新增参数审核",
                NOW,
                "trace_1234567890abcdef",
                run.runId(),
                "opencode",
                null,
                null,
                null,
                null,
                NOW);
    }
}
