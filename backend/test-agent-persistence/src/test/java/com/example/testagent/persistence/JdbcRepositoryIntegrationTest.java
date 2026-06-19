package com.example.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testagent.domain.event.RunEvent;
import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.event.RunEventType;
import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.node.ExecutionNodeId;
import com.example.testagent.domain.node.ExecutionNodeStatus;
import com.example.testagent.domain.routing.RoutingDecision;
import com.example.testagent.domain.routing.RoutingReason;
import com.example.testagent.domain.run.Run;
import com.example.testagent.domain.run.RunId;
import com.example.testagent.domain.run.RunStatus;
import com.example.testagent.domain.session.Session;
import com.example.testagent.domain.session.SessionId;
import com.example.testagent.domain.session.SessionMessage;
import com.example.testagent.domain.session.SessionMessageId;
import com.example.testagent.domain.session.SessionMessageRole;
import com.example.testagent.domain.session.SessionStatus;
import com.example.testagent.domain.workspace.Workspace;
import com.example.testagent.domain.workspace.WorkspaceId;
import com.example.testagent.domain.workspace.WorkspaceStatus;
import com.example.testagent.common.pagination.PageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-06-19T00:00:00Z");
    private EmbeddedDatabase database;
    private JdbcWorkspaceRepository workspaces;
    private JdbcSessionRepository sessions;
    private JdbcRunRepository runs;
    private JdbcRunEventRepository runEvents;
    private JdbcExecutionNodeRepository executionNodes;
    private JdbcRoutingDecisionRepository routingDecisions;
    private JdbcSessionMessageRepository sessionMessages;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testagent;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
                .build();
        Flyway.configure().dataSource(database).locations("classpath:db/migration").load().migrate();

        JdbcClient jdbcClient = JdbcClient.create(database);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        workspaces = new JdbcWorkspaceRepository(jdbcClient);
        sessions = new JdbcSessionRepository(jdbcClient);
        runs = new JdbcRunRepository(jdbcClient);
        runEvents = new JdbcRunEventRepository(jdbcClient, objectMapper);
        executionNodes = new JdbcExecutionNodeRepository(jdbcClient, objectMapper);
        routingDecisions = new JdbcRoutingDecisionRepository(jdbcClient);
        sessionMessages = new JdbcSessionMessageRepository(jdbcClient);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void repositoriesSaveAndReadCoreAggregates() {
        Workspace workspace = workspace();
        Session session = session();
        Run run = run();
        ExecutionNode node = executionNode();
        RoutingDecision decision = routingDecision();

        workspaces.save(workspace);
        sessions.save(session);
        runs.save(run);
        executionNodes.save(node);
        routingDecisions.save(decision);

        assertThat(workspaces.findById(workspace.workspaceId())).contains(workspace);
        assertThat(sessions.findById(session.sessionId())).contains(session);
        assertThat(runs.findById(run.runId())).contains(run);
        assertThat(executionNodes.findById(node.executionNodeId())).contains(node);
        assertThat(routingDecisions.findByRunId(run.runId())).contains(decision);
    }

    @Test
    void runEventsAppendWithMonotonicSeqAndReplayAfterLastSeq() {
        workspaces.save(workspace());
        sessions.save(session());
        runs.save(run());

        RunEvent first = runEvents.append(new RunEventDraft(
                new RunId("run_1234567890abcdef"),
                RunEventType.RUN_STARTED,
                "trace_1234567890abcdef",
                NOW,
                Map.of("status", "running")));
        RunEvent second = runEvents.append(new RunEventDraft(
                new RunId("run_1234567890abcdef"),
                RunEventType.ASSISTANT_MESSAGE_DELTA,
                "trace_1234567890abcdef",
                NOW.plusSeconds(1),
                Map.of("text", "hello")));

        assertThat(first.seq()).isEqualTo(1L);
        assertThat(second.seq()).isEqualTo(2L);
        assertThat(runEvents.findByRunIdAfter(new RunId("run_1234567890abcdef"), 1L, 50))
                .containsExactly(second);
    }

    @Test
    void runEventsEnforceUniqueRunSequence() {
        workspaces.save(workspace());
        sessions.save(session());
        runs.save(run());
        RunEvent event = runEvents.append(new RunEventDraft(
                new RunId("run_1234567890abcdef"),
                RunEventType.RUN_STARTED,
                "trace_1234567890abcdef",
                NOW,
                Map.of()));

        JdbcClient jdbcClient = JdbcClient.create((DataSource) database);
        assertThatThrownBy(() -> jdbcClient.sql("""
                        insert into run_events(event_id, run_id, seq, type, trace_id, occurred_at, payload_json)
                        values (:eventId, :runId, :seq, :type, :traceId, :occurredAt, :payloadJson)
                        """)
                .param("eventId", "evt_duplicate")
                .param("runId", event.runId().value())
                .param("seq", event.seq())
                .param("type", event.type().wireName())
                .param("traceId", event.traceId())
                .param("occurredAt", event.occurredAt())
                .param("payloadJson", "{}")
                .update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repositoriesPageWorkspacesSessionsAndSessionMessages() {
        Workspace workspace = workspace();
        Session session = session();
        workspaces.save(workspace);
        sessions.save(session);
        sessionMessages.save(sessionMessage("msg_1234567890abcdef", "first"));
        sessionMessages.save(sessionMessage("msg_2234567890abcdef", "second"));

        assertThat(workspaces.findPage(new PageRequest(1, 10)).items())
                .extracting(Workspace::workspaceId)
                .containsExactly(workspace.workspaceId());
        assertThat(sessions.findByWorkspaceId(workspace.workspaceId(), new PageRequest(1, 10)).items())
                .extracting(Session::sessionId)
                .containsExactly(session.sessionId());
        assertThat(sessionMessages.findBySessionId(session.sessionId(), new PageRequest(1, 10)).items())
                .extracting(SessionMessage::content)
                .containsExactly("first", "second");
    }

    private static Workspace workspace() {
        return new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "Demo",
                "/tmp/demo",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static Session session() {
        return new Session(
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                "Initial session",
                SessionStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static SessionMessage sessionMessage(String messageId, String content) {
        return new SessionMessage(
                new SessionMessageId(messageId),
                new SessionId("ses_1234567890abcdef"),
                SessionMessageRole.USER,
                content,
                NOW,
                "trace_1234567890abcdef");
    }

    private static Run run() {
        return new Run(
                new RunId("run_1234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                RunStatus.PENDING,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static ExecutionNode executionNode() {
        return new ExecutionNode(
                new ExecutionNodeId("node_1234567890abcdef"),
                "http://127.0.0.1:4096",
                ExecutionNodeStatus.READY,
                0,
                4,
                100,
                NOW,
                Set.of("chat", "diff"),
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static RoutingDecision routingDecision() {
        return new RoutingDecision(
                new RunId("run_1234567890abcdef"),
                new ExecutionNodeId("node_1234567890abcdef"),
                RoutingReason.LOWEST_LOAD,
                NOW,
                "trace_1234567890abcdef");
    }
}
