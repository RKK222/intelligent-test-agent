package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationCandidate;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceRelocationStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
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

/** 用真实 PostgreSQL 验证完整迁移链、错配扫描、运行闸门和三表事务切换。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisPersonalWorkspaceRelocationPostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-04T04:00:00Z");
    private static final Timestamp NOW_TIMESTAMP = Timestamp.from(NOW);
    private static final String SHA = "a".repeat(64);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static JdbcClient jdbc;
    private static MyBatisPersonalWorkspaceRelocationRepository repository;
    private static TransactionTemplate transaction;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(dataSource);
        seedFacts();
        repository = repository(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void relocatesOnlyWithoutActiveRunAndSwitchesDatabaseAtomically() {
        var mismatches = repository.findMismatches("server-a", 10);
        assertThat(mismatches).singleElement().satisfies(candidate -> {
            assertThat(candidate.personalWorkspaceId().value()).isEqualTo("psw_relocation_pg");
            assertThat(candidate.targetLinuxServerId()).isEqualTo("server-b");
        });
        PersonalWorkspaceRelocationCandidate candidate = mismatches.getFirst();
        repository.discover(candidate, "pwr_relocation_pg", "trace_relocation", NOW);
        var claimed = repository.claim(
                "pwr_relocation_pg", "server-a:lease", NOW.plusSeconds(600), NOW).orElseThrow();
        assertThat(claimed.status()).isEqualTo(PersonalWorkspaceRelocationStatus.EXPORTING);
        assertThat(repository.markTransferring(
                claimed.relocationId(), "server-a:lease", SHA, 123L, NOW.plusSeconds(1))).isTrue();
        assertThat(repository.markApplying(
                claimed.relocationId(), SHA, 123L, NOW.plusSeconds(2))).isTrue();

        jdbc.sql("""
                update personal_workspaces
                set repo_root_path = 'source/personal-changed', updated_at = :now
                where personal_workspace_id = 'psw_relocation_pg'
                """).param("now", Timestamp.from(NOW.plusSeconds(3))).update();
        Boolean blockedByChangedPath = transaction.execute(status -> repository.completeTarget(
                claimed.relocationId(), "server-a", "server-b", "target/repo", "target/repo/app",
                "abc123", "trace_target", NOW.plusSeconds(3)));
        assertThat(blockedByChangedPath).isFalse();
        jdbc.sql("""
                update personal_workspaces
                set repo_root_path = 'source/personal', updated_at = :now
                where personal_workspace_id = 'psw_relocation_pg'
                """).param("now", Timestamp.from(NOW.plusSeconds(3))).update();

        insertActiveRun();
        Boolean blockedByRun = transaction.execute(status -> repository.completeTarget(
                claimed.relocationId(), "server-a", "server-b", "target/repo", "target/repo/app",
                "abc123", "trace_target", NOW.plusSeconds(3)));
        assertThat(blockedByRun).isFalse();
        jdbc.sql("update runs set status = 'FAILED' where run_id = 'run_relocation_active'").update();

        Boolean completed = transaction.execute(status -> repository.completeTarget(
                claimed.relocationId(), "server-a", "server-b", "target/repo", "target/repo/app",
                "abc123", "trace_target", NOW.plusSeconds(4)));
        assertThat(completed).isTrue();
        assertThat(jdbc.sql("select linux_server_id from workspaces where workspace_id = 'wrk_relocation_personal'")
                .query(String.class).single()).isEqualTo("server-b");
        assertThat(jdbc.sql("select root_path from workspaces where workspace_id = 'wrk_relocation_personal'")
                .query(String.class).single()).isEqualTo("target/repo/app");
        assertThat(jdbc.sql("select repo_root_path from personal_workspaces where personal_workspace_id = 'psw_relocation_pg'")
                .query(String.class).single()).isEqualTo("target/repo");
        assertThat(repository.findByRelocationId(claimed.relocationId()).orElseThrow().status())
                .isEqualTo(PersonalWorkspaceRelocationStatus.CLEANUP_PENDING);

        // 第一次搬迁待清理时即使 Agent 再换服务器，也不能覆盖原源端的清理凭据。
        jdbc.sql("""
                update user_opencode_process_bindings
                set linux_server_id = 'server-a', updated_at = :now
                where user_id = 'usr_relocation_pg' and agent_id = 'opencode'
                """).param("now", Timestamp.from(NOW.plusSeconds(5))).update();
        PersonalWorkspaceRelocationCandidate nextCandidate =
                repository.findMismatches("server-b", 10).getFirst();
        repository.discover(
                nextCandidate, "pwr_relocation_pg_next", "trace_relocation_next", NOW.plusSeconds(5));
        var cleanupPending = repository.findByRelocationId(claimed.relocationId()).orElseThrow();
        assertThat(cleanupPending.status()).isEqualTo(PersonalWorkspaceRelocationStatus.CLEANUP_PENDING);
        assertThat(cleanupPending.sourceLinuxServerId()).isEqualTo("server-a");
        assertThat(cleanupPending.targetLinuxServerId()).isEqualTo("server-b");

        // 清理瞬时失败后会清空租约并设置退避时间；退避到期后必须仍可由原源服务器重新认领。
        assertThat(repository.reschedule(
                claimed.relocationId(),
                "server-a:lease",
                claimed.attemptCount(),
                NOW.plusSeconds(66),
                "RELOCATION_INTERNAL_ERROR",
                "个人工作区自动搬迁失败，等待下一轮安全重试",
                NOW.plusSeconds(6))).isTrue();
        assertThat(repository.findClaimable("server-a", NOW.plusSeconds(65), 10)).isEmpty();
        assertThat(repository.findClaimable("server-a", NOW.plusSeconds(66), 10))
                .extracting(relocation -> relocation.relocationId())
                .containsExactly(claimed.relocationId());
        var cleanupClaimed = repository.claim(
                claimed.relocationId(),
                "server-a:cleanup-lease",
                NOW.plusSeconds(666),
                NOW.plusSeconds(66)).orElseThrow();
        assertThat(cleanupClaimed.status()).isEqualTo(PersonalWorkspaceRelocationStatus.CLEANUP_PENDING);
        assertThat(repository.completeCleanup(
                claimed.relocationId(), "server-a:cleanup-lease", NOW.plusSeconds(67))).isTrue();

        // 原源端清理完成后，下一次错配才能生成一条新的搬迁事实，且旧快照字段会被清空。
        repository.discover(
                nextCandidate, "pwr_relocation_pg_next", "trace_relocation_next", NOW.plusSeconds(68));
        var nextRelocation = repository.findByRelocationId("pwr_relocation_pg_next").orElseThrow();
        assertThat(nextRelocation.status()).isEqualTo(PersonalWorkspaceRelocationStatus.DISCOVERED);
        assertThat(nextRelocation.sourceLinuxServerId()).isEqualTo("server-b");
        assertThat(nextRelocation.targetLinuxServerId()).isEqualTo("server-a");
        assertThat(nextRelocation.snapshotSha256()).isNull();
        assertThat(nextRelocation.archiveSizeBytes()).isNull();
    }

    private static void seedFacts() {
        jdbc.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values('usr_relocation_pg', 'AUTH_RELOCATION_PG', 'relocation-pg', 'hash', 'ACTIVE', :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
        jdbc.sql("""
                insert into linux_servers(linux_server_id, name, status, capacity_summary_json,
                    last_heartbeat_at, trace_id, created_at, updated_at)
                values
                    ('server-a', 'source', 'READY', '{}', :now, 'trace_server_a', :now, :now),
                    ('server-b', 'target', 'READY', '{}', :now, 'trace_server_b', :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
        jdbc.sql("""
                insert into opencode_containers(container_id, linux_server_id, container_name, port_start, port_end,
                    max_processes, current_processes, status, last_heartbeat_at, trace_id, created_at, updated_at)
                values('ctr_relocation_pg', 'server-b', 'target-container', 14121, 14130,
                    10, 1, 'READY', :now, 'trace_container', :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
        jdbc.sql("""
                insert into opencode_server_processes(process_id, user_id, linux_server_id, container_id, port, pid,
                    base_url, status, session_path, config_path, started_at, last_health_check_at,
                    health_message, trace_id, created_at, updated_at)
                values('ocp_relocation_pg', 'usr_relocation_pg', 'server-b', 'ctr_relocation_pg', 14121, 1001,
                    'http://server-b:14121', 'RUNNING', '/session', '/config', :now, :now,
                    null, 'trace_process', :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
        jdbc.sql("""
                insert into user_opencode_process_bindings(user_id, agent_id, process_id, linux_server_id, port,
                    status, trace_id, created_at, updated_at)
                values('usr_relocation_pg', 'opencode', 'ocp_relocation_pg', 'server-b', 14121,
                    'ACTIVE', 'trace_binding', :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
        jdbc.sql("""
                insert into applications(app_id, app_name, enabled, created_at, updated_at)
                values('app_relocation_pg', 'Relocation App', true, :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
        jdbc.sql("""
                insert into code_repositories(repository_id, git_url, name, english_name, standard,
                    repository_type, deployment_mode, created_at, updated_at)
                values('repo_relocation_pg', 'ssh://git/relocation.git', 'relocation', 'relocation', false,
                    'APPLICATION_CODE_REPOSITORY', 'EXTERNAL', :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
        jdbc.sql("""
                insert into application_workspaces(workspace_id, app_id, repository_id, branch, directory_path,
                    workspace_name, enabled, created_at, updated_at)
                values('awp_relocation_pg', 'app_relocation_pg', 'repo_relocation_pg', 'main', 'app',
                    'Relocation Workspace', true, :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
        jdbc.sql("""
                insert into workspaces(workspace_id, name, root_path, status, linux_server_id,
                    trace_id, created_at, updated_at)
                values
                    ('wrk_relocation_version', 'version', 'source/version/app', 'ACTIVE', 'server-a',
                     'trace_version', :now, :now),
                    ('wrk_relocation_personal', 'personal', 'source/personal/app', 'ACTIVE', 'server-a',
                     'trace_personal', :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
        jdbc.sql("""
                insert into application_workspace_versions(version_id, application_workspace_id, app_id,
                    repository_id, version, branch, repo_root_path, workspace_root_path, runtime_workspace_id,
                    created_by_user_id, status, created_at, updated_at)
                values('awv_relocation_pg', 'awp_relocation_pg', 'app_relocation_pg', 'repo_relocation_pg',
                    '20260804', 'main', 'source/version', 'source/version/app', 'wrk_relocation_version',
                    'usr_relocation_pg', 'ACTIVE', :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
        jdbc.sql("""
                insert into personal_workspaces(personal_workspace_id, app_workspace_version_id, app_id,
                    application_workspace_id, user_id, workspace_name, branch, repo_root_path,
                    workspace_root_path, runtime_workspace_id, base_commit, status, created_at, updated_at)
                values('psw_relocation_pg', 'awv_relocation_pg', 'app_relocation_pg', 'awp_relocation_pg',
                    'usr_relocation_pg', 'default', 'personal-user', 'source/personal', 'source/personal/app',
                    'wrk_relocation_personal', 'abc123', 'ACTIVE', :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
    }

    private static void insertActiveRun() {
        jdbc.sql("""
                insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at)
                values('ses_relocation_active', 'wrk_relocation_personal', 'active', 'ACTIVE',
                    'trace_session', :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
        jdbc.sql("""
                insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at)
                values('run_relocation_active', 'ses_relocation_active', 'wrk_relocation_personal', 'RUNNING',
                    'trace_run', :now, :now)
                """).param("now", NOW_TIMESTAMP).update();
    }

    private static MyBatisPersonalWorkspaceRelocationRepository repository(DataSource dataSource) throws Exception {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        provider.setProperties(databaseIds);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setDatabaseIdProvider(provider);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        PersonalWorkspaceRelocationMapper mapper =
                new SqlSessionTemplate(factory).getMapper(PersonalWorkspaceRelocationMapper.class);
        return new MyBatisPersonalWorkspaceRelocationRepository(mapper);
    }
}
