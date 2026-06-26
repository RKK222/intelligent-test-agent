package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.agent.AgentSessionBinding;
import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ApplicationMember;
import com.icbc.testagent.domain.configuration.ApplicationWorkspace;
import com.icbc.testagent.domain.configuration.ApplicationWorkspaceId;
import com.icbc.testagent.domain.configuration.CodeRepository;
import com.icbc.testagent.domain.configuration.CodeRepositoryId;
import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.configuration.SshKeyId;
import com.icbc.testagent.domain.configuration.UserSshKey;
import com.icbc.testagent.domain.configuration.WorkspaceCreateOperation;
import com.icbc.testagent.domain.configuration.WorkspaceCreateOperationStatus;
import com.icbc.testagent.domain.configuration.WorkspaceCreateOperationStep;
import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplica;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionReplicaId;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.icbc.testagent.domain.managedworkspace.WorkspaceReplicaSyncStatus;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspace;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.icbc.testagent.domain.managedworkspace.UserWorkspacePreference;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncDirection;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncRecord;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncRecordId;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServer;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerStatus;
import com.icbc.testagent.domain.opencodeprocess.ManagerConnectionStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainer;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerManager;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeManagerBackendConnection;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBindingStatus;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.routing.RoutingDecision;
import com.icbc.testagent.domain.routing.RoutingReason;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStatus;
import com.icbc.testagent.domain.run.TokenUsage;
import com.icbc.testagent.domain.scheduler.ScheduledTask;
import com.icbc.testagent.domain.scheduler.ScheduledTaskKey;
import com.icbc.testagent.domain.scheduler.ScheduledTaskPlan;
import com.icbc.testagent.domain.scheduler.ScheduledTaskPlanId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRegistrationStatus;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRun;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunFilter;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunId;
import com.icbc.testagent.domain.scheduler.ScheduledTaskRunStatus;
import com.icbc.testagent.domain.scheduler.ScheduledTaskTriggerType;
import com.icbc.testagent.domain.session.ConversationSourceType;
import com.icbc.testagent.domain.session.Session;
import com.icbc.testagent.domain.session.SessionId;
import com.icbc.testagent.domain.session.SessionMessage;
import com.icbc.testagent.domain.session.SessionMessageId;
import com.icbc.testagent.domain.session.SessionMessageRole;
import com.icbc.testagent.domain.session.SessionStatus;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.Workspace;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.domain.workspace.WorkspaceStatus;
import com.icbc.testagent.common.pagination.PageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    private JdbcAgentSessionBindingRepository agentSessionBindings;
    private JdbcConfigurationManagementRepository configurationManagement;
    private JdbcCommonParameterRepository commonParameters;
    private JdbcWorkspaceCreateOperationRepository workspaceCreateOperations;
    private JdbcManagedWorkspaceRepository managedWorkspaces;
    private JdbcOpencodeProcessManagementRepository opencodeProcesses;
    private JdbcScheduledTaskRepository scheduledTasks;
    private JdbcUserRepository users;
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testagent;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
                .build();
        Flyway.configure().dataSource(database).locations("classpath:db/migration").load().migrate();

        jdbcClient = JdbcClient.create(database);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        workspaces = new JdbcWorkspaceRepository(jdbcClient);
        sessions = new JdbcSessionRepository(jdbcClient);
        runs = new JdbcRunRepository(jdbcClient);
        runEvents = new JdbcRunEventRepository(jdbcClient, objectMapper);
        executionNodes = new JdbcExecutionNodeRepository(jdbcClient, objectMapper);
        routingDecisions = new JdbcRoutingDecisionRepository(jdbcClient);
        sessionMessages = new JdbcSessionMessageRepository(jdbcClient);
        agentSessionBindings = new JdbcAgentSessionBindingRepository(jdbcClient);
        configurationManagement = new JdbcConfigurationManagementRepository(jdbcClient);
        commonParameters = new JdbcCommonParameterRepository(jdbcClient);
        workspaceCreateOperations = new JdbcWorkspaceCreateOperationRepository(jdbcClient);
        managedWorkspaces = new JdbcManagedWorkspaceRepository(jdbcClient, objectMapper);
        opencodeProcesses = new JdbcOpencodeProcessManagementRepository(jdbcClient, objectMapper);
        scheduledTasks = new JdbcScheduledTaskRepository(jdbcClient, objectMapper);
        users = new JdbcUserRepository(jdbcClient);
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
    void sessionsPersistNullableOpencodeMappingAndAttachRemoteSession() {
        workspaces.save(workspace());
        sessions.save(session());

        Session existing = sessions.findById(new SessionId("ses_1234567890abcdef")).orElseThrow();
        assertThat(existing.opencodeSessionId()).isNull();
        assertThat(existing.opencodeExecutionNodeId()).isNull();

        executionNodes.save(executionNode());
        Session mapped = sessions.attachOpencodeSession(
                        new SessionId("ses_1234567890abcdef"),
                        "ses_remote1234567890abcdef",
                        new ExecutionNodeId("node_1234567890abcdef"),
                        NOW.plusSeconds(1),
                        "trace_attach1234567890")
                .orElseThrow();

        assertThat(mapped.opencodeSessionId()).isEqualTo("ses_remote1234567890abcdef");
        assertThat(mapped.opencodeExecutionNodeId()).isEqualTo(new ExecutionNodeId("node_1234567890abcdef"));
        assertThat(mapped.updatedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(sessions.findById(mapped.sessionId())).contains(mapped);
    }

    @Test
    void agentSessionBindingsUpsertAndQuery() {
        workspaces.save(workspace());
        sessions.save(session());
        executionNodes.save(executionNode());
        sessions.attachOpencodeSession(
                new SessionId("ses_1234567890abcdef"),
                "ses_remote1234567890abcdef",
                new ExecutionNodeId("node_1234567890abcdef"),
                NOW.plusSeconds(1),
                "trace_attach1234567890").orElseThrow();

        AgentSessionBinding saved = agentSessionBindings.save(new AgentSessionBinding(
                new SessionId("ses_1234567890abcdef"),
                "opencode",
                "ses_remote2234567890abcdef",
                new ExecutionNodeId("node_1234567890abcdef"),
                NOW,
                NOW.plusSeconds(2),
                "trace_bind1234567890"));

        assertThat(saved.remoteSessionId()).isEqualTo("ses_remote2234567890abcdef");
        assertThat(agentSessionBindings.findBySessionIdAndAgentId(new SessionId("ses_1234567890abcdef"), " OPENCODE "))
                .contains(saved);
        assertThat(agentSessionBindings.findByAgentIdAndRemoteSessionId(" OPENCODE ", "ses_remote2234567890abcdef"))
                .contains(saved);

        AgentSessionBinding updated = agentSessionBindings.save(saved.updateRemoteSession(
                "ses_remote3234567890abcdef",
                new ExecutionNodeId("node_1234567890abcdef"),
                NOW.plusSeconds(3),
                "trace_bind2234567890"));

        assertThat(agentSessionBindings.findBySessionIdAndAgentId(new SessionId("ses_1234567890abcdef"), "opencode"))
                .contains(updated);
        assertThat(agentSessionBindings.findByAgentIdAndRemoteSessionId("opencode", "ses_remote2234567890abcdef"))
                .isEmpty();

        sessions.save(session("ses_2234567890abcdef", "Second session", false, SessionStatus.ACTIVE, 3));
        assertThatThrownBy(() -> agentSessionBindings.save(new AgentSessionBinding(
                        new SessionId("ses_2234567890abcdef"),
                        "opencode",
                        "ses_remote3234567890abcdef",
                        new ExecutionNodeId("node_1234567890abcdef"),
                        NOW,
                        NOW.plusSeconds(4),
                        "trace_bind3234567890")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void agentSessionBindingsBackfillExistingOpencodeMappingDuringMigration() {
        EmbeddedDatabase migrationDatabase = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testagent_binding_migration;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
                .build();
        try {
            Flyway.configure()
                    .dataSource(migrationDatabase)
                    .locations("classpath:db/migration")
                    .target("5")
                    .load()
                    .migrate();
            JdbcClient jdbcClient = JdbcClient.create(migrationDatabase);
            jdbcClient.sql("""
                            insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                            values (:workspaceId, :name, :rootPath, :status, :traceId, :createdAt, :updatedAt)
                            """)
                    .param("workspaceId", "ws_1234567890abcdef")
                    .param("name", "Test Workspace")
                    .param("rootPath", "/tmp/workspace")
                    .param("status", WorkspaceStatus.ACTIVE.name())
                    .param("traceId", "trace_1234567890abcdef")
                    .param("createdAt", Timestamp.from(NOW))
                    .param("updatedAt", Timestamp.from(NOW))
                    .update();
            jdbcClient.sql("""
                            insert into execution_nodes(
                                execution_node_id, base_url, status, running_runs, max_runs, weight,
                                last_heartbeat_at, capabilities_json, trace_id, created_at, updated_at
                            )
                            values (
                                :nodeId, :baseUrl, :status, :runningRuns, :maxRuns, :weight,
                                :lastHeartbeatAt, :capabilitiesJson, :traceId, :createdAt, :updatedAt
                            )
                            """)
                    .param("nodeId", "node_1234567890abcdef")
                    .param("baseUrl", "http://127.0.0.1:4096")
                    .param("status", ExecutionNodeStatus.READY.name())
                    .param("runningRuns", 0)
                    .param("maxRuns", 4)
                    .param("weight", 1)
                    .param("lastHeartbeatAt", Timestamp.from(NOW))
                    .param("capabilitiesJson", "[]")
                    .param("traceId", "trace_1234567890abcdef")
                    .param("createdAt", Timestamp.from(NOW))
                    .param("updatedAt", Timestamp.from(NOW))
                    .update();
            jdbcClient.sql("""
                            insert into sessions(
                                session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                                opencode_session_id, opencode_execution_node_id, pinned
                            )
                            values (
                                :sessionId, :workspaceId, :title, :status, :traceId, :createdAt, :updatedAt,
                                :opencodeSessionId, :opencodeExecutionNodeId, :pinned
                            )
                            """)
                    .param("sessionId", "ses_1234567890abcdef")
                    .param("workspaceId", "ws_1234567890abcdef")
                    .param("title", "Session")
                    .param("status", SessionStatus.ACTIVE.name())
                    .param("traceId", "trace_session123456")
                    .param("createdAt", Timestamp.from(NOW))
                    .param("updatedAt", Timestamp.from(NOW.plusSeconds(3)))
                    .param("opencodeSessionId", "ses_remote1234567890abcdef")
                    .param("opencodeExecutionNodeId", "node_1234567890abcdef")
                    .param("pinned", false)
                    .update();

            Flyway.configure().dataSource(migrationDatabase).locations("classpath:db/migration").load().migrate();

            JdbcAgentSessionBindingRepository migratedBindings = new JdbcAgentSessionBindingRepository(jdbcClient);
            assertThat(migratedBindings.findBySessionIdAndAgentId(new SessionId("ses_1234567890abcdef"), "opencode"))
                    .contains(new AgentSessionBinding(
                            new SessionId("ses_1234567890abcdef"),
                            "opencode",
                            "ses_remote1234567890abcdef",
                            new ExecutionNodeId("node_1234567890abcdef"),
                            NOW,
                            NOW.plusSeconds(3),
                            "trace_session123456"));
        } finally {
            migrationDatabase.shutdown();
        }
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
    void runEventsAppendAllocatesUniqueSeqUnderConcurrentWrites() throws Exception {
        workspaces.save(workspace());
        sessions.save(session());
        runs.save(run());

        int writers = 12;
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RunEvent>> futures = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(2, TimeUnit.SECONDS);
                return runEvents.append(new RunEventDraft(
                        new RunId("run_1234567890abcdef"),
                        RunEventType.ASSISTANT_MESSAGE_DELTA,
                        "trace_1234567890abcdef",
                        NOW.plusMillis(index),
                        Map.of("index", index)));
            }));
        }

        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<RunEvent> appended = new ArrayList<>();
        for (Future<RunEvent> future : futures) {
            appended.add(future.get(5, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        assertThat(appended).extracting(RunEvent::seq)
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.LongStream.rangeClosed(1, writers).boxed().toList());
        assertThat(runEvents.findByRunIdAfter(new RunId("run_1234567890abcdef"), 0L, 50))
                .hasSize(writers);
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
                .param("occurredAt", Timestamp.from(event.occurredAt()))
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

        // V10 种子会预先写入 F-COSS 的运行态 Workspace，因此这里只断言"测试创建的工作区在分页结果里"
        // 而非"分页结果只有这一条"，避免后续种子数据扩张再次破坏该测试。
        assertThat(workspaces.findPage(new PageRequest(1, 10)).items())
                .extracting(Workspace::workspaceId)
                .contains(workspace.workspaceId());
        assertThat(sessions.findByWorkspaceId(workspace.workspaceId(), new PageRequest(1, 10)).items())
                .extracting(Session::sessionId)
                .containsExactly(session.sessionId());
        assertThat(sessionMessages.findBySessionId(session.sessionId(), new PageRequest(1, 10)).items())
                .extracting(SessionMessage::content)
                .containsExactly("first", "second");
    }

    @Test
    void sessionMessagesAndRunsPersistPerRunUsageSnapshotFields() {
        workspaces.save(workspace());
        sessions.save(session());
        TokenUsage usage = new TokenUsage(11L, 12L, 3L, 4L, 5L);
        Run enrichedRun = run().withUsage(usage, new BigDecimal("0.12345678"));
        runs.save(enrichedRun);
        SessionMessage assistant = new SessionMessage(
                new SessionMessageId("msg_3234567890abcdef"),
                new SessionId("ses_1234567890abcdef"),
                SessionMessageRole.ASSISTANT,
                "assistant answer",
                NOW.plusSeconds(1),
                "trace_1234567890abcdef",
                enrichedRun.runId(),
                "opencode",
                "msg_remote_1234567890abcdef",
                "[{\"id\":\"part_1\",\"type\":\"text\",\"text\":\"assistant answer\"}]",
                usage,
                new BigDecimal("0.12345678"),
                NOW.plusSeconds(2));

        sessionMessages.save(assistant);

        assertThat(runs.findById(enrichedRun.runId())).contains(enrichedRun);
        assertThat(sessionMessages.findById(assistant.messageId())).contains(assistant);
        assertThat(sessionMessages.findBySessionId(assistant.sessionId(), new PageRequest(1, 10)).items())
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.runId()).isEqualTo(enrichedRun.runId());
                    assertThat(message.agentId()).isEqualTo("opencode");
                    assertThat(message.remoteMessageId()).isEqualTo("msg_remote_1234567890abcdef");
                    assertThat(message.partsJson()).contains("\"part_1\"");
                    assertThat(message.tokenUsage()).isEqualTo(usage);
                    assertThat(message.costUsd()).isEqualByComparingTo("0.12345678");
                    assertThat(message.updatedAt()).isEqualTo(NOW.plusSeconds(2));
                });
    }

    @Test
    void runRepositoryFindsLatestNonTerminalRunBySessionId() {
        workspaces.save(workspace());
        sessions.save(session());
        Run pending = run("run_1234567890abcdef", RunStatus.PENDING, 0);
        Run running = run("run_2234567890abcdef", RunStatus.RUNNING, 5);
        Run succeeded = run("run_3234567890abcdef", RunStatus.SUCCEEDED, 9);
        runs.save(pending);
        runs.save(running);
        runs.save(succeeded);

        assertThat(runs.findLatestActiveBySessionId(new SessionId("ses_1234567890abcdef")))
                .contains(running);
    }

    @Test
    void runtimeSourceFieldsPersistForScheduledConversationReservation() {
        UserId userId = new UserId("usr_scheduler_owner");
        users.save(User.createNew(userId.value(), "AUTH_SCHEDULER", "scheduler-owner", "hash", "org", "rd", "dept"));
        workspaces.save(workspace());

        Session scheduledSession = session()
                .withSource(ConversationSourceType.SCHEDULED_TASK, "str_1234567890abcdef", userId);
        sessions.save(scheduledSession);

        Run scheduledRun = run()
                .withSource(ConversationSourceType.SCHEDULED_TASK, "str_1234567890abcdef", userId);
        runs.save(scheduledRun);

        SessionMessage scheduledMessage = sessionMessage("msg_1234567890abcdef", "scheduled prompt")
                .withSource(ConversationSourceType.SCHEDULED_TASK, "str_1234567890abcdef", userId);
        sessionMessages.save(scheduledMessage);

        assertThat(sessions.findById(scheduledSession.sessionId())).contains(scheduledSession);
        assertThat(runs.findById(scheduledRun.runId())).contains(scheduledRun);
        assertThat(sessionMessages.findById(scheduledMessage.messageId())).contains(scheduledMessage);
    }

    @Test
    void scheduledTaskRepositoryPersistsDefinitionsPlansAndRunRecords() {
        UserId userId = new UserId("usr_scheduler_owner");
        users.save(User.createNew(userId.value(), "AUTH_SCHEDULER", "scheduler-owner", "hash", "org", "rd", "dept"));
        ScheduledTask task = ScheduledTask.registered(
                new ScheduledTaskKey("daily.cleanup"),
                "每日清理",
                "0 0 2 * * *",
                Duration.ofMinutes(5),
                NOW,
                "trace_1234567890abcdef").withNextFireAt(NOW.minusSeconds(1), NOW.plusSeconds(1));

        scheduledTasks.saveTask(task);
        ScheduledTaskPlan plan = new ScheduledTaskPlan(
                new ScheduledTaskPlanId("stp_1234567890abcdef"),
                task.taskKey(),
                userId,
                "0 0 9 * * MON-FRI",
                Map.of("workspaceId", "wrk_1234567890abcdef"),
                true,
                NOW.plusSeconds(60),
                NOW,
                NOW,
                "trace_1234567890abcdef");
        scheduledTasks.savePlan(plan);
        ScheduledTaskRun pendingRun = ScheduledTaskRun.pending(
                new ScheduledTaskRunId("str_1234567890abcdef"),
                task.taskKey(),
                plan.planId(),
                ScheduledTaskTriggerType.USER_PLAN,
                userId,
                NOW,
                "trace_1234567890abcdef");

        scheduledTasks.saveRun(pendingRun);
        ScheduledTaskRun running = pendingRun.start("instance-a", NOW.plusSeconds(1));
        scheduledTasks.saveRun(running);
        ScheduledTaskRun succeeded = running.succeed(Map.of("deleted", 3), NOW.plusSeconds(2));
        scheduledTasks.saveRun(succeeded);
        ScheduledTaskRun stopPendingRun = ScheduledTaskRun.pending(
                new ScheduledTaskRunId("str_stop_1234567890abcdef"),
                task.taskKey(),
                plan.planId(),
                ScheduledTaskTriggerType.MANUAL,
                userId,
                NOW.plusSeconds(3),
                "trace_1234567890abcdef");
        ScheduledTaskRun stopRunning = stopPendingRun.start("instance-a", NOW.plusSeconds(4));
        scheduledTasks.saveRun(stopRunning);
        ScheduledTaskRun stopping = stopRunning.requestStop(userId, "管理员手工停止", NOW.plusSeconds(5));
        scheduledTasks.saveRun(stopping);

        assertThat(scheduledTasks.findTaskByKey(task.taskKey())).contains(task);
        assertThat(scheduledTasks.findDueTasks(NOW, 10)).containsExactly(task);
        assertThat(scheduledTasks.findPlanById(plan.planId())).contains(plan);
        assertThat(scheduledTasks.findRunById(pendingRun.taskRunId())).contains(succeeded);
        assertThat(scheduledTasks.findActiveRunByTaskKey(task.taskKey())).contains(stopping);
        ScheduledTaskRun manuallyStopped = stopping.manuallyStopped(NOW.plusSeconds(6));
        scheduledTasks.saveRun(manuallyStopped);
        assertThat(scheduledTasks.findRunById(stopPendingRun.taskRunId())).contains(manuallyStopped);
        assertThat(jdbcClient.sql("""
                        select count(*)
                        from dictionaries
                        where dict_key in (
                            'SCHEDULER_RUN_STATUS',
                            'SCHEDULER_TRIGGER_TYPE',
                            'SCHEDULER_TASK_REGISTRATION_STATUS'
                        )
                        """)
                .query(Long.class)
                .single()).isGreaterThanOrEqualTo(12L);
        assertThat(scheduledTasks.findRuns(
                        new ScheduledTaskRunFilter(
                                task.taskKey(),
                                ScheduledTaskRunStatus.SUCCEEDED,
                                ScheduledTaskTriggerType.USER_PLAN,
                                userId),
                        new PageRequest(1, 10)).items())
                .containsExactly(succeeded);
    }

    @Test
    void configurationManagementRepositoriesPersistV7ConfigurationTables() {
        ApplicationId appId = new ApplicationId("app_gcms");
        UserId userId = new UserId("usr_config");
        users.save(User.createNew(userId.value(), "AUTH_CONFIG", "config-user", "hash", "org", "rd", "dept"));
        jdbcClient.sql("""
                        insert into applications(app_id, app_name, enabled, created_at, updated_at)
                        values (:appId, :appName, true, :createdAt, :updatedAt)
                        """)
                .param("appId", appId.value())
                .param("appName", "F-GCMS")
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();

        configurationManagement.saveMember(ApplicationMember.active(appId, userId, NOW));
        assertThat(configurationManagement.findActiveMembers(appId)).extracting(ApplicationMember::userId).containsExactly(userId);
        configurationManagement.deleteMember(appId, userId);
        assertThat(configurationManagement.findActiveMembers(appId)).isEmpty();
        configurationManagement.saveMember(ApplicationMember.active(appId, userId, NOW.plusSeconds(1)));
        assertThat(configurationManagement.findActiveMembers(appId)).extracting(ApplicationMember::userId).containsExactly(userId);

        CodeRepository repository = new CodeRepository(
                new CodeRepositoryId("repo_config"),
                "git@gitee.com:demo/repo.git",
                "配置库",
                "configrepo",
                true,
                NOW,
                NOW);
        configurationManagement.saveRepository(repository);
        assertThat(configurationManagement.findRepositoryByEnglishName("configrepo")).contains(repository);
        configurationManagement.linkRepository(appId, repository.repositoryId());
        assertThat(configurationManagement.findRepositoriesByApplication(appId)).containsExactly(repository);
        assertThat(configurationManagement.findApplicationsByRepository(repository.repositoryId()))
                .extracting(application -> application.appName())
                .containsExactly("F-GCMS");

        ApplicationWorkspace workspace = new ApplicationWorkspace(
                new ApplicationWorkspaceId("awp_config"),
                appId,
                repository.repositoryId(),
                "main",
                "src/main",
                "main",
                NOW,
                NOW);
        configurationManagement.saveWorkspace(workspace);
        assertThat(configurationManagement.findWorkspaces(appId)).extracting(ApplicationWorkspace::directoryPath).containsExactly("src/main");

        configurationManagement.saveSshKey(new UserSshKey(
                new SshKeyId("ssh_config"),
                userId,
                "work",
                "SHA256:abc",
                "cipher",
                "nonce",
                NOW));
        assertThat(configurationManagement.findSshKeys(userId)).extracting(UserSshKey::name).containsExactly("work");
        assertThatThrownBy(() -> configurationManagement.saveSshKey(new UserSshKey(
                        new SshKeyId("ssh_config_2"),
                        userId,
                        "second",
                        "SHA256:def",
                        "cipher2",
                        "nonce2",
                        NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void commonParametersAreSeededAndWorkspaceCreateOperationsPersistProgress() {
        assertThat(commonParameters.findByEnglishNameAndPlatform("OPENCODE_APP_WORKSPACE_ROOT", ParameterPlatform.LINUX))
                .map(CommonParameter::parameterValue)
                .contains("/data/.testagent/agent-opencode/workspace/appworkspace/");
        assertThat(commonParameters.findByEnglishNameAndPlatform("OPENCODE_SESSION_DIR", ParameterPlatform.WINDOWS))
                .map(CommonParameter::parameterValue)
                .contains("D:/data/.testagent/agent-opencode/.session/");

        users.save(User.createNew("usr_1234567890abcdef", "AUTH_PROGRESS", "progress-user", "hash", "org", "rd", "dept"));
        jdbcClient.sql("""
                        insert into applications(app_id, app_name, enabled, created_at, updated_at)
                        values (:appId, :appName, true, :createdAt, :updatedAt)
                        """)
                .param("appId", "app_gcms")
                .param("appName", "F-GCMS")
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();

        WorkspaceCreateOperation operation = workspaceCreateOperations.start(
                "wco_test_1234567890",
                new ApplicationId("app_gcms"),
                new UserId("usr_1234567890abcdef"),
                "trace_1234567890abcdef",
                NOW);
        assertThat(operation.status()).isEqualTo(WorkspaceCreateOperationStatus.RUNNING);

        workspaceCreateOperations.markStep("wco_test_1234567890", WorkspaceCreateOperationStep.PREPARING_REPOSITORY, NOW.plusSeconds(1));
        workspaceCreateOperations.markSucceeded(
                "wco_test_1234567890",
                new ApplicationWorkspaceId("awp_1234567890abcdef"),
                new ApplicationWorkspaceVersionId("awv_1234567890abcdef"),
                NOW.plusSeconds(2));

        assertThat(workspaceCreateOperations.findById("wco_test_1234567890"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.status()).isEqualTo(WorkspaceCreateOperationStatus.SUCCEEDED);
                    assertThat(saved.currentStep()).isEqualTo(WorkspaceCreateOperationStep.COMPLETED);
                    assertThat(saved.workspaceId()).isEqualTo(new ApplicationWorkspaceId("awp_1234567890abcdef"));
                    assertThat(saved.versionId()).isEqualTo(new ApplicationWorkspaceVersionId("awv_1234567890abcdef"));
                });
    }

    @Test
    void managedWorkspaceRepositoriesPersistV9Tables() {
        ApplicationId appId = new ApplicationId("app_managed");
        UserId userId = new UserId("usr_managed");
        users.save(User.createNew(userId.value(), "AUTH_MANAGED", "managed-user", "hash", "org", "rd", "dept"));
        jdbcClient.sql("""
                        insert into applications(app_id, app_name, enabled, created_at, updated_at)
                        values (:appId, :appName, true, :createdAt, :updatedAt)
                        """)
                .param("appId", appId.value())
                .param("appName", "F-GCMS")
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .update();
        CodeRepository repository = configurationManagement.saveRepository(new CodeRepository(
                new CodeRepositoryId("repo_managed"),
                "git@gitee.com:demo/managed.git",
                "托管库",
                "managedrepo",
                true,
                NOW,
                NOW));
        ApplicationWorkspace template = configurationManagement.saveWorkspace(new ApplicationWorkspace(
                new ApplicationWorkspaceId("awp_managed"),
                appId,
                repository.repositoryId(),
                "main",
                "F-GCMS/workspace",
                "F-GCMS 工作区",
                NOW,
                NOW));
        Workspace applicationRuntime = new Workspace(
                new WorkspaceId("wrk_app_managed"),
                "F-GCMS-20260707",
                "/data/appworkspace/20260707/repo_managed/F-GCMS/workspace",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "10.8.0.11",
                "trace_1234567890abcdef");
        Workspace personalRuntime = new Workspace(
                new WorkspaceId("wrk_psw_managed"),
                "私人空间",
                "/data/personalworktree/20260707/AUTH_MANAGED/repo_managed/psw_1/F-GCMS/workspace",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
        workspaces.save(applicationRuntime);
        workspaces.save(personalRuntime);

        ApplicationWorkspaceVersion version = managedWorkspaces.saveVersion(new ApplicationWorkspaceVersion(
                new ApplicationWorkspaceVersionId("awv_managed"),
                template.workspaceId(),
                appId,
                repository.repositoryId(),
                "20260707",
                "feature_testagent_20260707",
                "/data/appworkspace/20260707/repo_managed",
                applicationRuntime.rootPath(),
                applicationRuntime.workspaceId(),
                userId,
                ManagedWorkspaceStatus.ACTIVE,
                NOW,
                NOW));
        managedWorkspaces.updateVersionTargetCommit(version.versionId(), "abc123", NOW.plusSeconds(2));
        ApplicationWorkspaceVersion updatedVersion = managedWorkspaces.findVersion(version.versionId()).orElseThrow();
        assertThat(updatedVersion.targetCommitHash()).isEqualTo("abc123");
        assertThat(updatedVersion.targetCommitUpdatedAt()).isEqualTo(NOW.plusSeconds(2));

        ApplicationWorkspaceVersionReplica replica = managedWorkspaces.saveVersionReplica(new ApplicationWorkspaceVersionReplica(
                new ApplicationWorkspaceVersionReplicaId("awr_managed_10_8_0_11"),
                version.versionId(),
                "10.8.0.11",
                version.repoRootPath(),
                version.workspaceRootPath(),
                applicationRuntime.workspaceId(),
                "abc123",
                WorkspaceReplicaSyncStatus.READY,
                null,
                NOW.plusSeconds(3),
                "trace_1234567890abcdef",
                NOW,
                NOW.plusSeconds(3)));
        PersonalWorkspace personal = managedWorkspaces.savePersonalWorkspace(new PersonalWorkspace(
                new PersonalWorkspaceId("psw_managed"),
                version.versionId(),
                appId,
                template.workspaceId(),
                userId,
                "私人空间",
                "feature_testagent_20260707_AUTH_MANAGED_psw_managed",
                "/data/personalworktree/20260707/AUTH_MANAGED/repo_managed/psw_managed",
                personalRuntime.rootPath(),
                personalRuntime.workspaceId(),
                "abc123",
                ManagedWorkspaceStatus.ACTIVE,
                NOW,
                NOW));
        managedWorkspaces.savePreference(new UserWorkspacePreference(userId, null, applicationRuntime.workspaceId(), NOW));
        managedWorkspaces.savePreference(new UserWorkspacePreference(userId, appId, personalRuntime.workspaceId(), NOW.plusSeconds(1)));
        managedWorkspaces.saveSyncRecord(new WorkspaceSyncRecord(
                new WorkspaceSyncRecordId("sync_managed"),
                userId,
                personalRuntime.workspaceId(),
                applicationRuntime.workspaceId(),
                WorkspaceSyncDirection.PERSONAL_TO_APPLICATION,
                List.of("src/App.java"),
                true,
                WorkspaceSyncStatus.SUCCEEDED,
                "trace_1234567890abcdef",
                NOW));

        assertThat(managedWorkspaces.findVersions(template.workspaceId())).containsExactly(updatedVersion);
        assertThat(managedWorkspaces.findVersionByTemplateAndVersion(template.workspaceId(), "20260707")).contains(updatedVersion);
        assertThat(managedWorkspaces.findVersionByRuntimeWorkspace(applicationRuntime.workspaceId())).contains(updatedVersion);
        assertThat(managedWorkspaces.findVersionReplica(version.versionId(), "10.8.0.11")).contains(replica);
        assertThat(managedWorkspaces.findVersionReplicaByRuntimeWorkspace(applicationRuntime.workspaceId())).contains(replica);
        assertThat(managedWorkspaces.findPersonalWorkspaces(version.versionId(), userId)).containsExactly(personal);
        assertThat(managedWorkspaces.findPersonalWorkspaceByRuntimeWorkspace(personalRuntime.workspaceId())).contains(personal);
        assertThat(managedWorkspaces.findGlobalPreference(userId).map(UserWorkspacePreference::workspaceId))
                .contains(applicationRuntime.workspaceId());
        assertThat(managedWorkspaces.findApplicationPreference(userId, appId).map(UserWorkspacePreference::workspaceId))
                .contains(personalRuntime.workspaceId());
        Integer syncRecords = jdbcClient.sql("select count(*) from workspace_sync_records where sync_record_id = 'sync_managed'")
                .query(Integer.class)
                .single();
        assertThat(syncRecords).isEqualTo(1);
        assertThatThrownBy(() -> managedWorkspaces.savePersonalWorkspace(new PersonalWorkspace(
                        new PersonalWorkspaceId("psw_managed_2"),
                        version.versionId(),
                        appId,
                        template.workspaceId(),
                        userId,
                        "私人空间",
                        "feature_testagent_20260707_AUTH_MANAGED_psw_managed_2",
                        "/data/personalworktree/20260707/AUTH_MANAGED/repo_managed/psw_managed_2",
                        personalRuntime.rootPath() + "_2",
                        new WorkspaceId("wrk_psw_managed_2"),
                        "abc123",
                        ManagedWorkspaceStatus.ACTIVE,
                        NOW,
                        NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migrationGrantsDefaultUserSuperAdminRole() {
        Integer roleCount = jdbcClient.sql("""
                        select count(*)
                        from user_roles ur
                        join users u on u.user_id = ur.user_id
                        join dictionaries d on d.dict_id = ur.dict_id
                        where u.username = '888888888'
                          and d.dict_key = 'ROLE'
                          and d.dict_value = 'SUPER_ADMIN'
                        """)
                .query(Integer.class)
                .single();

        assertThat(roleCount).isEqualTo(1);
    }

    @Test
    void sessionsSupportGlobalSearchPinnedOrderingAndArchiveFiltering() {
        Workspace workspace = workspace();
        workspaces.save(workspace);
        sessions.save(session("ses_1234567890abcdef", "Alpha session", false, SessionStatus.ACTIVE, 0));
        sessions.save(session("ses_2234567890abcdef", "Demo pinned", true, SessionStatus.ACTIVE, 1));
        sessions.save(session("ses_3234567890abcdef", "Demo archived", true, SessionStatus.ARCHIVED, 2));

        PageResponse<Session> all = sessions.findPage(null, new PageRequest(1, 10));
        PageResponse<Session> page = sessions.findPage("demo", new PageRequest(1, 10));

        assertThat(all.items()).extracting(Session::sessionId)
                .containsExactly(new SessionId("ses_2234567890abcdef"), new SessionId("ses_1234567890abcdef"));
        assertThat(page.items()).extracting(Session::sessionId)
                .containsExactly(new SessionId("ses_2234567890abcdef"));
        assertThat(page.items().getFirst().pinned()).isTrue();
        assertThat(sessions.findByWorkspaceId(workspace.workspaceId(), new PageRequest(1, 10)).items())
                .extracting(Session::sessionId)
                .containsExactly(new SessionId("ses_2234567890abcdef"), new SessionId("ses_1234567890abcdef"));
    }

    @Test
    void executionNodesFindRoutableNodesByCapacityStatusAndStableOrdering() {
        executionNodes.save(executionNode(
                "node_1234567890abcdef",
                ExecutionNodeStatus.READY,
                1,
                4,
                50,
                0));
        executionNodes.save(executionNode(
                "node_2234567890abcdef",
                ExecutionNodeStatus.READY,
                0,
                2,
                10,
                1));
        executionNodes.save(executionNode(
                "node_3234567890abcdef",
                ExecutionNodeStatus.READY,
                0,
                2,
                100,
                2));
        executionNodes.save(executionNode(
                "node_4234567890abcdef",
                ExecutionNodeStatus.BUSY,
                0,
                4,
                100,
                3));
        executionNodes.save(executionNode(
                "node_5234567890abcdef",
                ExecutionNodeStatus.READY,
                2,
                2,
                100,
                4));

        List<ExecutionNode> routableNodes = executionNodes.findRoutableNodes(10);

        assertThat(routableNodes).extracting(ExecutionNode::executionNodeId)
                .containsExactly(
                        new ExecutionNodeId("node_3234567890abcdef"),
                        new ExecutionNodeId("node_2234567890abcdef"),
                        new ExecutionNodeId("node_1234567890abcdef"));
    }

    @Test
    void opencodeProcessManagementRepositoriesSaveAndReadTopology() {
        users.save(processUser("usr_process_user", "process-user"));

        LinuxServer linuxServer = linuxServer();
        BackendJavaProcess backendProcess = backendJavaProcess();
        OpencodeContainer container = opencodeContainer();
        OpencodeContainerManager manager = opencodeContainerManager();
        OpencodeManagerBackendConnection connection = managerBackendConnection();
        OpencodeServerProcess process = opencodeServerProcess("ocp_1234567890abcdef", "usr_process_user", 4096);
        UserOpencodeProcessBinding binding = userBinding("usr_process_user", "ocp_1234567890abcdef", 4096);

        opencodeProcesses.saveLinuxServer(linuxServer);
        opencodeProcesses.saveBackendJavaProcess(backendProcess);
        opencodeProcesses.saveContainer(container);
        opencodeProcesses.saveContainerManager(manager);
        opencodeProcesses.saveManagerBackendConnection(connection);
        opencodeProcesses.saveOpencodeServerProcess(process);
        opencodeProcesses.saveUserBinding(binding);

        assertThat(opencodeProcesses.findLinuxServerById(linuxServer.linuxServerId())).contains(linuxServer);
        assertThat(opencodeProcesses.findBackendJavaProcessById(backendProcess.backendProcessId())).contains(backendProcess);
        assertThat(opencodeProcesses.findContainerById(container.containerId())).contains(container);
        assertThat(opencodeProcesses.findHealthyContainers(10)).containsExactly(container);
        assertThat(opencodeProcesses.findHealthyContainersByLinuxServer(new LinuxServerId("10.8.0.12"), 10))
                .containsExactly(container);
        assertThat(opencodeProcesses.findContainerManagerById(manager.managerId())).contains(manager);
        assertThat(opencodeProcesses.findManagerBackendConnection(manager.managerId(), backendProcess.backendProcessId()))
                .contains(connection);
        assertThat(opencodeProcesses.findOpencodeServerProcessById(process.processId())).contains(process);
        assertThat(opencodeProcesses.findUserBinding(new UserId("usr_process_user"), " OPENCODE ")).contains(binding);
        assertThat(opencodeProcesses.findOccupiedPorts(new LinuxServerId("10.8.0.12"), new OpencodeContainerId("ctr_01")))
                .containsExactly(4096);
        // V17 migration 在每个集成测试 setUp 阶段会再种入本地 opencode 机器与默认开发用户的进程，
        // 因此这些列举/计数断言需要容忍 V17 的种子行，单独断言"测试用例自己创建的行"仍可定位。
        assertThat(opencodeProcesses.findOpencodeServerProcesses(10)).contains(process);
        assertThat(opencodeProcesses.findLinuxServers(500)).contains(linuxServer);
        assertThat(opencodeProcesses.findBackendJavaProcesses(500)).containsExactly(backendProcess);
        assertThat(opencodeProcesses.findContainers(500)).contains(container);
        assertThat(opencodeProcesses.findContainerManagers(500)).contains(manager);
        assertThat(opencodeProcesses.findManagerBackendConnections(500)).containsExactly(connection);
        assertThat(opencodeProcesses.countUserBindings()).isEqualTo(2);
        assertThat(opencodeProcesses.findUserBindingsByProcessIds(List.of(process.processId())))
                .containsEntry(process.processId(), binding);
        OpencodeServerProcessFilter filter = new OpencodeServerProcessFilter(
                OpencodeServerProcessStatus.RUNNING,
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                new UserId("usr_process_user"));
        PageResponse<OpencodeServerProcess> filtered = opencodeProcesses.findOpencodeServerProcesses(filter, new PageRequest(1, 10));
        assertThat(filtered.items()).containsExactly(process);
        assertThat(filtered.total()).isEqualTo(1);
        assertThat(opencodeProcesses.countOpencodeServerProcesses(filter)).isEqualTo(1);
    }

    @Test
    void opencodeProcessManagementPagesAndFiltersServerProcesses() {
        users.save(processUser("usr_process_user", "process-user"));
        users.save(processUser("usr_process_second", "process-second"));
        opencodeProcesses.saveLinuxServer(linuxServer());
        opencodeProcesses.saveBackendJavaProcess(backendJavaProcess());
        opencodeProcesses.saveContainer(opencodeContainer());
        opencodeProcesses.saveContainerManager(opencodeContainerManager());
        OpencodeServerProcess running = opencodeServerProcess(
                "ocp_1234567890abcdef",
                "usr_process_user",
                4096,
                OpencodeServerProcessStatus.RUNNING);
        OpencodeServerProcess unhealthy = opencodeServerProcess(
                "ocp_2234567890abcdef",
                "usr_process_second",
                4097,
                OpencodeServerProcessStatus.UNHEALTHY);
        opencodeProcesses.saveOpencodeServerProcess(running);
        opencodeProcesses.saveOpencodeServerProcess(unhealthy);

        PageResponse<OpencodeServerProcess> runningPage = opencodeProcesses.findOpencodeServerProcesses(
                new OpencodeServerProcessFilter(
                        OpencodeServerProcessStatus.RUNNING,
                        new LinuxServerId("10.8.0.12"),
                        new OpencodeContainerId("ctr_01"),
                        null),
                new PageRequest(1, 10));
        PageResponse<OpencodeServerProcess> secondUserPage = opencodeProcesses.findOpencodeServerProcesses(
                new OpencodeServerProcessFilter(null, null, null, new UserId("usr_process_second")),
                new PageRequest(1, 10));

        assertThat(runningPage.items()).containsExactly(running);
        assertThat(runningPage.total()).isEqualTo(1);
        assertThat(opencodeProcesses.countOpencodeServerProcesses(new OpencodeServerProcessFilter(
                OpencodeServerProcessStatus.RUNNING,
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                null))).isEqualTo(1);
        assertThat(secondUserPage.items()).containsExactly(unhealthy);
        assertThat(secondUserPage.total()).isEqualTo(1);
    }

    @Test
    void opencodeProcessManagementFindsReadyBackendsAndConnectedContainers() {
        LinuxServer linuxServer = linuxServer();
        BackendJavaProcess currentBackend = backendJavaProcess();
        BackendJavaProcess staleBackend = new BackendJavaProcess(
                new BackendProcessId("bjp_2234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                "http://10.8.0.12:8081",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW.minusSeconds(60),
                NOW,
                NOW,
                "trace_2234567890abcdef");
        BackendJavaProcess otherBackend = new BackendJavaProcess(
                new BackendProcessId("bjp_3234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                "http://10.8.0.12:8082",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                "trace_3234567890abcdef");
        OpencodeContainer connectedContainer = opencodeContainer();
        OpencodeContainer otherBackendContainer = new OpencodeContainer(
                new OpencodeContainerId("ctr_02"),
                new LinuxServerId("10.8.0.12"),
                "opencode-b",
                4101,
                4105,
                4,
                0,
                OpencodeContainerStatus.READY,
                NOW,
                NOW,
                NOW.plusMillis(1),
                "trace_3234567890abcdef");
        OpencodeContainerManager connectedManager = opencodeContainerManager();
        OpencodeContainerManager otherManager = new OpencodeContainerManager(
                new ContainerManagerId("mgr_2234567890abcdef"),
                new OpencodeContainerId("ctr_02"),
                new LinuxServerId("10.8.0.12"),
                "opencode-manager.v1",
                ManagerConnectionStatus.CONNECTED,
                Map.of("start", true, "health", true),
                NOW,
                NOW,
                NOW,
                "trace_3234567890abcdef");

        opencodeProcesses.saveLinuxServer(linuxServer);
        opencodeProcesses.saveBackendJavaProcess(currentBackend);
        opencodeProcesses.saveBackendJavaProcess(staleBackend);
        opencodeProcesses.saveBackendJavaProcess(otherBackend);
        opencodeProcesses.saveContainer(connectedContainer);
        opencodeProcesses.saveContainer(otherBackendContainer);
        opencodeProcesses.saveContainerManager(connectedManager);
        opencodeProcesses.saveContainerManager(otherManager);
        opencodeProcesses.saveManagerBackendConnection(managerBackendConnection());
        opencodeProcesses.saveManagerBackendConnection(new OpencodeManagerBackendConnection(
                otherManager.managerId(),
                otherBackend.backendProcessId(),
                ManagerConnectionStatus.CONNECTED,
                NOW,
                NOW,
                NOW,
                "trace_3234567890abcdef"));
        opencodeProcesses.saveManagerBackendConnection(new OpencodeManagerBackendConnection(
                otherManager.managerId(),
                currentBackend.backendProcessId(),
                ManagerConnectionStatus.DISCONNECTED,
                NOW,
                NOW,
                NOW,
                "trace_4234567890abcdef"));

        assertThat(opencodeProcesses.findReadyBackendJavaProcesses(NOW.minusSeconds(5), 10))
                .containsExactly(currentBackend, otherBackend);
        assertThat(opencodeProcesses.findHealthyContainersConnectedToBackend(currentBackend.backendProcessId(), 10))
                .containsExactly(connectedContainer);
        assertThat(opencodeProcesses.findHealthyContainersConnectedToBackendByLinuxServer(
                        currentBackend.backendProcessId(),
                        new LinuxServerId("10.8.0.12"),
                        10))
                .containsExactly(connectedContainer);
    }

    @Test
    void opencodeProcessManagementConstraintsProtectCurrentTopology() {
        users.save(processUser("usr_process_user", "process-user"));
        users.save(processUser("usr_process_second", "process-second"));
        opencodeProcesses.saveLinuxServer(linuxServer());
        opencodeProcesses.saveBackendJavaProcess(backendJavaProcess());
        opencodeProcesses.saveContainer(opencodeContainer());
        opencodeProcesses.saveContainerManager(opencodeContainerManager());
        opencodeProcesses.saveOpencodeServerProcess(opencodeServerProcess("ocp_1234567890abcdef", "usr_process_user", 4096));

        assertThatThrownBy(() -> opencodeProcesses.saveContainerManager(new OpencodeContainerManager(
                        new ContainerManagerId("mgr_2234567890abcdef"),
                        new OpencodeContainerId("ctr_01"),
                        new LinuxServerId("10.8.0.12"),
                        "v1",
                        ManagerConnectionStatus.CONNECTED,
                        Map.of("start", true),
                        NOW,
                        NOW,
                        NOW,
                        "trace_1234567890abcdef")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> opencodeProcesses.saveOpencodeServerProcess(
                        opencodeServerProcess("ocp_2234567890abcdef", "usr_process_second", 4096)))
                .isInstanceOf(DataIntegrityViolationException.class);

        opencodeProcesses.saveOpencodeServerProcess(opencodeServerProcess("ocp_3234567890abcdef", "usr_process_second", 4097));
        opencodeProcesses.saveUserBinding(userBinding("usr_process_user", "ocp_1234567890abcdef", 4096));
        assertThatThrownBy(() -> jdbcClient.sql("""
                                insert into user_opencode_process_bindings(
                                    user_id, agent_id, process_id, linux_server_id, port,
                                    status, trace_id, created_at, updated_at
                                )
                                values (
                                    :userId, :agentId, :processId, :linuxServerId, :port,
                                    :status, :traceId, :createdAt, :updatedAt
                                )
                                """)
                        .param("userId", "usr_process_user")
                        .param("agentId", "opencode")
                        .param("processId", "ocp_3234567890abcdef")
                        .param("linuxServerId", "10.8.0.12")
                        .param("port", 4097)
                        .param("status", UserOpencodeProcessBindingStatus.ACTIVE.name())
                        .param("traceId", "trace_1234567890abcdef")
                        .param("createdAt", Timestamp.from(NOW))
                        .param("updatedAt", Timestamp.from(NOW))
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
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
        return session("ses_1234567890abcdef", "Initial session", false, SessionStatus.ACTIVE, 0);
    }

    private static Session session(String sessionId, String title, boolean pinned, SessionStatus status, long secondOffset) {
        return new Session(
                new SessionId(sessionId),
                new WorkspaceId("wrk_1234567890abcdef"),
                title,
                status,
                NOW,
                NOW.plusSeconds(secondOffset),
                "trace_1234567890abcdef",
                null,
                null,
                pinned);
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
        return run("run_1234567890abcdef", RunStatus.PENDING, 0);
    }

    private static Run run(String runId, RunStatus status, long secondOffset) {
        return new Run(
                new RunId(runId),
                new SessionId("ses_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                status,
                NOW,
                NOW.plusSeconds(secondOffset),
                "trace_1234567890abcdef");
    }

    private static ExecutionNode executionNode() {
        return executionNode("node_1234567890abcdef", ExecutionNodeStatus.READY, 0, 4, 100, 0);
    }

    private static ExecutionNode executionNode(
            String nodeId,
            ExecutionNodeStatus status,
            int runningRuns,
            int maxRuns,
            int weight,
            long secondOffset) {
        return new ExecutionNode(
                new ExecutionNodeId(nodeId),
                "http://127.0.0.1:4096",
                status,
                runningRuns,
                maxRuns,
                weight,
                NOW,
                Set.of("chat", "diff"),
                NOW,
                NOW.plusSeconds(secondOffset),
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

    private static User processUser(String userId, String username) {
        return new User(
                new UserId(userId),
                "UA_" + username,
                username,
                "hash",
                "org",
                "rd",
                "dept",
                com.icbc.testagent.domain.user.UserStatus.ACTIVE,
                NOW,
                NOW);
    }

    private static LinuxServer linuxServer() {
        return new LinuxServer(
                new LinuxServerId("10.8.0.12"),
                "backend-a",
                LinuxServerStatus.READY,
                Map.of("containers", 1, "processes", 1),
                NOW,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static BackendJavaProcess backendJavaProcess() {
        return new BackendJavaProcess(
                new BackendProcessId("bjp_1234567890abcdef"),
                new LinuxServerId("10.8.0.12"),
                "http://10.8.0.12:8080",
                BackendJavaProcessStatus.READY,
                NOW,
                NOW,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static OpencodeContainer opencodeContainer() {
        return new OpencodeContainer(
                new OpencodeContainerId("ctr_01"),
                new LinuxServerId("10.8.0.12"),
                "opencode-a",
                4096,
                4100,
                4,
                1,
                OpencodeContainerStatus.READY,
                NOW,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static OpencodeContainerManager opencodeContainerManager() {
        return new OpencodeContainerManager(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new OpencodeContainerId("ctr_01"),
                new LinuxServerId("10.8.0.12"),
                "v1",
                ManagerConnectionStatus.CONNECTED,
                Map.of("start", true, "health", true),
                NOW,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static OpencodeManagerBackendConnection managerBackendConnection() {
        return new OpencodeManagerBackendConnection(
                new ContainerManagerId("mgr_1234567890abcdef"),
                new BackendProcessId("bjp_1234567890abcdef"),
                ManagerConnectionStatus.CONNECTED,
                NOW,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static OpencodeServerProcess opencodeServerProcess(String processId, String userId, int port) {
        return opencodeServerProcess(processId, userId, port, OpencodeServerProcessStatus.RUNNING);
    }

    private static OpencodeServerProcess opencodeServerProcess(
            String processId,
            String userId,
            int port,
            OpencodeServerProcessStatus status) {
        return new OpencodeServerProcess(
                new OpencodeProcessId(processId),
                new UserId(userId),
                new LinuxServerId("10.8.0.12"),
                new OpencodeContainerId("ctr_01"),
                port,
                12345L,
                "http://10.8.0.12:" + port,
                status,
                "/data/opencode/session/" + port,
                "/data/opencode/.config/opencode/",
                NOW,
                NOW,
                "ok",
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    private static UserOpencodeProcessBinding userBinding(String userId, String processId, int port) {
        return new UserOpencodeProcessBinding(
                new UserId(userId),
                "opencode",
                new OpencodeProcessId(processId),
                new LinuxServerId("10.8.0.12"),
                port,
                UserOpencodeProcessBindingStatus.ACTIVE,
                NOW,
                NOW,
                "trace_1234567890abcdef");
    }

    @Test
    void v17SeedLocalOpencodeMachineForDefaultUserIsIdempotent() {
        // V17 已在 setUp 阶段通过 Flyway 应用一次；这里验证种子行已写入。
        assertThat(opencodeProcesses.findLinuxServerById(new LinuxServerId("127.0.0.1")))
                .isPresent()
                .get()
                .extracting(LinuxServer::status)
                .isEqualTo(LinuxServerStatus.READY);
        assertThat(opencodeProcesses.findContainerById(new OpencodeContainerId("ctr_local_4096")))
                .isPresent();
        assertThat(opencodeProcesses.findContainerManagerById(new ContainerManagerId("mgr_local_4096")))
                .isPresent()
                .get()
                .extracting(OpencodeContainerManager::connectionStatus)
                .isEqualTo(ManagerConnectionStatus.CONNECTED);
        assertThat(opencodeProcesses.findOpencodeServerProcessById(new OpencodeProcessId("ocp_local_user_dev")))
                .isPresent()
                .get()
                .extracting(OpencodeServerProcess::baseUrl)
                .isEqualTo("http://127.0.0.1:4096");
        assertThat(opencodeProcesses.findUserBinding(new UserId("usr_test_dev"), "opencode"))
                .isPresent();

        // 重新执行 V17 也不应破坏数据或产生重复行。
        Flyway.configure()
                .dataSource(database)
                .locations("classpath:db/migration")
                .target("17")
                .load()
                .migrate();
        Flyway.configure()
                .dataSource(database)
                .locations("classpath:db/migration")
                .target("17")
                .load()
                .migrate();
        assertThat(opencodeProcesses.findContainerManagerById(new ContainerManagerId("mgr_local_4096")))
                .isPresent();
        assertThat(opencodeProcesses.findOpencodeServerProcessById(new OpencodeProcessId("ocp_local_user_dev")))
                .isPresent();
        assertThat(opencodeProcesses.findUserBinding(new UserId("usr_test_dev"), "opencode"))
                .isPresent();
    }

    @Test
    void v17SeedReusesExistingLocalOpencodePortProcess() {
        EmbeddedDatabase migrationDatabase = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testagent_v17_existing_port;MODE=PostgreSQL;DATABASE_TO_UPPER=false")
                .build();
        try {
            Flyway.configure()
                    .dataSource(migrationDatabase)
                    .locations("classpath:db/migration")
                    .target("16")
                    .load()
                    .migrate();
            JdbcClient migrationJdbc = JdbcClient.create(migrationDatabase);

            // 复现历史本地库：V17 尚未应用，但 127.0.0.1:4096 已有旧进程记录。
            migrationJdbc.sql("""
                            insert into linux_servers (
                                linux_server_id, name, status, capacity_summary_json,
                                last_heartbeat_at, trace_id, created_at, updated_at
                            )
                            values (
                                '127.0.0.1', 'local-opencode-host', 'READY', '{}',
                                now(), 'trace_existing_local_4096', now(), now()
                            )
                            """)
                    .update();
            migrationJdbc.sql("""
                            insert into opencode_containers (
                                container_id, linux_server_id, container_name,
                                port_start, port_end, max_processes, current_processes,
                                status, last_heartbeat_at, trace_id, created_at, updated_at
                            )
                            values (
                                'ctr_local_4096', '127.0.0.1', 'local-opencode',
                                4096, 4096, 1, 1,
                                'READY', now(), 'trace_existing_local_4096', now(), now()
                            )
                            """)
                    .update();
            migrationJdbc.sql("""
                            insert into opencode_server_processes (
                                process_id, user_id, linux_server_id, container_id, port, pid, base_url,
                                status, session_path, config_path, started_at, last_health_check_at,
                                health_message, trace_id, created_at, updated_at
                            )
                            values (
                                'ocp_existing_local_4096', 'usr_test_dev', '127.0.0.1', 'ctr_local_4096',
                                4096, null, 'http://127.0.0.1:4096',
                                'RUNNING', '/data/opencode/session/4096', '/data/opencode/.config/opencode/',
                                now(), now(), 'existing before V17', 'trace_existing_local_4096', now(), now()
                            )
                            """)
                    .update();

            Flyway.configure()
                    .dataSource(migrationDatabase)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            Integer processCount = migrationJdbc.sql("""
                            select count(*)
                            from opencode_server_processes
                            where linux_server_id = '127.0.0.1' and port = 4096
                            """)
                    .query(Integer.class)
                    .single();
            String boundProcessId = migrationJdbc.sql("""
                            select process_id
                            from user_opencode_process_bindings
                            where user_id = 'usr_test_dev' and agent_id = 'opencode'
                            """)
                    .query(String.class)
                    .single();

            assertThat(processCount).isEqualTo(1);
            assertThat(boundProcessId).isEqualTo("ocp_existing_local_4096");
        } finally {
            migrationDatabase.shutdown();
        }
    }
}
