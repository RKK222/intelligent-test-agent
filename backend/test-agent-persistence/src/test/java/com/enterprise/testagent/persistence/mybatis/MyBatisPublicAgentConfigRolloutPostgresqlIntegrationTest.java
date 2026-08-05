package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.AgentConfigRolloutScope;
import com.enterprise.testagent.domain.configuration.PublicAgentConfigRolloutTarget;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 用真实 PostgreSQL 验证公共纠错发布的原子替换和强制停止目标继承。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisPublicAgentConfigRolloutPostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-03T05:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static DataSource dataSource;
    private static JdbcClient jdbc;
    private static MyBatisPublicAgentConfigRolloutRepository repository;
    private static TransactionTemplate transaction;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource configured = new PGSimpleDataSource();
        configured.setURL(POSTGRES.getJdbcUrl());
        configured.setUser(POSTGRES.getUsername());
        configured.setPassword(POSTGRES.getPassword());
        dataSource = configured;
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = JdbcClient.create(dataSource);
        repository = repository(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void supersedeReplacesActiveGateAndMarksOnlyMatchingOldTargetForForcedStop() {
        insertStuckRollout();

        boolean replaced = transaction.execute(status -> repository.supersedePublicRollout(
                "acr_stuck",
                "acr_fixed",
                "feature_config",
                "commit_fixed",
                "commit_bad",
                false,
                "usr-admin",
                "linux-1",
                "trace-fix",
                "错误 Agent description 导致排空失败",
                List.of("linux-1", "linux-2"),
                NOW));

        assertThat(replaced).isTrue();
        assertThat(jdbc.sql("""
                        select status, superseded_by_rollout_id, supersede_reason
                        from public_agent_config_rollouts where rollout_id = 'acr_stuck'
                        """).query().singleRow())
                .containsEntry("status", "SUPERSEDED")
                .containsEntry("superseded_by_rollout_id", "acr_fixed")
                .containsEntry("supersede_reason", "错误 Agent description 导致排空失败");
        assertThat(jdbc.sql("""
                        select status, supersedes_rollout_id, supersede_reason
                        from public_agent_config_rollouts where rollout_id = 'acr_fixed'
                        """).query().singleRow())
                .containsEntry("status", "DRAINING")
                .containsEntry("supersedes_rollout_id", "acr_stuck")
                .containsEntry("supersede_reason", "错误 Agent description 导致排空失败");
        assertThat(jdbc.sql("""
                        select status, lease_token, last_error
                        from public_agent_config_rollout_targets where target_id = 'act_stuck'
                        """).query().singleRow())
                .containsEntry("status", "ABANDONED")
                .containsEntry("last_error", "ROLLOUT_SUPERSEDED")
                .containsEntry("lease_token", null);

        repository.addTarget(target("act_matching", "acr_fixed", 14102, 1234L, NOW.minusSeconds(60)), NOW);
        repository.addTarget(target("act_other", "acr_fixed", 14103, 5678L, NOW.minusSeconds(30)), NOW);

        assertThat(jdbc.sql("""
                        select target_id, force_stop
                        from public_agent_config_rollout_targets
                        where rollout_id = 'acr_fixed'
                        order by target_id
                        """).query().listOfRows())
                .containsExactly(
                        java.util.Map.of("target_id", "act_matching", "force_stop", true),
                        java.util.Map.of("target_id", "act_other", "force_stop", false));

        jdbc.sql("""
                        update public_agent_config_rollout_servers
                        set status = 'SYNCED', synced_at = :now, updated_at = :now
                        where rollout_id = 'acr_fixed'
                        """)
                .param("now", Timestamp.from(NOW))
                .update();
        List<PublicAgentConfigRolloutTarget> claimed = transaction.execute(status ->
                repository.claimTargets("linux-1", NOW, NOW.plusSeconds(60), 10));
        assertThat(claimed)
                .extracting(PublicAgentConfigRolloutTarget::targetId, PublicAgentConfigRolloutTarget::forceStop)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("act_matching", true),
                        org.assertj.core.groups.Tuple.tuple("act_other", false));
        assertThat(repository.findRolloutServerStatuses("acr_fixed"))
                .filteredOn(server -> "linux-1".equals(server.linuxServerId()))
                .singleElement()
                .satisfies(server -> assertThat(server.pendingTargets())
                        .extracting(
                                target -> target.username(),
                                target -> target.containerId(),
                                target -> target.forceStop())
                        .containsExactlyInAnyOrder(
                                org.assertj.core.groups.Tuple.tuple("卡住用户", "container-1", true),
                                org.assertj.core.groups.Tuple.tuple("卡住用户", "container-1", false)));
        assertThat(repository.findActiveRolloutId()).contains("acr_fixed");
    }

    private static void insertStuckRollout() {
        jdbc.sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values ('usr-stuck', 'AUTH-STUCK', '卡住用户', 'hash', 'ACTIVE', :now, :now)
                        """)
                .param("now", Timestamp.from(NOW.minusSeconds(180)))
                .update();
        jdbc.sql("""
                        insert into public_agent_config_rollouts(
                            rollout_id, config_scope, scope_key, branch, commit_hash, previous_commit_hash,
                            discard_shared_runtime_changes, initiated_by_user_id, initiated_linux_server_id,
                            status, trace_id, created_at, updated_at)
                        values ('acr_stuck', 'PUBLIC', null, 'feature_config', 'commit_bad', 'commit_before',
                            false, 'usr-admin', 'linux-1', 'DRAINING', 'trace-bad', :now, :now)
                        """)
                .param("now", Timestamp.from(NOW.minusSeconds(120)))
                .update();
        jdbc.sql("""
                        insert into public_agent_config_rollout_servers(
                            rollout_id, linux_server_id, status, retry_count, next_retry_at, created_at, updated_at)
                        values ('acr_stuck', 'linux-1', 'SYNCED', 0, :now, :now, :now)
                        """)
                .param("now", Timestamp.from(NOW.minusSeconds(90)))
                .update();
        jdbc.sql("""
                        insert into public_agent_config_rollout_targets(
                            target_id, rollout_id, user_id, linux_server_id, container_id, port,
                            process_pid, process_started_at, base_url, status, retry_count, next_retry_at,
                            lease_until, lease_token, created_at, updated_at)
                        values ('act_stuck', 'acr_stuck', 'usr-stuck', 'linux-1', 'container-1', 14102,
                            1234, :startedAt, 'http://127.0.0.1:14102', 'RETRY_WAIT', 15, :now,
                            null, null, :now, :now)
                        """)
                .param("startedAt", Timestamp.from(NOW.minusSeconds(60)))
                .param("now", Timestamp.from(NOW.minusSeconds(30)))
                .update();
    }

    private static PublicAgentConfigRolloutTarget target(
            String targetId,
            String rolloutId,
            int port,
            long pid,
            Instant startedAt) {
        return new PublicAgentConfigRolloutTarget(
                targetId,
                rolloutId,
                AgentConfigRolloutScope.PUBLIC,
                "usr-stuck",
                "linux-1",
                "container-1",
                port,
                pid,
                startedAt,
                "http://127.0.0.1:" + port,
                0,
                null,
                null,
                "trace-fix");
    }

    private static MyBatisPublicAgentConfigRolloutRepository repository(DataSource source) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(source);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        PublicAgentConfigRolloutMapper mapper = new SqlSessionTemplate(factory)
                .getMapper(PublicAgentConfigRolloutMapper.class);
        return new MyBatisPublicAgentConfigRolloutRepository(mapper);
    }
}
