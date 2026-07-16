package com.icbc.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.agent.runtime.AgentRuntime;
import com.icbc.testagent.agent.runtime.AgentRuntimeRegistry;
import com.icbc.testagent.agent.runtime.AgentSessionMessage;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesCommand;
import com.icbc.testagent.agent.runtime.AgentSessionMessagesResult;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.agent.AgentSessionBindingRepository;
import com.icbc.testagent.domain.event.RunSessionScope;
import com.icbc.testagent.domain.event.RunSessionScopeRepository;
import com.icbc.testagent.domain.event.RunSessionScopeSession;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.Test;

class RunSessionMessageSnapshotServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-06T00:00:00Z");
    private static final SessionId SESSION_ID = new SessionId("ses_1234567890abcdef");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_1234567890abcdef");
    private static final ExecutionNodeId NODE_ID = new ExecutionNodeId("node_1234567890abcdef");
    private static final RunId PREVIOUS_RUN_ID = new RunId("run_previous1234567890abcdef");
    private static final RunId CURRENT_RUN_ID = new RunId("run_current1234567890abcdef");

    @Test
    void refreshSessionSnapshotUsesSyntheticRemoteIdWhenProjectedMessageHasNoId() {
        FakeMessageRepository messages = new FakeMessageRepository();
        RunSessionMessageSnapshotService service = new RunSessionMessageSnapshotService(
                new NoopRunRepository(),
                new NoopSessionRepository(),
                messages,
                new SingleNodeRepository(),
                new AgentRuntimeRegistry(List.of(new MissingIdRuntime())),
                new SingleBindingRepository(),
                new ObjectMapper());

        boolean first = service.refreshSessionSnapshot("opencode", session(), "trace_1234567890abcdef");
        boolean second = service.refreshSessionSnapshot("opencode", session(), "trace_1234567890abcdef");

        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(messages.saved).singleElement().satisfies(message -> {
            assertThat(message.content()).isEqualTo("assistant text");
            assertThat(message.remoteMessageId()).startsWith("synthetic:");
        });
    }

    @Test
    void refreshSessionSnapshotPreservesExistingRunOwnershipAndLeavesNewHistoryUnassigned() {
        String previousUserId = "msg_previous_user";
        String currentUserId = "msg_current_user";
        String previousAssistantId = "msg_previous_assistant";
        String currentAssistantId = "msg_current_assistant";
        FakeMessageRepository messages = new FakeMessageRepository();
        messages.save(message(
                "msg_platform_previous_assistant",
                SessionMessageRole.ASSISTANT,
                PREVIOUS_RUN_ID,
                previousAssistantId,
                "旧轮回答"));
        RunSessionMessageSnapshotService service = new RunSessionMessageSnapshotService(
                new NoopRunRepository(),
                new NoopSessionRepository(),
                messages,
                new SingleNodeRepository(),
                new AgentRuntimeRegistry(List.of(new TwoTurnRuntime(
                        previousUserId,
                        previousAssistantId,
                        currentUserId,
                        currentAssistantId))),
                new SingleBindingRepository(),
                new ObjectMapper());

        boolean refreshed = service.refreshSessionSnapshot("opencode", session(), "trace_1234567890abcdef");

        assertThat(refreshed).isTrue();
        assertThat(messages.findBySessionIdAndRemoteMessageId(SESSION_ID, previousAssistantId))
                .get()
                .extracting(SessionMessage::runId)
                .isEqualTo(PREVIOUS_RUN_ID);
        assertThat(messages.findBySessionIdAndRemoteMessageId(SESSION_ID, currentAssistantId))
                .get()
                .extracting(SessionMessage::runId)
                .isNull();
    }

    @Test
    void persistRunSnapshotOnlyAssignsCurrentTurnAssistantsToCurrentRun() {
        String previousUserId = "msg_previous_user";
        String currentUserId = "msg_current_user";
        String previousAssistantId = "msg_previous_assistant";
        String currentAssistantId = "msg_current_assistant";
        FakeMessageRepository messages = new FakeMessageRepository();
        messages.save(message(
                "msg_platform_previous_user",
                SessionMessageRole.USER,
                PREVIOUS_RUN_ID,
                previousUserId,
                "生成一条待办，然后执行"));
        messages.save(message(
                "msg_platform_previous_assistant",
                SessionMessageRole.ASSISTANT,
                PREVIOUS_RUN_ID,
                previousAssistantId,
                "待办已完成"));
        messages.save(message(
                "msg_platform_current_user",
                SessionMessageRole.USER,
                CURRENT_RUN_ID,
                currentUserId,
                "1+1=？"));
        RunSessionMessageSnapshotService service = new RunSessionMessageSnapshotService(
                new NoopRunRepository(),
                new NoopSessionRepository(),
                messages,
                new SingleNodeRepository(),
                new AgentRuntimeRegistry(List.of(new TwoTurnRuntime(
                        previousUserId,
                        previousAssistantId,
                        currentUserId,
                        currentAssistantId))),
                new SingleBindingRepository(),
                new ObjectMapper());

        boolean refreshed = service.persistRunSnapshot(
                "opencode",
                run(CURRENT_RUN_ID),
                "trace_1234567890abcdef");

        assertThat(refreshed).isTrue();
        assertThat(messages.findBySessionIdAndRemoteMessageId(SESSION_ID, previousAssistantId))
                .get()
                .extracting(SessionMessage::runId)
                .isEqualTo(PREVIOUS_RUN_ID);
        assertThat(messages.findBySessionIdAndRemoteMessageId(SESSION_ID, currentAssistantId))
                .get()
                .extracting(SessionMessage::runId)
                .isEqualTo(CURRENT_RUN_ID);
    }

    @Test
    void persistRunSnapshotFindsDispatchUserAcrossOlderMessagePages() {
        String currentUserId = "msg_current_user";
        String currentAssistantId = "msg_current_assistant";
        FakeMessageRepository messages = new FakeMessageRepository();
        messages.save(message(
                "msg_platform_current_user",
                SessionMessageRole.USER,
                CURRENT_RUN_ID,
                currentUserId,
                "1+1=？"));
        PagedRuntime runtime = new PagedRuntime();
        runtime.pages.put("<latest>", new AgentSessionMessagesResult(
                List.of(assistant(currentAssistantId, currentUserId, NOW.plusSeconds(2))),
                null,
                "cursor_older"));
        runtime.pages.put("cursor_older", new AgentSessionMessagesResult(
                List.of(
                        user("msg_previous_user", NOW.minusSeconds(20)),
                        assistant("msg_previous_assistant", "msg_previous_user", NOW.minusSeconds(19)),
                        user(currentUserId, NOW.plusSeconds(1))),
                null,
                null));
        RunSessionMessageSnapshotService service = service(messages, runtime);

        boolean refreshed = service.persistRunSnapshot(
                "opencode",
                run(CURRENT_RUN_ID),
                "trace_1234567890abcdef");

        assertThat(refreshed).isTrue();
        assertThat(runtime.commands).extracting(AgentSessionMessagesCommand::cursor)
                .containsExactly(null, "cursor_older");
        assertThat(runtime.commands).allSatisfy(command -> assertThat(command.limit()).isEqualTo(100));
        assertThat(messages.findBySessionIdAndRemoteMessageId(SESSION_ID, currentAssistantId))
                .get()
                .extracting(SessionMessage::runId)
                .isEqualTo(CURRENT_RUN_ID);
        assertThat(messages.findBySessionIdAndRemoteMessageId(SESSION_ID, "msg_previous_assistant"))
                .isEmpty();
    }

    @Test
    void legacyRunWithoutDispatchUsesUniqueUserInsideRunTimeWindow() {
        String currentUserId = "msg_current_user";
        String currentAssistantId = "msg_current_assistant";
        FakeMessageRepository messages = new FakeMessageRepository();
        PagedRuntime runtime = new PagedRuntime();
        runtime.pages.put("<latest>", new AgentSessionMessagesResult(List.of(
                user("msg_previous_user", NOW.minusSeconds(20)),
                assistant("msg_previous_assistant", "msg_previous_user", NOW.minusSeconds(19)),
                user(currentUserId, NOW.plusSeconds(1)),
                assistant(currentAssistantId, currentUserId, NOW.plusSeconds(2)))));
        RunSessionMessageSnapshotService service = service(messages, runtime);

        boolean refreshed = service.persistRunSnapshot(
                "opencode",
                run(CURRENT_RUN_ID),
                "trace_1234567890abcdef");

        assertThat(refreshed).isTrue();
        assertThat(messages.findBySessionIdAndRemoteMessageId(SESSION_ID, currentAssistantId))
                .get()
                .extracting(SessionMessage::runId)
                .isEqualTo(CURRENT_RUN_ID);
        assertThat(messages.findBySessionIdAndRemoteMessageId(SESSION_ID, "msg_previous_assistant"))
                .isEmpty();
    }

    @Test
    void runSnapshotFailsClosedAfterTwentyPagesWithoutOwnershipEvidence() {
        FakeMessageRepository messages = new FakeMessageRepository();
        messages.save(message(
                "msg_platform_current_user",
                SessionMessageRole.USER,
                CURRENT_RUN_ID,
                "msg_dispatch_missing",
                "1+1=？"));
        PagedRuntime runtime = new PagedRuntime();
        for (int page = 0; page < 20; page++) {
            String cursor = page == 0 ? "<latest>" : "cursor_" + page;
            String nextCursor = "cursor_" + (page + 1);
            runtime.pages.put(cursor, new AgentSessionMessagesResult(
                    List.of(assistant(
                            "msg_unowned_assistant_" + page,
                            "msg_another_user",
                            NOW.minusSeconds(page + 10L))),
                    null,
                    nextCursor));
        }
        RunSessionMessageSnapshotService service = service(messages, runtime);

        boolean refreshed = service.persistRunSnapshot(
                "opencode",
                run(CURRENT_RUN_ID),
                "trace_1234567890abcdef");

        assertThat(refreshed).isTrue();
        assertThat(runtime.commands).hasSize(20);
        assertThat(messages.saved).noneMatch(message -> message.role() == SessionMessageRole.ASSISTANT);
    }

    @Test
    void runSnapshotFailsClosedWhenPlatformAndScopeDispatchAnchorsConflict() {
        String platformDispatchId = "msg_platform_dispatch";
        FakeMessageRepository messages = new FakeMessageRepository();
        messages.save(message(
                "msg_platform_current_user",
                SessionMessageRole.USER,
                CURRENT_RUN_ID,
                platformDispatchId,
                "1+1=？"));
        PagedRuntime runtime = new PagedRuntime();
        runtime.pages.put("<latest>", new AgentSessionMessagesResult(List.of(
                user(platformDispatchId, NOW.plusSeconds(1)),
                assistant("msg_current_assistant", platformDispatchId, NOW.plusSeconds(2)))));
        RunSessionMessageSnapshotService service = new RunSessionMessageSnapshotService(
                new NoopRunRepository(),
                new NoopSessionRepository(),
                messages,
                new SingleNodeRepository(),
                new AgentRuntimeRegistry(List.of(runtime)),
                new SingleBindingRepository(),
                new SingleScopeRepository("msg_scope_dispatch"),
                new ObjectMapper());

        boolean refreshed = service.persistRunSnapshot(
                "opencode",
                run(CURRENT_RUN_ID),
                "trace_1234567890abcdef");

        assertThat(refreshed).isTrue();
        assertThat(runtime.commands).isEmpty();
        assertThat(messages.saved).noneMatch(message -> message.role() == SessionMessageRole.ASSISTANT);
    }

    private static RunSessionMessageSnapshotService service(
            FakeMessageRepository messages,
            AgentRuntime runtime) {
        return new RunSessionMessageSnapshotService(
                new NoopRunRepository(),
                new NoopSessionRepository(),
                messages,
                new SingleNodeRepository(),
                new AgentRuntimeRegistry(List.of(runtime)),
                new SingleBindingRepository(),
                new ObjectMapper());
    }

    private static AgentSessionMessage user(String id, Instant createdAt) {
        return new AgentSessionMessage(
                Map.of(
                        "id", id,
                        "role", "user",
                        "time", Map.of("created", createdAt.toEpochMilli())),
                List.of());
    }

    private static AgentSessionMessage assistant(String id, String parentId, Instant createdAt) {
        return new AgentSessionMessage(
                Map.of(
                        "id", id,
                        "parentID", parentId,
                        "role", "assistant",
                        "time", Map.of("created", createdAt.toEpochMilli())),
                List.of(Map.of(
                        "id", "part_" + id,
                        "messageID", id,
                        "type", "text",
                        "text", id)));
    }

    private static Session session() {
        return new Session(
                SESSION_ID,
                WORKSPACE_ID,
                "Demo session",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static Run run(RunId runId) {
        return new Run(
                runId,
                SESSION_ID,
                WORKSPACE_ID,
                RunStatus.SUCCEEDED,
                NOW,
                NOW.plusSeconds(30),
                "trace_1234567890abcdef");
    }

    private static SessionMessage message(
            String messageId,
            SessionMessageRole role,
            RunId runId,
            String remoteMessageId,
            String content) {
        return new SessionMessage(
                new SessionMessageId(messageId),
                SESSION_ID,
                role,
                content,
                NOW,
                "trace_1234567890abcdef",
                runId,
                role == SessionMessageRole.ASSISTANT ? "opencode" : null,
                remoteMessageId,
                null,
                null,
                null,
                NOW);
    }

    private static final class MissingIdRuntime implements AgentRuntime {

        @Override
        public String agentId() {
            return "opencode";
        }

        @Override
        public Mono<AgentSessionMessagesResult> sessionMessages(AgentSessionMessagesCommand command) {
            AgentSessionMessage message = new AgentSessionMessage(
                    Map.of(
                            "role", "assistant",
                            "time", Map.of("created", NOW.getEpochSecond())),
                    List.of(Map.of(
                            "id", "part_text",
                            "type", "text",
                            "text", "assistant text")));
            return Mono.just(new AgentSessionMessagesResult(List.of(message)));
        }
    }

    private record TwoTurnRuntime(
            String previousUserId,
            String previousAssistantId,
            String currentUserId,
            String currentAssistantId) implements AgentRuntime {

        @Override
        public String agentId() {
            return "opencode";
        }

        @Override
        public Mono<AgentSessionMessagesResult> sessionMessages(AgentSessionMessagesCommand command) {
            return Mono.just(new AgentSessionMessagesResult(List.of(
                    new AgentSessionMessage(
                            Map.of("id", previousUserId, "role", "user"),
                            List.of(Map.of("id", "part_previous_user", "type", "text", "text", "旧问题"))),
                    new AgentSessionMessage(
                            Map.of(
                                    "id", previousAssistantId,
                                    "parentID", previousUserId,
                                    "role", "assistant"),
                            List.of(Map.of(
                                    "id", "part_previous_todo",
                                    "messageID", previousAssistantId,
                                    "type", "tool",
                                    "tool", "todowrite"))),
                    new AgentSessionMessage(
                            Map.of("id", currentUserId, "role", "user"),
                            List.of(Map.of("id", "part_current_user", "type", "text", "text", "1+1=？"))),
                    new AgentSessionMessage(
                            Map.of(
                                    "id", currentAssistantId,
                                    "parentID", currentUserId,
                                    "role", "assistant"),
                            List.of(Map.of(
                                    "id", "part_current_answer",
                                    "messageID", currentAssistantId,
                                    "type", "text",
                                    "text", "2"))))));
        }
    }

    private static final class PagedRuntime implements AgentRuntime {
        private final Map<String, AgentSessionMessagesResult> pages = new LinkedHashMap<>();
        private final List<AgentSessionMessagesCommand> commands = new ArrayList<>();

        @Override
        public String agentId() {
            return "opencode";
        }

        @Override
        public Mono<AgentSessionMessagesResult> sessionMessages(AgentSessionMessagesCommand command) {
            commands.add(command);
            String cursor = command.cursor() == null ? "<latest>" : command.cursor();
            return Mono.just(pages.getOrDefault(cursor, new AgentSessionMessagesResult(List.of())));
        }
    }

    private static final class SingleBindingRepository implements AgentSessionBindingRepository {

        @Override
        public AgentSessionBinding save(AgentSessionBinding binding) {
            return binding;
        }

        @Override
        public Optional<AgentSessionBinding> findBySessionIdAndAgentId(SessionId sessionId, String agentId) {
            return Optional.of(new AgentSessionBinding(
                    sessionId,
                    agentId,
                    "remote_session_1",
                    NODE_ID,
                    NOW,
                    NOW,
                    "trace_1234567890abcdef"));
        }

        @Override
        public Optional<AgentSessionBinding> findByAgentIdAndRemoteSessionId(String agentId, String remoteSessionId) {
            return Optional.empty();
        }
    }

    private record SingleScopeRepository(String dispatchMessageId) implements RunSessionScopeRepository {

        @Override
        public void upsertScope(RunSessionScope scope) {
        }

        @Override
        public void upsertSession(RunSessionScopeSession session) {
        }

        @Override
        public List<RunSessionScopeSession> findSessionsByRunId(RunId runId) {
            return List.of(rootScopeSession(runId, dispatchMessageId));
        }

        @Override
        public List<RunSessionScopeSession> findSessionsByRootSessionId(String rootSessionId) {
            return List.of();
        }

        @Override
        public Optional<RunSessionScopeSession> findSession(RunId runId, String sessionId) {
            return Optional.of(rootScopeSession(runId, dispatchMessageId));
        }

        private static RunSessionScopeSession rootScopeSession(RunId runId, String dispatchMessageId) {
            return new RunSessionScopeSession(
                    runId,
                    "remote_session_1",
                    "remote_session_1",
                    null,
                    false,
                    "ROOT",
                    null,
                    null,
                    null,
                    "trace_1234567890abcdef",
                    NOW,
                    NOW,
                    Map.of("dispatchMessageId", dispatchMessageId));
        }
    }

    private static final class SingleNodeRepository implements ExecutionNodeRepository {

        @Override
        public ExecutionNode save(ExecutionNode executionNode) {
            return executionNode;
        }

        @Override
        public Optional<ExecutionNode> findById(ExecutionNodeId executionNodeId) {
            return Optional.of(new ExecutionNode(NODE_ID, "http://127.0.0.1:4096", ExecutionNodeStatus.READY, 0, 1, NOW));
        }

        @Override
        public List<ExecutionNode> findRoutableNodes(int limit) {
            return List.of();
        }
    }

    private static final class FakeMessageRepository implements SessionMessageRepository {
        private final List<SessionMessage> saved = new ArrayList<>();

        @Override
        public SessionMessage save(SessionMessage message) {
            saved.removeIf(existing -> existing.messageId().equals(message.messageId()));
            saved.add(message);
            return message;
        }

        @Override
        public Optional<SessionMessage> findById(SessionMessageId messageId) {
            return saved.stream()
                    .filter(message -> message.messageId().equals(messageId))
                    .findFirst();
        }

        @Override
        public Optional<SessionMessage> findBySessionIdAndRemoteMessageId(SessionId sessionId, String remoteMessageId) {
            if (remoteMessageId == null || remoteMessageId.isBlank()) {
                return Optional.empty();
            }
            return saved.stream()
                    .filter(message -> message.sessionId().equals(sessionId))
                    .filter(message -> remoteMessageId.equals(message.remoteMessageId()))
                    .findFirst();
        }

        @Override
        public PageResponse<SessionMessage> findBySessionId(SessionId sessionId, PageRequest pageRequest) {
            List<SessionMessage> items = saved.stream()
                    .filter(message -> message.sessionId().equals(sessionId))
                    .toList();
            return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), items.size());
        }
    }

    private static final class NoopRunRepository implements RunRepository {

        @Override
        public Run save(Run run) {
            return run;
        }

        @Override
        public Optional<Run> findById(RunId runId) {
            return Optional.empty();
        }
    }

    private static final class NoopSessionRepository implements com.icbc.testagent.domain.session.SessionRepository {

        @Override
        public Session save(Session session) {
            return session;
        }

        @Override
        public Optional<Session> findById(SessionId sessionId) {
            return Optional.of(session());
        }

        @Override
        public PageResponse<Session> findPage(String query, PageRequest pageRequest) {
            return new PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
        }

        @Override
        public PageResponse<Session> findByWorkspaceId(WorkspaceId workspaceId, PageRequest pageRequest) {
            return new PageResponse<>(List.of(), pageRequest.page(), pageRequest.size(), 0);
        }

        @Override
        public Optional<Session> attachOpencodeSession(
                SessionId sessionId,
                String opencodeSessionId,
                ExecutionNodeId executionNodeId,
                Instant updatedAt,
                String traceId) {
            return Optional.empty();
        }
    }
}
