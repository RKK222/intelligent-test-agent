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
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeRepository;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunRepository;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRepository;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.ArrayList;
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
            return Optional.empty();
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
