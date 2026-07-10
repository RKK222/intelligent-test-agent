package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.OpencodeAgentRuntime;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunSessionScope;
import com.icbc.testagent.domain.event.RunSessionScopeRepository;
import com.icbc.testagent.domain.event.RunSessionScopeSession;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunConversationSummary;
import com.icbc.testagent.domain.run.RunDetailsLocator;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunRuntimeManifest;
import com.icbc.testagent.domain.run.RunRuntimeReplay;
import com.icbc.testagent.domain.run.RunRuntimeSnapshot;
import com.icbc.testagent.domain.run.RunRuntimeStore;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.RunStorageMode;
import com.icbc.testagent.domain.run.RunSummaryPersistencePort;
import com.icbc.testagent.domain.run.RunSummaryStatus;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.event.RunEventSsePayload;
import com.icbc.testagent.opencode.client.OpencodeCancelCommand;
import com.icbc.testagent.opencode.client.OpencodeCancelResult;
import com.icbc.testagent.opencode.client.OpencodeClientFacade;
import com.icbc.testagent.opencode.client.OpencodeCreateSessionCommand;
import com.icbc.testagent.opencode.client.OpencodeCreateSessionResult;
import com.icbc.testagent.opencode.client.OpencodeDiffCommand;
import com.icbc.testagent.opencode.client.OpencodeDiffResult;
import com.icbc.testagent.opencode.client.OpencodeHealthCommand;
import com.icbc.testagent.opencode.client.OpencodeHealthResult;
import com.icbc.testagent.opencode.client.OpencodeRejectDiffCommand;
import com.icbc.testagent.opencode.client.OpencodeRejectDiffResult;
import com.icbc.testagent.opencode.client.OpencodeRuntimeCommand;
import com.icbc.testagent.opencode.client.OpencodeRuntimeResult;
import com.icbc.testagent.opencode.client.OpencodeSessionExistsCommand;
import com.icbc.testagent.opencode.client.OpencodeSessionMessage;
import com.icbc.testagent.opencode.client.OpencodeSessionMessagesCommand;
import com.icbc.testagent.opencode.client.OpencodeSessionMessagesResult;
import com.icbc.testagent.opencode.client.OpencodeStartCommand;
import com.icbc.testagent.opencode.client.OpencodeStartRunCommand;
import com.icbc.testagent.opencode.client.OpencodeStartRunResult;
import com.icbc.testagent.opencode.client.OpencodeStreamEventsCommand;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.Test;

class RunMessageRecoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private static final RunId RUN_ID = new RunId("run_1234567890abcdef");
    private static final SessionId SESSION_ID = new SessionId("ses_1234567890abcdef");
    private static final String REMOTE_SESSION_ID = "ses_remote1234567890abcdef";

    @Test
    void historyRecoveryUsesRedisSnapshotBeforeOpenCodeAndPostgresql() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort summaryPersistence = mock(RunSummaryPersistencePort.class);
        RunRepository runRepository = mock(RunRepository.class);
        com.icbc.testagent.domain.session.SessionRepository sessionRepository =
                mock(com.icbc.testagent.domain.session.SessionRepository.class);
        ExecutionNodeRepository executionNodeRepository = mock(ExecutionNodeRepository.class);
        AgentSessionBindingRepository bindingRepository = mock(AgentSessionBindingRepository.class);
        RunRuntimeManifest manifest = runtimeManifest();
        RunEventDraft snapshotMessage = new RunEventDraft(
                RUN_ID,
                com.icbc.testagent.domain.event.RunEventType.MESSAGE_UPDATED,
                "trace_1234567890abcdef",
                NOW,
                Map.of("message", Map.of(
                        "id", "msg_redis", "role", "assistant", "text", "redis answer")));
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.of(manifest));
        when(runtimeStore.replayAfter(RUN_ID, 0L, RunRuntimeStore.MAX_DURABLE_EVENTS))
                .thenReturn(new RunRuntimeReplay(
                        manifest,
                        new RunRuntimeSnapshot(RUN_ID, 2L, 2L, 0L, List.of(snapshotMessage), NOW),
                        List.of(),
                        false,
                        null));
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                runRepository,
                sessionRepository,
                executionNodeRepository,
                runtimeRegistry(facade),
                bindingRepository,
                null,
                runtimeStore,
                summaryPersistence);

        RunHistoryRecoveryResult result = service
                .recoverHistory(RUN_ID, "trace_1234567890abcdef")
                .block(Duration.ofSeconds(2));

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo(RunHistoryRecoverySource.REDIS);
        assertThat(result.historyRepresentation()).isEqualTo("FULL");
        assertThat(result.replayAvailable()).isTrue();
        assertThat(result.detailsAvailableUntil()).isEqualTo(manifest.detailsExpiresAt());
        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("message.updated");
            assertThat(event.payload()).containsEntry("sessionId", REMOTE_SESSION_ID);
        });
        assertThat(facade.lastCommand).isNull();
        verifyNoInteractions(
                runRepository,
                sessionRepository,
                executionNodeRepository,
                bindingRepository,
                summaryPersistence);
    }

    @Test
    void historyRecoveryFallsBackToPostgresqlSummariesWhenRedisAndOpenCodeAreUnavailable() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.error = new IllegalStateException("opencode unavailable");
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort summaryPersistence = mock(RunSummaryPersistencePort.class);
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.empty());
        when(summaryPersistence.findSummariesByRunId(RUN_ID)).thenReturn(List.of(
                summary("msg_user_summary", SessionMessageRole.USER, "prompt summary"),
                summary("msg_assistant_summary", SessionMessageRole.ASSISTANT, "answer summary")));
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                new FakeRunRepository(run()),
                new FakeSessionRepository(mappedSession()),
                new FakeExecutionNodeRepository(),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                null,
                runtimeStore,
                summaryPersistence);

        RunHistoryRecoveryResult result = service
                .recoverHistory(RUN_ID, "trace_1234567890abcdef")
                .block(Duration.ofSeconds(2));

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo(RunHistoryRecoverySource.POSTGRESQL_SUMMARY);
        assertThat(result.historyRepresentation()).isEqualTo("SUMMARY");
        assertThat(result.replayAvailable()).isFalse();
        assertThat(result.detailsAvailableUntil()).isNull();
        assertThat(result.events()).extracting(RunEventSsePayload::type)
                .containsExactly(
                        "message.updated", "message.part.updated",
                        "message.updated", "message.part.updated");
        assertThat(result.events().get(0).payload()).containsEntry("contentKind", "SUMMARY");
        assertThat(result.events().get(0).payload()).containsEntry("summaryStatus", "COMPLETE");
    }

    @Test
    void sessionHistoryUsesRecentRedisSnapshotsInChronologicalRunOrder() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.error = new IllegalStateException("opencode unavailable");
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort summaryPersistence = mock(RunSummaryPersistencePort.class);
        RunRuntimeManifest newer = runtimeManifest();
        RunId olderRunId = new RunId("run_0000000000000001");
        RunRuntimeManifest older = runtimeManifest(olderRunId, NOW.minusSeconds(60));
        when(runtimeStore.findRecentBySession(SESSION_ID, 100)).thenReturn(List.of(newer, older));
        when(runtimeStore.replayAfter(olderRunId, 0L, RunRuntimeStore.MAX_DURABLE_EVENTS))
                .thenReturn(replayWithText(older, "older"));
        when(runtimeStore.replayAfter(RUN_ID, 0L, RunRuntimeStore.MAX_DURABLE_EVENTS))
                .thenReturn(replayWithText(newer, "newer"));
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                new FakeRunRepository(run()),
                new FakeSessionRepository(mappedSession()),
                new FakeExecutionNodeRepository(),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                null,
                runtimeStore,
                summaryPersistence);

        RunHistoryRecoveryResult result = service
                .recoverSessionTreeHistory(SESSION_ID, "trace_1234567890abcdef")
                .block(Duration.ofSeconds(2));

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo(RunHistoryRecoverySource.REDIS);
        assertThat(result.events()).extracting(event ->
                        String.valueOf(((Map<?, ?>) event.payload().get("message")).get("text")))
                .containsExactly("older", "newer");
        assertThat(result.detailsAvailableUntil()).isEqualTo(older.detailsExpiresAt());
        assertThat(facade.lastCommand).isNotNull();
        verify(summaryPersistence).findSummariesBySessionId(SESSION_ID);
    }

    @Test
    void sessionHistoryMergesExpiredSummariesBeforeRecentRedisWhenOpenCodeIsUnavailable() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.error = new IllegalStateException("opencode unavailable");
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort summaryPersistence = mock(RunSummaryPersistencePort.class);
        RunRuntimeManifest recent = runtimeManifest();
        RunId expiredRunId = new RunId("run_0000000000000000");
        when(runtimeStore.findRecentBySession(SESSION_ID, 100)).thenReturn(List.of(recent));
        when(runtimeStore.replayAfter(RUN_ID, 0L, RunRuntimeStore.MAX_DURABLE_EVENTS))
                .thenReturn(replayWithText(recent, "recent redis"));
        when(summaryPersistence.findSummariesBySessionId(SESSION_ID)).thenReturn(List.of(
                summary(expiredRunId, "msg_user_expired", SessionMessageRole.USER, "expired user summary"),
                summary(expiredRunId, "msg_assistant_expired", SessionMessageRole.ASSISTANT, "expired answer summary"),
                summary(RUN_ID, "msg_duplicate_recent", SessionMessageRole.ASSISTANT, "must be deduplicated")));
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                new FakeRunRepository(run()),
                new FakeSessionRepository(mappedSession()),
                new FakeExecutionNodeRepository(),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                null,
                runtimeStore,
                summaryPersistence);

        RunHistoryRecoveryResult result = service
                .recoverSessionTreeHistory(SESSION_ID, "trace_1234567890abcdef")
                .block(Duration.ofSeconds(2));

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo(RunHistoryRecoverySource.REDIS_POSTGRESQL_SUMMARY);
        assertThat(result.historyRepresentation()).isEqualTo("SUMMARY");
        assertThat(result.replayAvailable()).isFalse();
        assertThat(result.detailsAvailableUntil()).isEqualTo(recent.detailsExpiresAt());
        assertThat(result.events()).extracting(event -> event.payload().toString())
                .anyMatch(payload -> payload.contains("expired user summary"))
                .anyMatch(payload -> payload.contains("expired answer summary"))
                .anyMatch(payload -> payload.contains("recent redis"))
                .noneMatch(payload -> payload.contains("must be deduplicated"));
    }

    @Test
    void recoveryLoadsOpencodeProjectedMessagesAsTransientSnapshotEvents() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.result = new OpencodeSessionMessagesResult(
                List.of(new OpencodeSessionMessage(
                        Map.of("id", "msg_1", "type", "assistant", "role", "assistant"),
                        List.of(Map.of("id", "part_1", "messageID", "msg_1", "type", "text", "text", "hello")))),
                null,
                null);
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                new FakeRunRepository(run()),
                new FakeSessionRepository(mappedSession()),
                new FakeExecutionNodeRepository(),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        List<RunEventSsePayload> payloads = service.recover(RUN_ID, "trace_1234567890abcdef")
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(payloads).hasSize(2);
        assertThat(payloads).extracting(RunEventSsePayload::type)
                .containsExactly("message.updated", "message.part.updated");
        assertThat(payloads).allSatisfy(payload -> {
            assertThat(payload.seq()).isZero();
            assertThat(payload.eventId()).startsWith("evt_live_");
        });
        assertThat(payloads.get(0).payload()).containsKey("message");
        Map<?, ?> messagePayload = (Map<?, ?>) payloads.get(0).payload().get("message");
        Map<?, ?> partPayload = (Map<?, ?>) payloads.get(1).payload().get("part");
        assertThat(messagePayload.get("id")).isEqualTo("msg_1");
        assertThat(partPayload.get("text")).isEqualTo("hello");
        assertThat(facade.lastCommand.opencodeSessionId()).isEqualTo(REMOTE_SESSION_ID);
        assertThat(facade.lastCommand.limit()).isEqualTo(100);
        assertThat(facade.lastCommand.order()).isEqualTo("asc");
    }

    @Test
    void recoveryLoadsRootAndChildMessagesWhenRunScopeExists() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.resultsBySession.put(REMOTE_SESSION_ID, new OpencodeSessionMessagesResult(
                List.of(new OpencodeSessionMessage(
                        Map.of("id", "msg_root", "type", "assistant", "role", "assistant"),
                        List.of(Map.of("id", "part_root", "messageID", "msg_root", "type", "text", "text", "root")))),
                null,
                null));
        facade.resultsBySession.put("ses_child1234567890abcdef", new OpencodeSessionMessagesResult(
                List.of(new OpencodeSessionMessage(
                        Map.of("id", "msg_child", "type", "assistant", "role", "assistant"),
                        List.of(Map.of("id", "part_child", "messageID", "msg_child", "type", "text", "text", "child")))),
                null,
                null));
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                new FakeRunRepository(run()),
                new FakeSessionRepository(mappedSession()),
                new FakeExecutionNodeRepository(),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new FakeRunSessionScopeRepository(List.of(
                        scopeSession(REMOTE_SESSION_ID, null, false),
                        scopeSession("ses_child1234567890abcdef", REMOTE_SESSION_ID, true))));

        List<RunEventSsePayload> payloads = service.recover(RUN_ID, "trace_1234567890abcdef")
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(payloads).extracting(RunEventSsePayload::type)
                .containsExactly(
                        "message.updated",
                        "message.part.updated",
                        "message.updated",
                        "message.part.updated");
        assertThat(payloads.get(0).payload())
                .containsEntry("rootSessionId", REMOTE_SESSION_ID)
                .containsEntry("sessionId", REMOTE_SESSION_ID)
                .containsEntry("isChildSession", false);
        assertThat(payloads.get(2).payload())
                .containsEntry("rootSessionId", REMOTE_SESSION_ID)
                .containsEntry("sessionId", "ses_child1234567890abcdef")
                .containsEntry("parentSessionId", REMOTE_SESSION_ID)
                .containsEntry("isChildSession", true);
    }

    @Test
    void recoveryLoadsFullSessionTreeByRootSession() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.resultsBySession.put(REMOTE_SESSION_ID, new OpencodeSessionMessagesResult(
                List.of(new OpencodeSessionMessage(
                        Map.of("id", "msg_root", "type", "assistant", "role", "assistant"),
                        List.of(Map.of("id", "part_root", "messageID", "msg_root", "type", "text", "text", "root")))),
                null,
                null));
        facade.resultsBySession.put("ses_child1234567890abcdef", new OpencodeSessionMessagesResult(
                List.of(new OpencodeSessionMessage(
                        Map.of("id", "msg_child", "type", "assistant", "role", "assistant"),
                        List.of(Map.of("id", "part_child", "messageID", "msg_child", "type", "text", "text", "child")))),
                null,
                null));
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                new FakeRunRepository(run()),
                new FakeSessionRepository(mappedSession()),
                new FakeExecutionNodeRepository(),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new FakeRunSessionScopeRepository(List.of(
                        scopeSession(REMOTE_SESSION_ID, null, false),
                        scopeSession("ses_child1234567890abcdef", REMOTE_SESSION_ID, true))));

        List<RunEventSsePayload> payloads = service.recoverSessionTree(SESSION_ID, "trace_1234567890abcdef")
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(payloads).extracting(RunEventSsePayload::type)
                .containsExactly(
                        "message.updated",
                        "message.part.updated",
                        "message.updated",
                        "message.part.updated");
        assertThat(facade.requestedSessionIds)
                .containsExactly(REMOTE_SESSION_ID, "ses_child1234567890abcdef");
        assertThat(payloads.get(2).payload())
                .containsEntry("rootSessionId", REMOTE_SESSION_ID)
                .containsEntry("sessionId", "ses_child1234567890abcdef")
                .containsEntry("parentSessionId", REMOTE_SESSION_ID)
                .containsEntry("isChildSession", true);
    }

    @Test
    void recoveryDiscoversChildFromRootTaskPartMetadataWhenScopeIsMissing() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.resultsBySession.put(REMOTE_SESSION_ID, new OpencodeSessionMessagesResult(
                List.of(new OpencodeSessionMessage(
                        Map.of("id", "msg_root", "type", "assistant", "role", "assistant"),
                        List.of(Map.of(
                                "id", "part_task",
                                "messageID", "msg_root",
                                "callID", "call_task",
                                "type", "tool",
                                "metadata", Map.of("sessionID", "ses_child1234567890abcdef"))))),
                null,
                null));
        facade.resultsBySession.put("ses_child1234567890abcdef", new OpencodeSessionMessagesResult(
                List.of(new OpencodeSessionMessage(
                        Map.of("id", "msg_child", "type", "assistant", "role", "assistant"),
                        List.of(Map.of("id", "part_child", "messageID", "msg_child", "type", "text", "text", "child")))),
                null,
                null));
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                new FakeRunRepository(run()),
                new FakeSessionRepository(mappedSession()),
                new FakeExecutionNodeRepository(),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository(),
                new FakeRunSessionScopeRepository(List.of(scopeSession(REMOTE_SESSION_ID, null, false))));

        List<RunEventSsePayload> payloads = service.recover(RUN_ID, "trace_1234567890abcdef")
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(facade.requestedSessionIds)
                .containsExactly(REMOTE_SESSION_ID, "ses_child1234567890abcdef");
        assertThat(payloads).extracting(RunEventSsePayload::type)
                .containsExactly(
                        "message.updated",
                        "message.part.updated",
                        "message.updated",
                        "message.part.updated");
        assertThat(payloads.get(2).payload())
                .containsEntry("rootSessionId", REMOTE_SESSION_ID)
                .containsEntry("sessionId", "ses_child1234567890abcdef")
                .containsEntry("parentSessionId", REMOTE_SESSION_ID)
                .containsEntry("isChildSession", true)
                .containsEntry("taskMessageId", "msg_root")
                .containsEntry("taskPartId", "part_task")
                .containsEntry("taskCallId", "call_task");
    }

    @Test
    void recoveryDoesNotReplayUserPartsAsAssistantParts() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.result = new OpencodeSessionMessagesResult(
                List.of(
                        new OpencodeSessionMessage(
                                Map.of("id", "msg_user_1", "role", "user"),
                                List.of(Map.of(
                                        "id", "part_user_1",
                                        "messageID", "msg_user_1",
                                        "type", "text",
                                        "text", "用户提示词"))),
                        new OpencodeSessionMessage(
                                Map.of("id", "msg_assistant_1", "role", "assistant"),
                                List.of(Map.of(
                                        "id", "part_assistant_1",
                                        "messageID", "msg_assistant_1",
                                        "type", "text",
                                        "text", "助手回答")))),
                null,
                null);
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                new FakeRunRepository(run()),
                new FakeSessionRepository(mappedSession()),
                new FakeExecutionNodeRepository(),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        List<RunEventSsePayload> payloads = service.recover(RUN_ID, "trace_1234567890abcdef")
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(payloads).extracting(RunEventSsePayload::type)
                .containsExactly("message.updated", "message.part.updated");
        Map<?, ?> messagePayload = (Map<?, ?>) payloads.get(0).payload().get("message");
        Map<?, ?> partPayload = (Map<?, ?>) payloads.get(1).payload().get("part");
        assertThat(messagePayload.get("id")).isEqualTo("msg_assistant_1");
        assertThat(partPayload.get("text")).isEqualTo("助手回答");
    }

    @Test
    void openCodeHistoryKeepsUserMessageWhenRedisDetailsHaveExpired() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.result = new OpencodeSessionMessagesResult(
                List.of(
                        new OpencodeSessionMessage(
                                Map.of("id", "msg_user_history", "role", "user"),
                                List.of(Map.of(
                                        "id", "part_user_history",
                                        "messageID", "msg_user_history",
                                        "type", "text",
                                        "text", "完整用户输入"))),
                        new OpencodeSessionMessage(
                                Map.of("id", "msg_assistant_history", "role", "assistant"),
                                List.of(Map.of(
                                        "id", "part_assistant_history",
                                        "messageID", "msg_assistant_history",
                                        "type", "text",
                                        "text", "完整助手回答")))),
                null,
                null);
        RunRuntimeStore runtimeStore = mock(RunRuntimeStore.class);
        RunSummaryPersistencePort summaryPersistence = mock(RunSummaryPersistencePort.class);
        RunRepository runRepository = mock(RunRepository.class);
        com.icbc.testagent.domain.session.SessionRepository sessionRepository =
                mock(com.icbc.testagent.domain.session.SessionRepository.class);
        AgentSessionBindingRepository bindingRepository = mock(AgentSessionBindingRepository.class);
        when(runtimeStore.findManifest(RUN_ID)).thenReturn(Optional.empty());
        when(summaryPersistence.findDetailsLocator(RUN_ID)).thenReturn(Optional.of(new RunDetailsLocator(
                RUN_ID,
                RunStorageMode.REDIS_SUMMARY,
                REMOTE_SESSION_ID,
                node().executionNodeId().value(),
                "msg_remote",
                "part_remote",
                NOW.plus(Duration.ofHours(24)))));
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                runRepository,
                sessionRepository,
                new FakeExecutionNodeRepository(),
                runtimeRegistry(facade),
                bindingRepository,
                null,
                runtimeStore,
                summaryPersistence);

        RunHistoryRecoveryResult result = service
                .recoverHistory(RUN_ID, "trace_1234567890abcdef")
                .block(Duration.ofSeconds(2));

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo(RunHistoryRecoverySource.OPENCODE_REDIS_SUMMARY);
        assertThat(result.events()).extracting(event -> event.type())
                .containsExactly(
                        "message.updated", "message.part.updated",
                        "message.updated", "message.part.updated");
        assertThat(((Map<?, ?>) result.events().get(0).payload().get("message")).get("role"))
                .isEqualTo("user");
        verifyNoInteractions(runRepository, sessionRepository, bindingRepository);
    }

    @Test
    void recoverySkipsWhenSessionHasNoOpencodeMapping() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                new FakeRunRepository(run()),
                new FakeSessionRepository(session()),
                new FakeExecutionNodeRepository(),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        List<RunEventSsePayload> payloads = service.recover(RUN_ID, "trace_1234567890abcdef")
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(payloads).isEmpty();
        assertThat(facade.lastCommand).isNull();
    }

    @Test
    void recoverySkipsWhenOpencodeMessagesCannotBeLoaded() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        facade.error = new IllegalStateException("opencode unavailable");
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                new FakeRunRepository(run()),
                new FakeSessionRepository(mappedSession()),
                new FakeExecutionNodeRepository(),
                runtimeRegistry(facade),
                new FakeAgentSessionBindingRepository());

        List<RunEventSsePayload> payloads = service.recover(RUN_ID, "trace_1234567890abcdef")
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(payloads).isEmpty();
        assertThat(facade.lastCommand).isNotNull();
    }

    private static RunRuntimeManifest runtimeManifest() {
        return runtimeManifest(RUN_ID, NOW);
    }

    private static RunRuntimeManifest runtimeManifest(RunId runId, Instant createdAt) {
        return new RunRuntimeManifest(
                runId,
                RunStorageMode.REDIS_SUMMARY,
                new UserId("usr_1234567890abcdef"),
                SESSION_ID,
                new WorkspaceId("wrk_1234567890abcdef"),
                "opencode",
                "client_1234567890abcdef",
                "msg_dispatch_1234567890abcdef",
                "server-a",
                "backend-a",
                node().executionNodeId().value(),
                "process_1234567890abcdef",
                REMOTE_SESSION_ID,
                RunStatus.SUCCEEDED,
                2L,
                2L,
                1L,
                0L,
                false,
                2L,
                1024L,
                null,
                null,
                null,
                createdAt.plusSeconds(86_400),
                createdAt,
                createdAt.plusSeconds(1));
    }

    private static RunRuntimeReplay replayWithText(RunRuntimeManifest manifest, String text) {
        RunEventDraft message = new RunEventDraft(
                manifest.runId(),
                com.icbc.testagent.domain.event.RunEventType.MESSAGE_UPDATED,
                "trace_1234567890abcdef",
                manifest.createdAt(),
                Map.of(
                        "rootSessionId", REMOTE_SESSION_ID,
                        "sessionId", REMOTE_SESSION_ID,
                        "isChildSession", false,
                        "message", Map.of(
                                "id", "msg_" + text,
                                "role", "assistant",
                                "text", text)));
        return new RunRuntimeReplay(
                manifest,
                new RunRuntimeSnapshot(manifest.runId(), 1L, 1L, 0L, List.of(message), NOW),
                List.of(),
                false,
                null);
    }

    private static RunConversationSummary summary(
            String messageId,
            SessionMessageRole role,
            String content) {
        return summary(RUN_ID, messageId, role, content);
    }

    private static RunConversationSummary summary(
            RunId runId,
            String messageId,
            SessionMessageRole role,
            String content) {
        return new RunConversationSummary(
                new SessionMessageId(messageId),
                role,
                content,
                runId.value() + ":" + role.name().toLowerCase(Locale.ROOT),
                1,
                RunSummaryStatus.COMPLETE,
                NOW,
                role == SessionMessageRole.ASSISTANT ? "msg_remote" : null);
    }

    private static Run run() {
        return new Run(
                RUN_ID,
                SESSION_ID,
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.RUNNING,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static Session session() {
        return new Session(
                SESSION_ID,
                new WorkspaceId("wrk_1234567890abcdef"),
                "Demo session",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static Session mappedSession() {
        return session().attachOpencodeSession(
                REMOTE_SESSION_ID,
                node().executionNodeId(),
                NOW,
                "trace_1234567890abcdef");
    }

    private static ExecutionNode node() {
        return new ExecutionNode(
                new ExecutionNodeId("node_1234567890abcdef"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                4,
                100,
                NOW,
                Set.of("chat"),
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static RunSessionScopeSession scopeSession(String sessionId, String parentSessionId, boolean child) {
        return new RunSessionScopeSession(
                RUN_ID,
                sessionId,
                REMOTE_SESSION_ID,
                parentSessionId,
                child,
                child ? "TASK_PART" : "ROOT",
                child ? "msg_task" : null,
                child ? "part_task" : null,
                child ? "call_task" : null,
                "trace_1234567890abcdef",
                NOW,
                NOW,
                Map.of());
    }

    private record FakeRunRepository(Run run) implements RunRepository {
        @Override
        public Run save(Run run) {
            return run;
        }

        @Override
        public Optional<Run> findById(RunId runId) {
            return Optional.of(run);
        }
    }

    private record FakeSessionRepository(Session session)
            implements com.icbc.testagent.domain.session.SessionRepository {
        @Override
        public Session save(Session session) {
            return session;
        }

        @Override
        public Optional<Session> findById(SessionId sessionId) {
            return Optional.of(session);
        }

        @Override
        public PageResponse<Session> findPage(String query, PageRequest pageRequest) {
            return new PageResponse<>(List.of(session), pageRequest.page(), pageRequest.size(), 1);
        }

        @Override
        public PageResponse<Session> findByWorkspaceId(WorkspaceId workspaceId, PageRequest pageRequest) {
            return new PageResponse<>(List.of(session), pageRequest.page(), pageRequest.size(), 1);
        }

        @Override
        public Optional<Session> attachOpencodeSession(
                SessionId sessionId,
                String opencodeSessionId,
                ExecutionNodeId executionNodeId,
                Instant updatedAt,
                String traceId) {
            return Optional.of(session.attachOpencodeSession(opencodeSessionId, executionNodeId, updatedAt, traceId));
        }
    }

    private static final class FakeExecutionNodeRepository implements ExecutionNodeRepository {
        @Override
        public ExecutionNode save(ExecutionNode executionNode) {
            return executionNode;
        }

        @Override
        public Optional<ExecutionNode> findById(ExecutionNodeId executionNodeId) {
            return Optional.of(node());
        }

        @Override
        public List<ExecutionNode> findRoutableNodes(int limit) {
            return List.of(node());
        }
    }

    private static AgentRuntimeRegistry runtimeRegistry(FakeOpencodeFacade facade) {
        return new AgentRuntimeRegistry(List.of(new OpencodeAgentRuntime(facade)));
    }

    private static final class FakeAgentSessionBindingRepository implements AgentSessionBindingRepository {
        private final Map<String, AgentSessionBinding> bindings = new LinkedHashMap<>();

        @Override
        public AgentSessionBinding save(AgentSessionBinding binding) {
            bindings.put(key(binding.sessionId(), binding.agentId()), binding);
            return binding;
        }

        @Override
        public Optional<AgentSessionBinding> findBySessionIdAndAgentId(SessionId sessionId, String agentId) {
            return Optional.ofNullable(bindings.get(key(sessionId, agentId)));
        }

        @Override
        public Optional<AgentSessionBinding> findByAgentIdAndRemoteSessionId(String agentId, String remoteSessionId) {
            return bindings.values().stream()
                    .filter(binding -> binding.agentId().equals(agentId.trim().toLowerCase(Locale.ROOT)))
                    .filter(binding -> binding.remoteSessionId().equals(remoteSessionId))
                    .findFirst();
        }

        private String key(SessionId sessionId, String agentId) {
            return sessionId.value() + ":" + agentId.trim().toLowerCase(Locale.ROOT);
        }
    }

    private record FakeRunSessionScopeRepository(List<RunSessionScopeSession> sessions)
            implements RunSessionScopeRepository {
        @Override
        public void upsertScope(RunSessionScope scope) {
        }

        @Override
        public void upsertSession(RunSessionScopeSession session) {
        }

        @Override
        public List<RunSessionScopeSession> findSessionsByRunId(RunId runId) {
            return sessions;
        }

        @Override
        public Optional<RunSessionScopeSession> findSession(RunId runId, String sessionId) {
            return sessions.stream()
                    .filter(session -> session.sessionId().equals(sessionId))
                    .findFirst();
        }

        @Override
        public List<RunSessionScopeSession> findSessionsByRootSessionId(String rootSessionId) {
            return sessions.stream()
                    .filter(session -> session.rootSessionId().equals(rootSessionId))
                    .toList();
        }
    }

    private static final class FakeOpencodeFacade implements OpencodeClientFacade {
        private OpencodeSessionMessagesResult result = new OpencodeSessionMessagesResult(List.of(), null, null);
        private final Map<String, OpencodeSessionMessagesResult> resultsBySession = new LinkedHashMap<>();
        private RuntimeException error;
        private OpencodeSessionMessagesCommand lastCommand;
        private final List<String> requestedSessionIds = new java.util.ArrayList<>();

        @Override
        public Mono<OpencodeHealthResult> health(OpencodeHealthCommand command) {
            return Mono.just(new OpencodeHealthResult(true, command.node().baseUrl()));
        }

        @Override
        public Mono<OpencodeCreateSessionResult> createSession(OpencodeCreateSessionCommand command) {
            return Mono.just(new OpencodeCreateSessionResult(REMOTE_SESSION_ID));
        }

        @Override
        public Mono<Boolean> sessionExists(OpencodeSessionExistsCommand command) {
            return Mono.just(true);
        }

        @Override
        public Mono<OpencodeCancelResult> cancelSession(OpencodeCancelCommand command) {
            return Mono.just(new OpencodeCancelResult(true));
        }

        @Override
        public Mono<OpencodeStartRunResult> startRun(OpencodeStartRunCommand command) {
            return Mono.just(new OpencodeStartRunResult(true));
        }

        @Override
        public Mono<OpencodeStartRunResult> startCommand(OpencodeStartCommand command) {
            return Mono.just(new OpencodeStartRunResult(true));
        }

        @Override
        public Flux<RunEventDraft> streamRunEvents(OpencodeStreamEventsCommand command) {
            return Flux.empty();
        }

        @Override
        public Mono<OpencodeDiffResult> getDiff(OpencodeDiffCommand command) {
            return Mono.just(new OpencodeDiffResult(List.of()));
        }

        @Override
        public Mono<OpencodeRejectDiffResult> rejectDiff(OpencodeRejectDiffCommand command) {
            return Mono.just(new OpencodeRejectDiffResult(true));
        }

        @Override
        public Mono<OpencodeRuntimeResult> runtime(OpencodeRuntimeCommand command) {
            return Mono.just(new OpencodeRuntimeResult(JsonNodeFactory.instance.objectNode()));
        }

        @Override
        public Mono<OpencodeSessionMessagesResult> sessionMessages(OpencodeSessionMessagesCommand command) {
            lastCommand = command;
            requestedSessionIds.add(command.opencodeSessionId());
            if (error != null) {
                return Mono.error(error);
            }
            return Mono.just(resultsBySession.getOrDefault(command.opencodeSessionId(), result));
        }
    }
}
