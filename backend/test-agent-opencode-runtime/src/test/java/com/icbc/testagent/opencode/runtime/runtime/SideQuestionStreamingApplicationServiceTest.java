package com.icbc.testagent.opencode.runtime.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentEventStream;
import com.icbc.testagent.agent.runtime.AgentRuntimeCommand;
import com.icbc.testagent.agent.runtime.AgentRuntimeResult;
import com.icbc.testagent.agent.runtime.AgentSessionMessage;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesResult;
import com.icbc.testagent.agent.runtime.AgentStartRunCommand;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.routing.RoutingDecision;
import com.icbc.testagent.domain.routing.RoutingDecisionRepository;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.event.RunEventAppender;
import com.icbc.testagent.event.RunEventLiveBus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.scheduler.VirtualTimeScheduler;

class SideQuestionStreamingApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-11T00:00:00Z");
    private static final UserId USER_ID = new UserId("usr_sidequestionstream01");
    private static final SessionId MAIN_SESSION_ID = new SessionId("ses_sidequestionstream01");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_sidequestionstream01");
    private static final String TRACE_ID = "trace_sidequestionstream01";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void startReturnsImmediatelyAndCreatesArchivedInternalSessionAndIndependentPendingRun() {
        Fixture fixture = new Fixture(command -> {
            // 本测试只验证同步创建边界，故意保留后台任务不执行。
        });

        SideQuestionRunStartResult result = fixture.service.start(
                USER_ID,
                "opencode",
                MAIN_SESSION_ID,
                "当前任务进展如何？",
                null,
                "provider/model",
                TRACE_ID);

        assertThat(result.runId()).isNotNull();
        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(fixture.sessions).save(sessionCaptor.capture());
        Session internal = sessionCaptor.getValue();
        assertThat(internal.sessionId()).isNotEqualTo(MAIN_SESSION_ID);
        assertThat(internal.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(internal.title()).isEqualTo("宠物旁路问答（内部）");
        assertThat(internal.status()).isEqualTo(SessionStatus.ARCHIVED);
        assertThat(internal.sourceType()).isEqualTo(ConversationSourceType.SIDE_QUESTION);
        assertThat(internal.sourceRefId()).isEqualTo(MAIN_SESSION_ID.value());
        assertThat(internal.createdByUserId()).isEqualTo(USER_ID);

        ArgumentCaptor<Run> runCaptor = ArgumentCaptor.forClass(Run.class);
        verify(fixture.runs).save(runCaptor.capture());
        Run pending = runCaptor.getValue();
        assertThat(pending.runId()).isEqualTo(result.runId());
        assertThat(pending.sessionId()).isEqualTo(internal.sessionId());
        assertThat(pending.status()).isEqualTo(RunStatus.PENDING);
        assertThat(pending.sourceType()).isEqualTo(ConversationSourceType.SIDE_QUESTION);
        assertThat(pending.sourceRefId()).isEqualTo(MAIN_SESSION_ID.value());
        assertThat(pending.triggeredByUserId()).isEqualTo(USER_ID);
        assertThat(pending.agentId()).isNull();
        ArgumentCaptor<RoutingDecision> routingCaptor = ArgumentCaptor.forClass(RoutingDecision.class);
        verify(fixture.routingDecisions).save(routingCaptor.capture());
        assertThat(routingCaptor.getValue().runId()).isEqualTo(result.runId());
        assertThat(routingCaptor.getValue().executionNodeId()).isEqualTo(fixture.node.executionNodeId());
        assertThat(fixture.durableEvents).extracting(RunEventDraft::type)
                .containsExactly(RunEventType.RUN_CREATED);
        verify(fixture.runs, never()).findLatestActiveBySessionId(eq(MAIN_SESSION_ID));
    }

    @Test
    void manualQuestionCreatesArchivedInternalRunWithoutMainSession() {
        Fixture fixture = new Fixture(command -> {
            // 本测试只验证同步创建边界，后台任务不执行。
        });
        when(fixture.targetResolver.sessionTarget(
                        eq("opencode"),
                        eq(USER_ID),
                        org.mockito.ArgumentMatchers.argThat(id -> !MAIN_SESSION_ID.value().equals(id)),
                        eq(TRACE_ID)))
                .thenReturn(new AgentRuntimeTargetResolver.SessionRuntimeTarget(
                        fixture.runtime, fixture.node, "/workspace", "remote_manual"));

        SideQuestionRunStartResult result = fixture.service.startManual(
                USER_ID,
                "opencode",
                WORKSPACE_ID,
                "怎样初始化工作区？",
                "provider/model",
                TRACE_ID);

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(fixture.sessions).save(sessionCaptor.capture());
        Session internal = sessionCaptor.getValue();
        assertThat(internal.title()).isEqualTo("手册问答（内部）");
        assertThat(internal.status()).isEqualTo(SessionStatus.ARCHIVED);
        assertThat(internal.sourceType()).isEqualTo(ConversationSourceType.SIDE_QUESTION);
        assertThat(internal.sourceRefId()).isNull();
        assertThat(internal.createdByUserId()).isEqualTo(USER_ID);

        ArgumentCaptor<Run> runCaptor = ArgumentCaptor.forClass(Run.class);
        verify(fixture.runs).save(runCaptor.capture());
        assertThat(runCaptor.getValue().runId()).isEqualTo(result.runId());
        assertThat(runCaptor.getValue().sessionId()).isEqualTo(internal.sessionId());
        assertThat(runCaptor.getValue().sourceRefId()).isNull();
        verify(fixture.runtime, never()).runtime(any());
    }

    @Test
    void workflowSubscribesBeforeContextOnlyPromptStreamsAnswerAndCleansFork() {
        List<String> order = new ArrayList<>();
        Fixture fixture = new Fixture(Runnable::run);
        fixture.order = order;
        fixture.finalMessages = finalAnswer("最终答案");
        fixture.remoteEvents = Flux.fromIterable(remoteAnswerEvents());
        fixture.rewireRuntime();

        SideQuestionRunStartResult result = fixture.service.start(
                USER_ID,
                "opencode",
                MAIN_SESSION_ID,
                "当前任务进展如何？",
                "msg_boundary",
                "provider/model",
                TRACE_ID);

        assertThat(result.runId()).isNotNull();
        assertThat(order).containsSubsequence("fork", "mapping", "subscribe", "prompt", "delete");
        assertThat(fixture.durableEvents).extracting(RunEventDraft::type).containsExactly(
                RunEventType.RUN_CREATED,
                RunEventType.RUN_STARTED,
                RunEventType.SIDE_QUESTION_STARTED,
                RunEventType.SIDE_QUESTION_PROGRESS,
                RunEventType.SIDE_QUESTION_PROGRESS,
                RunEventType.SIDE_QUESTION_PROGRESS);
        assertThat(fixture.durableEvents.get(2).payload()).containsEntry("sessionId", MAIN_SESSION_ID.value());
        assertThat(fixture.durableEvents.stream()
                        .filter(event -> event.type() == RunEventType.SIDE_QUESTION_PROGRESS)
                        .map(RunEventDraft::payload))
                .allMatch(payload -> !payload.containsKey("command") && !payload.containsKey("path"));
        verify(fixture.liveBus).publishTransient(any(RunEventDraft.class));
        ArgumentCaptor<AgentStartRunCommand> startCaptor = ArgumentCaptor.forClass(AgentStartRunCommand.class);
        verify(fixture.runtime).startRun(startCaptor.capture());
        assertThat(startCaptor.getValue().agent()).isNull();
        assertThat(startCaptor.getValue().system()).isNull();
        assertThat(startCaptor.getValue().messageId()).startsWith("msg_");
        assertThat(startCaptor.getValue().tools()).containsExactly(Map.entry("*", false));
        ArgumentCaptor<AgentRuntimeCommand> runtimeCaptor = ArgumentCaptor.forClass(AgentRuntimeCommand.class);
        verify(fixture.runtime, atLeastOnce()).runtime(runtimeCaptor.capture());
        AgentRuntimeCommand forkCommand = runtimeCaptor.getAllValues().stream()
                        .filter(command -> command.path().endsWith("/fork"))
                        .findFirst()
                        .orElseThrow();
        assertThat(((Map<?, ?>) forkCommand.body()).get("messageID")).isEqualTo("msg_boundary");
        verify(fixture.terminal).succeed(
                eq(result.runId()),
                org.mockito.ArgumentMatchers.argThat((Map<String, Object> payload) -> Boolean.TRUE.equals(payload.get("sideQuestion"))
                        && "最终答案".equals(payload.get("answer"))
                        && Boolean.FALSE.equals(payload.get("compacted"))),
                eq(TRACE_ID));
        assertThat(fixture.deleteCalls).hasValue(1);
    }

    @Test
    void longUnicodeAnswerIsSafelyTruncatedAndFailureStillDeletesForkOnce() {
        Fixture fixture = new Fixture(Runnable::run);
        fixture.context = context(1, "短上下文");
        fixture.finalMessages = finalAnswer("🐾".repeat(40_000));
        fixture.remoteEvents = Flux.fromIterable(remoteAnswerEvents());
        fixture.rewireRuntime();
        AtomicReference<Map<String, Object>> terminalPayload = new AtomicReference<>();
        when(fixture.terminal.succeed(any(), any(), any())).thenAnswer(invocation -> {
            terminalPayload.set(invocation.getArgument(1));
            return true;
        });

        fixture.service.start(USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);

        String answer = (String) terminalPayload.get().get("answer");
        assertThat(answer.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64 * 1024);
        assertThat(answer.codePoints().allMatch(codePoint -> Character.isValidCodePoint(codePoint))).isTrue();
        assertThat(terminalPayload.get()).containsEntry("truncated", true);
        verify(fixture.runtime, atLeastOnce()).runtime(any(AgentRuntimeCommand.class));

        Fixture failed = new Fixture(Runnable::run);
        failed.failMapping = true;
        failed.rewireRuntime();
        failed.service.start(USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);
        verify(failed.terminal).fail(any(), eq("旁路问答暂时失败"), eq(TRACE_ID));
        assertThat(failed.deleteCalls).hasValue(1);
    }

    @Test
    void everyRemoteExecutionFailureConvergesToOneSafeFailureAndCleansCreatedFork() {
        for (FailurePoint failurePoint : List.of(
                FailurePoint.FORK,
                FailurePoint.PROMPT,
                FailurePoint.FINAL_ANSWER,
                FailurePoint.READY_FAILURE,
                FailurePoint.PROMPT_REJECTED)) {
            Fixture fixture = new Fixture(Runnable::run);
            fixture.failurePoint = failurePoint;
            if (failurePoint == FailurePoint.FINAL_ANSWER) {
                fixture.finalMessages = new AgentSessionMessagesResult(List.of());
            }
            fixture.rewireRuntime();

            fixture.service.start(
                    USER_ID,
                    "opencode",
                    MAIN_SESSION_ID,
                    "问题",
                    null,
                    null,
                    TRACE_ID);

            verify(fixture.terminal).fail(any(), eq("旁路问答暂时失败"), eq(TRACE_ID));
            assertThat(fixture.deleteCalls.get())
                    .as(failurePoint.name())
                    .isEqualTo(failurePoint == FailurePoint.FORK ? 0 : 1);
        }
    }

    @Test
    void promptWaitsForRealEventStreamReadySignalInsteadOfSubscriptionCallback() throws Exception {
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch promptStarted = new CountDownLatch(1);
        reactor.core.publisher.Sinks.One<Void> ready = reactor.core.publisher.Sinks.one();
        reactor.core.publisher.Sinks.Many<RunEventDraft> events = reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();
        ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            Fixture fixture = new Fixture(executor);
            when(fixture.runtime.openRunEventStream(any())).thenReturn(new AgentEventStream(
                    ready.asMono(),
                    events.asFlux().doOnSubscribe(ignored -> subscribed.countDown())));
            when(fixture.runtime.startRun(any())).thenAnswer(invocation -> {
                fixture.promptMessageId.set(((AgentStartRunCommand) invocation.getArgument(0)).messageId());
                promptStarted.countDown();
                return Mono.just(new com.icbc.testagent.agent.runtime.AgentStartRunResult(true));
            });

            fixture.service.start(USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);

            assertThat(subscribed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(promptStarted.getCount()).isEqualTo(1L);
            ready.tryEmitEmpty();
            assertThat(promptStarted.await(2, TimeUnit.SECONDS)).isTrue();
            events.tryEmitNext(remote(RunEventType.MESSAGE_UPDATED, Map.of(
                    "sessionID", "remote_temp",
                    "info", Map.of("id", "msg_answer", "role", "assistant"))));
            events.tryEmitNext(remote(RunEventType.RUN_SUCCEEDED, Map.of("sessionID", "remote_temp")));
            events.tryEmitComplete();
            assertThat(fixture.deleteCompleted.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void startReturnsWhileBackgroundForkIsBlocked() throws Exception {
        CountDownLatch forkEntered = new CountDownLatch(1);
        CountDownLatch releaseFork = new CountDownLatch(1);
        ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            Fixture fixture = new Fixture(executor);
            org.mockito.Mockito.doAnswer(invocation -> {
                AgentRuntimeCommand command = invocation.getArgument(0);
                if (command.path().endsWith("/fork")) {
                    forkEntered.countDown();
                    releaseFork.await();
                    return Mono.just(new AgentRuntimeResult(JSON.valueToTree(Map.of("id", "remote_temp"))));
                }
                if ("DELETE".equals(command.method())) {
                    fixture.deleteCalls.updateAndGet(value -> value + 1);
                    fixture.deleteCompleted.countDown();
                }
                return Mono.just(new AgentRuntimeResult(JSON.valueToTree(Map.of("ok", true))));
            }).when(fixture.runtime).runtime(any());

            SideQuestionRunStartResult result = fixture.service.start(
                    USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);

            assertThat(result.runId()).isNotNull();
            assertThat(forkEntered.await(2, TimeUnit.SECONDS)).isTrue();
            releaseFork.countDown();
            assertThat(fixture.deleteCompleted.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFork.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void forkedContextIsReusedWithoutReadingOrCompactingMainMessages() {
        Fixture fixture = new Fixture(Runnable::run);
        fixture.rewireRuntime();

        fixture.service.start(
                USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, "provider/model", TRACE_ID);

        verify(fixture.runtime, never()).sessionMessages(org.mockito.ArgumentMatchers.argThat(
                command -> "remote_main".equals(command.remoteSessionId())));
        assertThat(fixture.order).doesNotContain("compact");
    }

    @Test
    void rejectedPromptFailsSafely() {
        Fixture fixture = new Fixture(Runnable::run);
        fixture.failurePoint = FailurePoint.PROMPT_REJECTED;
        fixture.rewireRuntime();

        fixture.service.start(USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);

        verify(fixture.terminal).fail(any(), eq("旁路问答暂时失败"), eq(TRACE_ID));
    }

    @Test
    void readyFailureFailsSafelyWithoutStartingPromptAndDeletesForkOnce() {
        Fixture fixture = new Fixture(Runnable::run);
        fixture.failurePoint = FailurePoint.READY_FAILURE;
        fixture.rewireRuntime();

        fixture.service.start(USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);

        verify(fixture.runtime, never()).startRun(any());
        verify(fixture.terminal).fail(any(), eq("旁路问答暂时失败"), eq(TRACE_ID));
        assertThat(fixture.deleteCalls).hasValue(1);
    }

    @Test
    void absoluteTaskTimeoutIsNotExtendedByOtherSessionEventsAndCancelsStream() throws Exception {
        VirtualTimeScheduler timeoutScheduler = VirtualTimeScheduler.create();
        ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        AtomicBoolean cancelled = new AtomicBoolean();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch promptStarted = new CountDownLatch(1);
        reactor.core.publisher.Sinks.Many<RunEventDraft> events =
                reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();
        try {
            Fixture fixture = new Fixture(executor, Duration.ofSeconds(5), timeoutScheduler);
            when(fixture.runtime.openRunEventStream(any())).thenReturn(new AgentEventStream(
                    Mono.empty(),
                    events.asFlux()
                            .doOnSubscribe(ignored -> subscribed.countDown())
                            .doOnCancel(() -> cancelled.set(true))));
            when(fixture.runtime.startRun(any())).thenAnswer(invocation -> {
                fixture.promptMessageId.set(((AgentStartRunCommand) invocation.getArgument(0)).messageId());
                promptStarted.countDown();
                return Mono.just(new com.icbc.testagent.agent.runtime.AgentStartRunResult(true));
            });

            fixture.service.start(USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);

            assertThat(subscribed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(promptStarted.await(2, TimeUnit.SECONDS)).isTrue();
            events.tryEmitNext(remote(RunEventType.RUN_STARTED, Map.of("sessionID", "remote_temp")));
            Flux.interval(Duration.ofSeconds(1), timeoutScheduler)
                    .subscribe(index -> events.tryEmitNext(remote(
                            RunEventType.SESSION_STATUS,
                            Map.of("sessionID", "remote_other", "status", Map.of("type", "busy")))));

            timeoutScheduler.advanceTimeBy(Duration.ofSeconds(5));

            assertThat(fixture.deleteCompleted.await(2, TimeUnit.SECONDS)).isTrue();
            verify(fixture.terminal).fail(any(), eq("旁路问答暂时失败"), eq(TRACE_ID));
            verify(fixture.terminal, never()).succeed(any(), any(), any());
            assertThat(cancelled).isTrue();
            assertThat(fixture.deleteCalls).hasValue(1);
        } finally {
            executor.shutdownNow();
            timeoutScheduler.dispose();
        }
    }

    @Test
    void ignoresOldForkTerminalUntilTheNewAssistantAnswerAppears() throws Exception {
        ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch promptStarted = new CountDownLatch(1);
        reactor.core.publisher.Sinks.One<Void> ready = reactor.core.publisher.Sinks.one();
        reactor.core.publisher.Sinks.Many<RunEventDraft> events =
                reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();
        try {
            Fixture fixture = new Fixture(executor);
            fixture.finalMessages = finalAnswer("新答案");
            when(fixture.runtime.openRunEventStream(any())).thenReturn(new AgentEventStream(
                    ready.asMono(),
                    events.asFlux().doOnSubscribe(ignored -> subscribed.countDown())));
            when(fixture.runtime.startRun(any())).thenAnswer(invocation -> {
                fixture.promptMessageId.set(((AgentStartRunCommand) invocation.getArgument(0)).messageId());
                promptStarted.countDown();
                return Mono.just(new com.icbc.testagent.agent.runtime.AgentStartRunResult(true));
            });

            fixture.service.start(USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);

            assertThat(subscribed.await(2, TimeUnit.SECONDS)).isTrue();
            events.tryEmitNext(remote(RunEventType.RUN_SUCCEEDED, Map.of("sessionID", "remote_temp")));
            assertThat(fixture.deleteCalls).hasValue(0);
            ready.tryEmitEmpty();
            assertThat(promptStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.deleteCalls).hasValue(0);

            assertThat(events.tryEmitNext(remote(RunEventType.MESSAGE_UPDATED, Map.of(
                    "sessionID", "remote_temp",
                    "info", Map.of("id", "msg_answer", "role", "assistant")))).isSuccess()).isTrue();
            assertThat(events.tryEmitNext(remote(RunEventType.MESSAGE_PART_UPDATED, Map.of(
                    "sessionID", "remote_temp",
                    "part", Map.of(
                            "id", "part_answer", "messageID", "msg_answer", "type", "text")))).isSuccess()).isTrue();
            assertThat(events.tryEmitNext(remote(RunEventType.MESSAGE_PART_DELTA, Map.of(
                    "sessionID", "remote_temp",
                    "partID", "part_answer",
                    "messageID", "msg_answer",
                    "delta", "新答案"))).isSuccess()).isTrue();
            assertThat(events.tryEmitNext(remote(
                            RunEventType.RUN_SUCCEEDED, Map.of("sessionID", "remote_temp"))).isSuccess())
                    .isTrue();
            events.tryEmitComplete();

            assertThat(fixture.deleteCompleted.await(2, TimeUnit.SECONDS)).isTrue();
            verify(fixture.terminal).succeed(
                    any(),
                    org.mockito.ArgumentMatchers.argThat((Map<String, Object> payload) ->
                            "新答案".equals(payload.get("answer"))),
                    eq(TRACE_ID));
            verify(fixture.terminal, never()).fail(any(), any(), any());
            verify(fixture.liveBus).publishTransient(org.mockito.ArgumentMatchers.argThat(
                    event -> event.type() == RunEventType.SIDE_QUESTION_DELTA));
            assertThat(fixture.deleteCalls).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void newAssistantAnswerCompletesWithoutBusyOrRunStartedSignal() throws Exception {
        ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch promptStarted = new CountDownLatch(1);
        CountDownLatch terminalSucceeded = new CountDownLatch(1);
        reactor.core.publisher.Sinks.One<Void> ready = reactor.core.publisher.Sinks.one();
        reactor.core.publisher.Sinks.Many<RunEventDraft> events =
                reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();
        try {
            Fixture fixture = new Fixture(executor);
            fixture.finalMessages = finalAnswer("legacy 新答案");
            when(fixture.runtime.openRunEventStream(any())).thenReturn(new AgentEventStream(
                    ready.asMono(),
                    events.asFlux().doOnSubscribe(ignored -> subscribed.countDown())));
            when(fixture.runtime.startRun(any())).thenAnswer(invocation -> {
                fixture.promptMessageId.set(((AgentStartRunCommand) invocation.getArgument(0)).messageId());
                promptStarted.countDown();
                return Mono.just(new com.icbc.testagent.agent.runtime.AgentStartRunResult(true));
            });
            when(fixture.terminal.succeed(any(), any(), any())).thenAnswer(invocation -> {
                terminalSucceeded.countDown();
                return true;
            });

            fixture.service.start(USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);

            assertThat(subscribed.await(2, TimeUnit.SECONDS)).isTrue();
            events.tryEmitNext(remote(RunEventType.SESSION_STATUS, Map.of(
                    "sessionID", "remote_temp",
                    "status", Map.of("type", "idle"))));
            events.tryEmitNext(remote(RunEventType.RUN_SUCCEEDED, Map.of("sessionID", "remote_temp")));
            assertThat(fixture.deleteCalls).hasValue(0);
            ready.tryEmitEmpty();
            assertThat(promptStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.deleteCalls).hasValue(0);

            events.tryEmitNext(remote(RunEventType.MESSAGE_UPDATED, Map.of(
                    "sessionID", "remote_temp",
                    "info", Map.of("id", "msg_answer", "role", "assistant"))));
            events.tryEmitNext(remote(RunEventType.MESSAGE_PART_UPDATED, Map.of(
                    "sessionID", "remote_temp",
                    "part", Map.of(
                            "id", "part_answer", "messageID", "msg_answer", "type", "text"))));
            events.tryEmitNext(remote(RunEventType.MESSAGE_PART_DELTA, Map.of(
                    "sessionID", "remote_temp",
                    "partID", "part_answer",
                    "messageID", "msg_answer",
                    "delta", "legacy 新答案")));
            events.tryEmitNext(remote(RunEventType.SESSION_STATUS, Map.of(
                    "sessionID", "remote_temp",
                    "status", Map.of("type", "idle"))));
            events.tryEmitNext(remote(RunEventType.RUN_SUCCEEDED, Map.of("sessionID", "remote_temp")));
            events.tryEmitComplete();

            assertThat(terminalSucceeded.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.deleteCompleted.await(2, TimeUnit.SECONDS)).isTrue();
            verify(fixture.terminal).succeed(
                    any(),
                    org.mockito.ArgumentMatchers.argThat((Map<String, Object> payload) ->
                            "legacy 新答案".equals(payload.get("answer"))),
                    eq(TRACE_ID));
            verify(fixture.terminal, never()).fail(any(), any(), any());
            verify(fixture.liveBus).publishTransient(org.mockito.ArgumentMatchers.argThat(
                    event -> event.type() == RunEventType.SIDE_QUESTION_DELTA));
            assertThat(fixture.deleteCalls).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void completedMessageSnapshotRecoversWhenEventStreamMissesTerminal() throws Exception {
        VirtualTimeScheduler timeoutScheduler = VirtualTimeScheduler.create();
        ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            Fixture fixture = new Fixture(executor, Duration.ofSeconds(10), timeoutScheduler);
            fixture.failurePoint = FailurePoint.STREAM_NO_TERMINAL;
            fixture.finalMessages = completedFinalAnswer("快照恢复答案");
            fixture.rewireRuntime();

            fixture.service.start(USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);

            verify(fixture.runtime, org.mockito.Mockito.timeout(2_000)).startRun(any());
            timeoutScheduler.advanceTimeBy(SideQuestionPolicy.MESSAGE_RECOVERY_INTERVAL);

            assertThat(fixture.deleteCompleted.await(2, TimeUnit.SECONDS)).isTrue();
            verify(fixture.terminal).succeed(
                    any(),
                    org.mockito.ArgumentMatchers.argThat((Map<String, Object> payload) ->
                            "快照恢复答案".equals(payload.get("answer"))),
                    eq(TRACE_ID));
            verify(fixture.terminal, never()).fail(any(), any(), any());
        } finally {
            executor.shutdownNow();
            timeoutScheduler.dispose();
        }
    }

    @Test
    void independentArchivedRunDoesNotReplaceMainSessionsActiveRun() {
        TrackingRunRepository runRepository = new TrackingRunRepository();
        Run mainRunning = new Run(
                new RunId("run_mainactive1234567890"),
                MAIN_SESSION_ID,
                WORKSPACE_ID,
                RunStatus.RUNNING,
                NOW,
                NOW,
                TRACE_ID);
        runRepository.save(mainRunning);
        Fixture fixture = new Fixture(command -> {
        }, runRepository);

        SideQuestionRunStartResult result = fixture.service.start(
                USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);

        assertThat(result.runId()).isNotEqualTo(mainRunning.runId());
        assertThat(runRepository.findLatestActiveBySessionId(MAIN_SESSION_ID)).contains(mainRunning);
        assertThat(runRepository.findById(result.runId())).get().satisfies(sideRun -> {
            assertThat(sideRun.sessionId()).isNotEqualTo(MAIN_SESSION_ID);
            assertThat(sideRun.sourceType()).isEqualTo(ConversationSourceType.SIDE_QUESTION);
        });
    }

    @Test
    void invalidSurrogateInLongAnswerIsReplacedBeforeUtf8BoundedTerminalPayload() {
        Fixture fixture = new Fixture(Runnable::run);
        fixture.finalMessages = finalAnswer("a".repeat(65_530) + '\uD800' + "🐾".repeat(10));
        fixture.rewireRuntime();
        AtomicReference<Map<String, Object>> terminalPayload = new AtomicReference<>();
        when(fixture.terminal.succeed(any(), any(), any())).thenAnswer(invocation -> {
            terminalPayload.set(invocation.getArgument(1));
            return true;
        });

        fixture.service.start(USER_ID, "opencode", MAIN_SESSION_ID, "问题", null, null, TRACE_ID);

        String answer = (String) terminalPayload.get().get("answer");
        assertThat(answer).doesNotContain("\uD800");
        assertThat(answer.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64 * 1024);
    }

    private static AgentSessionMessagesResult context(int count, String text) {
        List<AgentSessionMessage> messages = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            messages.add(new AgentSessionMessage(
                    Map.of("id", "msg_" + index, "role", "user"),
                    List.of(Map.of("type", "text", "text", text))));
        }
        return new AgentSessionMessagesResult(messages);
    }

    private static AgentSessionMessagesResult finalAnswer(String answer) {
        return new AgentSessionMessagesResult(List.of(new AgentSessionMessage(
                Map.of("id", "msg_answer", "role", "assistant"),
                List.of(Map.of("type", "text", "text", answer)))));
    }

    private static AgentSessionMessagesResult completedFinalAnswer(String answer) {
        return new AgentSessionMessagesResult(List.of(new AgentSessionMessage(
                Map.of("id", "msg_answer", "role", "assistant", "finish", "stop"),
                List.of(Map.of("type", "text", "text", answer)))));
    }

    private static List<RunEventDraft> remoteAnswerEvents() {
        return List.of(
                remote(RunEventType.RUN_STARTED, Map.of("sessionID", "remote_temp")),
                remote(RunEventType.MESSAGE_UPDATED, Map.of(
                        "sessionID", "remote_temp",
                        "info", Map.of(
                                "id", "msg_answer",
                                "parentID", "msg_old_answer",
                                "role", "assistant"))),
                remote(RunEventType.MESSAGE_PART_UPDATED, Map.of(
                        "sessionID", "remote_temp",
                        "part", Map.of("id", "part_answer", "messageID", "msg_answer", "type", "text"))),
                remote(RunEventType.TOOL_STARTED, Map.of(
                        "sessionID", "remote_temp", "tool", "read", "input", Map.of("path", "/secret"))),
                remote(RunEventType.MESSAGE_PART_DELTA, Map.of(
                        "sessionID", "remote_temp", "partID", "part_answer", "messageID", "msg_answer", "delta", "最终")),
                remote(RunEventType.RUN_SUCCEEDED, Map.of("sessionID", "remote_temp")));
    }

    private static RunEventDraft remote(RunEventType type, Map<String, Object> rawPayload) {
        return new RunEventDraft(
                new RunId("run_remoteplaceholder01"),
                type,
                TRACE_ID,
                NOW,
                Map.of("rawPayload", rawPayload));
    }

    private enum FailurePoint {
        FORK,
        PROMPT,
        STREAM,
        FINAL_ANSWER,
        READY_FAILURE,
        PROMPT_REJECTED,
        STREAM_NO_TERMINAL
    }

    private static final class TrackingRunRepository implements RunRepository {
        private final java.util.LinkedHashMap<RunId, Run> values = new java.util.LinkedHashMap<>();

        @Override
        public synchronized Run save(Run run) {
            values.put(run.runId(), run);
            return run;
        }

        @Override
        public synchronized Run saveIfStatus(Run run, RunStatus expectedStatus) {
            Run current = values.get(run.runId());
            if (current != null && current.status() == expectedStatus) {
                return save(run);
            }
            return current;
        }

        @Override
        public synchronized Optional<Run> findById(RunId runId) {
            return Optional.ofNullable(values.get(runId));
        }

        @Override
        public synchronized Optional<Run> findLatestActiveBySessionId(SessionId sessionId) {
            return values.values().stream()
                    .filter(run -> run.sessionId().equals(sessionId) && !run.status().isTerminal())
                    .reduce((first, second) -> second);
        }
    }

    private static final class Fixture {
        private final SessionRepository sessions = mock(SessionRepository.class);
        private final RunRepository runs;
        private final RoutingDecisionRepository routingDecisions = mock(RoutingDecisionRepository.class);
        private final RunEventAppender eventAppender = mock(RunEventAppender.class);
        private final RunEventLiveBus liveBus = mock(RunEventLiveBus.class);
        private final SideQuestionTerminalService terminal = mock(SideQuestionTerminalService.class);
        private final AgentRuntimeTargetResolver targetResolver = mock(AgentRuntimeTargetResolver.class);
        private final AgentRuntime runtime = mock(AgentRuntime.class);
        private final ExecutionNode node = new ExecutionNode(
                new ExecutionNodeId("node_sidequestionstream01"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                2,
                NOW);
        private final List<RunEventDraft> durableEvents = new ArrayList<>();
        private final AtomicReference<Integer> deleteCalls = new AtomicReference<>(0);
        private final AtomicReference<String> promptMessageId = new AtomicReference<>();
        private final CountDownLatch deleteCompleted = new CountDownLatch(1);
        private SideQuestionStreamingApplicationService service;
        private List<String> order = new ArrayList<>();
        private AgentSessionMessagesResult context = context(1, "短上下文");
        private AgentSessionMessagesResult forkBaselineMessages = new AgentSessionMessagesResult(List.of(
                new AgentSessionMessage(
                        Map.of("id", "msg_old_answer", "role", "assistant", "finish", "stop"),
                        List.of(Map.of("type", "text", "text", "历史答案")))));
        private AgentSessionMessagesResult finalMessages = finalAnswer("答案");
        private Flux<RunEventDraft> remoteEvents = Flux.fromIterable(remoteAnswerEvents());
        private boolean failMapping;
        private FailurePoint failurePoint;

        private Fixture(java.util.concurrent.Executor executor) {
            this(executor, mock(RunRepository.class));
        }

        private Fixture(
                java.util.concurrent.Executor executor,
                Duration taskTimeout,
                reactor.core.scheduler.Scheduler timeoutScheduler) {
            this(executor, mock(RunRepository.class), taskTimeout, timeoutScheduler);
        }

        private Fixture(java.util.concurrent.Executor executor, RunRepository runs) {
            this(executor, runs, SideQuestionPolicy.TASK_TIMEOUT, Schedulers.parallel());
        }

        private Fixture(
                java.util.concurrent.Executor executor,
                RunRepository runs,
                Duration taskTimeout,
                reactor.core.scheduler.Scheduler timeoutScheduler) {
            this.runs = runs;
            Session main = new Session(MAIN_SESSION_ID, WORKSPACE_ID, "主会话", SessionStatus.ACTIVE, NOW, NOW, TRACE_ID);
            when(sessions.findById(MAIN_SESSION_ID)).thenReturn(Optional.of(main));
            when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(sessions.attachOpencodeSession(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
                order.add("mapping");
                if (failMapping) {
                    return Optional.empty();
                }
                Session internal = new Session(
                        invocation.getArgument(0),
                        WORKSPACE_ID,
                        "宠物旁路问答（内部）",
                        SessionStatus.ARCHIVED,
                        NOW,
                        NOW,
                        TRACE_ID);
                return Optional.of(internal.attachOpencodeSession(
                        invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4)));
            });
            if (org.mockito.Mockito.mockingDetails(runs).isMock()) {
                when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
                when(runs.saveIfStatus(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
            }
            when(eventAppender.append(any())).thenAnswer(invocation -> {
                durableEvents.add(invocation.getArgument(0));
                return null;
            });
            when(targetResolver.sessionTarget("opencode", USER_ID, MAIN_SESSION_ID.value(), TRACE_ID))
                    .thenReturn(new AgentRuntimeTargetResolver.SessionRuntimeTarget(
                            runtime, node, "/workspace", "remote_main"));
            rewireRuntime();
            service = new SideQuestionStreamingApplicationService(
                    sessions,
                    runs,
                    routingDecisions,
                    eventAppender,
                    liveBus,
                    targetResolver,
                    terminal,
                    Schedulers.fromExecutor(executor),
                    taskTimeout,
                    timeoutScheduler);
        }

        private void rewireRuntime() {
            org.mockito.Mockito.reset(runtime);
            reactor.core.publisher.Sinks.Many<RunEventDraft> eventSink =
                    reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();
            Flux<RunEventDraft> selectedEvents = failurePoint == FailurePoint.STREAM
                    ? Flux.error(new IllegalStateException("stream failed"))
                    : failurePoint == FailurePoint.STREAM_NO_TERMINAL
                            ? Flux.empty()
                            : remoteEvents;
            when(runtime.sessionMessages(any())).thenAnswer(invocation -> {
                String remoteSessionId = ((com.icbc.testagent.agent.runtime.AgentSessionMessagesCommand) invocation.getArgument(0))
                        .remoteSessionId();
                return Mono.just("remote_main".equals(remoteSessionId)
                        ? context
                        : promptMessageId.get() == null ? forkBaselineMessages : finalMessages);
            });
            when(runtime.runtime(any())).thenAnswer(invocation -> {
                AgentRuntimeCommand command = invocation.getArgument(0);
                if (command.path().endsWith("/fork")) {
                    order.add("fork");
                    if (failurePoint == FailurePoint.FORK) {
                        return Mono.error(new IllegalStateException("fork failed with /private/path"));
                    }
                    return Mono.just(new AgentRuntimeResult(JSON.valueToTree(Map.of("id", "remote_temp"))));
                }
                if ("DELETE".equals(command.method())) {
                    order.add("delete");
                    deleteCalls.updateAndGet(value -> value + 1);
                    deleteCompleted.countDown();
                    return Mono.just(new AgentRuntimeResult(JSON.valueToTree(Map.of("ok", true))));
                }
                return Mono.just(new AgentRuntimeResult(JSON.valueToTree(Map.of("ok", true))));
            });
            when(runtime.openRunEventStream(any())).thenAnswer(invocation -> {
                Mono<Void> ready = failurePoint == FailurePoint.READY_FAILURE
                        ? Mono.error(new IllegalStateException("event stream handshake failed"))
                        : Mono.empty();
                return new AgentEventStream(
                        ready,
                        eventSink.asFlux().doOnSubscribe(ignored -> order.add("subscribe")));
            });
            when(runtime.startRun(any())).thenAnswer(invocation -> {
                order.add("prompt");
                AgentStartRunCommand command = invocation.getArgument(0);
                if (command != null) {
                    promptMessageId.set(command.messageId());
                }
                if (failurePoint == FailurePoint.PROMPT) {
                    return Mono.error(new IllegalStateException("prompt failed"));
                }
                boolean accepted = failurePoint != FailurePoint.PROMPT_REJECTED;
                if (accepted) {
                    selectedEvents.subscribe(
                            eventSink::tryEmitNext,
                            eventSink::tryEmitError,
                            eventSink::tryEmitComplete);
                }
                return Mono.just(new com.icbc.testagent.agent.runtime.AgentStartRunResult(accepted));
            });
        }
    }
}
