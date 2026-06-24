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
import com.icbc.testagent.domain.configuration.SshKeyId;
import com.icbc.testagent.domain.configuration.UserSshKey;
import com.icbc.testagent.domain.event.RunEvent;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventType;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspace;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.icbc.testagent.domain.managedworkspace.UserWorkspacePreference;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncDirection;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncRecord;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncRecordId;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncStatus;
import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.node.ExecutionNodeId;
import com.icbc.testagent.domain.node.ExecutionNodeStatus;
import com.icbc.testagent.domain.routing.RoutingDecision;
import com.icbc.testagent.domain.routing.RoutingReason;
import com.icbc.testagent.domain.run.Run;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.domain.run.RunStatus;
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
import java.sql.Timestamp;
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
    private JdbcManagedWorkspaceRepository managedWorkspaces;
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
        managedWorkspaces = new JdbcManagedWorkspaceRepository(jdbcClient, objectMapper);
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
                true,
                NOW,
                NOW);
        configurationManagement.saveRepository(repository);
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

        assertThat(managedWorkspaces.findVersions(template.workspaceId())).containsExactly(version);
        assertThat(managedWorkspaces.findVersionByTemplateAndVersion(template.workspaceId(), "20260707")).contains(version);
        assertThat(managedWorkspaces.findVersionByRuntimeWorkspace(applicationRuntime.workspaceId())).contains(version);
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
}
