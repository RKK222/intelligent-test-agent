package com.example.testagent.opencode.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.testagent.common.pagination.PageRequest;
import com.example.testagent.common.pagination.PageResponse;
import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.node.ExecutionNodeRepository;
import com.example.testagent.domain.node.ExecutionNodeStatus;
import com.example.testagent.domain.run.Run;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.run.RunRepository;
import com.example.testagent.domain.run.RunStatus;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.session.SessionStatus;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.example.testagent.event.RunEventSsePayload;
import com.example.testagent.opencode.client.OpencodeCancelCommand;
import com.example.testagent.opencode.client.OpencodeCancelResult;
import com.example.testagent.opencode.client.OpencodeClientFacade;
import com.example.testagent.opencode.client.OpencodeCreateSessionCommand;
import com.example.testagent.opencode.client.OpencodeCreateSessionResult;
import com.example.testagent.opencode.client.OpencodeDiffCommand;
import com.example.testagent.opencode.client.OpencodeDiffResult;
import com.example.testagent.opencode.client.OpencodeHealthCommand;
import com.example.testagent.opencode.client.OpencodeHealthResult;
import com.example.testagent.opencode.client.OpencodeRejectDiffCommand;
import com.example.testagent.opencode.client.OpencodeRejectDiffResult;
import com.example.testagent.opencode.client.OpencodeRuntimeCommand;
import com.example.testagent.opencode.client.OpencodeRuntimeResult;
import com.example.testagent.opencode.client.OpencodeSessionMessage;
import com.example.testagent.opencode.client.OpencodeSessionMessagesCommand;
import com.example.testagent.opencode.client.OpencodeSessionMessagesResult;
import com.example.testagent.opencode.client.OpencodeStartRunCommand;
import com.example.testagent.opencode.client.OpencodeStartRunResult;
import com.example.testagent.opencode.client.OpencodeStreamEventsCommand;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
                facade);

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
    void recoverySkipsWhenSessionHasNoOpencodeMapping() {
        FakeOpencodeFacade facade = new FakeOpencodeFacade();
        RunMessageRecoveryService service = new RunMessageRecoveryService(
                new FakeRunRepository(run()),
                new FakeSessionRepository(session()),
                new FakeExecutionNodeRepository(),
                facade);

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
                facade);

        List<RunEventSsePayload> payloads = service.recover(RUN_ID, "trace_1234567890abcdef")
                .collectList()
                .block(Duration.ofSeconds(2));

        assertThat(payloads).isEmpty();
        assertThat(facade.lastCommand).isNotNull();
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
            implements com.example.testagent.domain.session.SessionRepository {
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

    private static final class FakeOpencodeFacade implements OpencodeClientFacade {
        private OpencodeSessionMessagesResult result = new OpencodeSessionMessagesResult(List.of(), null, null);
        private RuntimeException error;
        private OpencodeSessionMessagesCommand lastCommand;

        @Override
        public Mono<OpencodeHealthResult> health(OpencodeHealthCommand command) {
            return Mono.just(new OpencodeHealthResult(true, command.node().baseUrl()));
        }

        @Override
        public Mono<OpencodeCreateSessionResult> createSession(OpencodeCreateSessionCommand command) {
            return Mono.just(new OpencodeCreateSessionResult(REMOTE_SESSION_ID));
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
            if (error != null) {
                return Mono.error(error);
            }
            return Mono.just(result);
        }
    }
}
