package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.AgentConfigOperation;
import com.enterprise.testagent.domain.configuration.AgentConfigOperationStatus;
import com.enterprise.testagent.domain.configuration.AgentConfigOperationStep;
import com.enterprise.testagent.domain.configuration.AgentConfigRepository;
import com.enterprise.testagent.domain.configuration.AgentConfigScope;
import com.enterprise.testagent.domain.configuration.AgentConfigWorktree;
import com.enterprise.testagent.domain.configuration.AgentConfigWorktreeStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.persistence.mybatis.AgentConfigMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisAgentConfigRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证 AgentConfig 仓储通过 MyBatis XML 访问数据库，并保留 worktree 所在服务器归属。
 */
class MyBatisAgentConfigRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-06-28T10:00:00Z");

    private SingleConnectionDataSource dataSource;
    private AgentConfigRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_mybatis_agent_config_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("20260715213000").load().migrate();
        insertUser();
        insertWorkspace();

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        AgentConfigMapper mapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(AgentConfigMapper.class);
        repository = new MyBatisAgentConfigRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void worktreeLinuxServerIdIsPersistedThroughMyBatisXmlMapper() {
        AgentConfigWorktree worktree = repository.saveWorktree(new AgentConfigWorktree(
                "agw_mybatis_1234567890",
                AgentConfigScope.PUBLIC,
                null,
                "10.0.0.8",
                "change-agent-20260628",
                "change-agent-20260628",
                "/data/.testagent/agent-opencode/.configdev/change-agent-20260628",
                new UserId("usr_test_dev"),
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));

        assertThat(repository.findWorktree(worktree.worktreeId()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.linuxServerId()).isEqualTo("10.0.0.8");
                    assertThat(saved.rootPath()).endsWith("change-agent-20260628");
                });
        assertThat(repository.findWorktrees(AgentConfigScope.PUBLIC, null, new UserId("usr_test_dev")))
                .extracting(AgentConfigWorktree::linuxServerId)
                .containsExactly("10.0.0.8");
    }

    @Test
    void publicWorktreeQueryFiltersByServerAndActiveStatusThroughMyBatisXmlMapper() {
        repository.saveWorktree(new AgentConfigWorktree(
                "agw_linux_8_old",
                AgentConfigScope.PUBLIC,
                null,
                "10.0.0.8",
                "old-change",
                "old-change",
                "/data/.testagent/agent-opencode/.configdev/old-change",
                new UserId("usr_test_dev"),
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW.plusSeconds(1)));
        repository.saveWorktree(new AgentConfigWorktree(
                "agw_linux_8_new",
                AgentConfigScope.PUBLIC,
                null,
                "10.0.0.8",
                "new-change",
                "new-change",
                "/data/.testagent/agent-opencode/.configdev/new-change",
                new UserId("usr_test_dev"),
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW.plusSeconds(10)));
        repository.saveWorktree(new AgentConfigWorktree(
                "agw_linux_8_published",
                AgentConfigScope.PUBLIC,
                null,
                "10.0.0.8",
                "published-change",
                "published-change",
                "/data/.testagent/agent-opencode/.configdev/published-change",
                new UserId("usr_test_dev"),
                AgentConfigWorktreeStatus.PUBLISHED,
                NOW,
                NOW.plusSeconds(20)));
        repository.saveWorktree(new AgentConfigWorktree(
                "agw_linux_9",
                AgentConfigScope.PUBLIC,
                null,
                "10.0.0.9",
                "other-server",
                "other-server",
                "/data/.testagent/agent-opencode/.configdev/other-server",
                new UserId("usr_test_dev"),
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW.plusSeconds(30)));
        repository.saveWorktree(new AgentConfigWorktree(
                "agw_workspace",
                AgentConfigScope.WORKSPACE,
                new WorkspaceId("wrk_agentcfg_mybatis"),
                "10.0.0.8",
                "workspace-change",
                "workspace-change",
                "/data/.testagent/agent-opencode/.configdev/workspace-change",
                new UserId("usr_test_dev"),
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW.plusSeconds(40)));

        assertThat(repository.findWorktrees(
                        AgentConfigScope.PUBLIC,
                        null,
                        null,
                        "10.0.0.8",
                        AgentConfigWorktreeStatus.ACTIVE))
                .extracting(AgentConfigWorktree::worktreeId)
                .containsExactly("agw_linux_8_new", "agw_linux_8_old");
    }

    @Test
    void missingPublicWorktreeCandidatesRequireActiveSuperAdminBindingAndDisappearAfterCreation() {
        insertSuperAdminOpencodeBinding();
        repository.saveWorktree(new AgentConfigWorktree(
                "agw_legacy_public",
                AgentConfigScope.PUBLIC,
                null,
                "10.0.0.8",
                "public-personal-20260717",
                "public-personal-20260717",
                "/data/.testagent/agent-opencode/.configdev/public-personal-20260717",
                new UserId("usr_test_dev"),
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));

        assertThat(repository.findMissingPublicWorktreeUsers("10.0.0.8", 50))
                .containsExactly(new UserId("usr_test_dev"));
        assertThat(repository.findMissingPublicWorktreeUsers("10.0.0.9", 50)).isEmpty();

        repository.saveWorktree(new AgentConfigWorktree(
                "agw_compensated_public",
                AgentConfigScope.PUBLIC,
                null,
                "10.0.0.8",
                "public-usr_test_dev",
                "public-usr_test_dev",
                "/data/.testagent/agent-opencode/.configdev/public-usr_test_dev",
                new UserId("usr_test_dev"),
                AgentConfigWorktreeStatus.ACTIVE,
                NOW,
                NOW));

        assertThat(repository.findMissingPublicWorktreeUsers("10.0.0.8", 50)).isEmpty();
    }

    @Test
    void operationSnapshotsArePersistedThroughMyBatisXmlMapper() {
        AgentConfigOperation operation = repository.saveOperation(new AgentConfigOperation(
                "aco_mybatis_1234567890",
                AgentConfigScope.WORKSPACE,
                new WorkspaceId("wrk_agentcfg_mybatis"),
                "publish",
                AgentConfigOperationStatus.RUNNING,
                AgentConfigOperationStep.PUSHING,
                null,
                null,
                "trace_agentcfg_mybatis",
                "main",
                null,
                NOW,
                NOW));
        repository.saveOperation(operation.succeeded("commit_mybatis", NOW.plusSeconds(1)));

        assertThat(repository.findOperation("aco_mybatis_1234567890"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.status()).isEqualTo(AgentConfigOperationStatus.SUCCEEDED);
                    assertThat(saved.commitHash()).isEqualTo("commit_mybatis");
                });
    }

    private void insertWorkspace() {
        JdbcClient.create(dataSource)
                .sql("""
                        insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at, linux_server_id)
                        values (:workspaceId, :name, :rootPath, :status, :traceId, :createdAt, :updatedAt, :linuxServerId)
                        """)
                .param("workspaceId", "wrk_agentcfg_mybatis")
                .param("name", "Agent Config MyBatis")
                .param("rootPath", "/tmp/agentcfg-mybatis")
                .param("status", "ACTIVE")
                .param("traceId", "trace_workspace_mybatis")
                .param("createdAt", Timestamp.from(NOW))
                .param("updatedAt", Timestamp.from(NOW))
                .param("linuxServerId", "10.0.0.8")
                .update();
    }

    /** 构造 ACTIVE 超管、进程和同服 binding，候选 SQL 不依赖生产 migration 造测试用户。 */
    private void insertSuperAdminOpencodeBinding() {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        insert into user_roles(user_id, dict_id, created_at)
                        select 'usr_test_dev', dict_id, :now
                        from dictionaries
                        where dict_key = 'ROLE' and dict_value = 'SUPER_ADMIN'
                        """)
                .param("now", Timestamp.from(NOW))
                .update();
        jdbc.sql("""
                        insert into linux_servers(
                            linux_server_id, name, status, capacity_summary_json,
                            last_heartbeat_at, trace_id, created_at, updated_at
                        ) values (
                            '10.0.0.8', 'server-8', 'ONLINE', '{}',
                            :now, 'trace_server_8', :now, :now
                        )
                        """)
                .param("now", Timestamp.from(NOW))
                .update();
        jdbc.sql("""
                        insert into opencode_containers(
                            container_id, linux_server_id, container_name, port_start, port_end,
                            max_processes, current_processes, status, last_heartbeat_at,
                            trace_id, created_at, updated_at
                        ) values (
                            'container-agentcfg', '10.0.0.8', 'agentcfg', 14096, 15095,
                            30, 1, 'ONLINE', :now,
                            'trace_container_agentcfg', :now, :now
                        )
                        """)
                .param("now", Timestamp.from(NOW))
                .update();
        jdbc.sql("""
                        insert into opencode_server_processes(
                            process_id, user_id, linux_server_id, container_id, port, pid, base_url,
                            status, session_path, config_path, started_at, last_health_check_at,
                            health_message, trace_id, created_at, updated_at
                        ) values (
                            'ocp_agentcfg', 'usr_test_dev', '10.0.0.8', 'container-agentcfg', 14123, 1234,
                            'http://10.0.0.8:14123', 'RUNNING', '/data/session', '/data/config',
                            :now, :now, 'ready', 'trace_process_agentcfg', :now, :now
                        )
                        """)
                .param("now", Timestamp.from(NOW))
                .update();
        jdbc.sql("""
                        insert into user_opencode_process_bindings(
                            user_id, agent_id, process_id, linux_server_id, port,
                            status, trace_id, created_at, updated_at
                        ) values (
                            'usr_test_dev', 'opencode', 'ocp_agentcfg', '10.0.0.8', 14123,
                            'ACTIVE', 'trace_binding_agentcfg', :now, :now
                        )
                        """)
                .param("now", Timestamp.from(NOW))
                .update();
    }

    /** 测试用户属于 fixture，不再依赖生产 migration 写入个人开发数据。 */
    private void insertUser() {
        JdbcClient.create(dataSource)
                .sql("""
                        insert into users(
                            user_id, unified_auth_id, username, password_hash,
                            status, created_at, updated_at
                        ) values (
                            'usr_test_dev', 'auth_agentcfg_mybatis', 'agentcfg-mybatis', 'hash',
                            'ACTIVE', :now, :now
                        )
                        """)
                .param("now", Timestamp.from(NOW))
                .update();
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
