package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.configuration.AgentConfigOperation;
import com.icbc.testagent.domain.configuration.AgentConfigOperationStatus;
import com.icbc.testagent.domain.configuration.AgentConfigOperationStep;
import com.icbc.testagent.domain.configuration.AgentConfigRepository;
import com.icbc.testagent.domain.configuration.AgentConfigScope;
import com.icbc.testagent.domain.configuration.AgentConfigWorktree;
import com.icbc.testagent.domain.configuration.AgentConfigWorktreeStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import com.icbc.testagent.persistence.mybatis.AgentConfigMapper;
import com.icbc.testagent.persistence.mybatis.MyBatisAgentConfigRepository;
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
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
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

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
