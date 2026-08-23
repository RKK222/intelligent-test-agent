package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheck;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheckRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisWorkspaceGitAccessCheckRepository;
import com.enterprise.testagent.persistence.mybatis.WorkspaceGitAccessCheckMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 使用 .env.test 的真实 PostgreSQL 验证双端候选查询、约束和生产 upsert SQL。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyBatisWorkspaceGitAccessCheckPostgresqlIntegrationTest {

    private static final UserId USER_ID = new UserId("usr_workspace_git_access");
    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");

    private PGSimpleDataSource administrativeDataSource;
    private String schema;
    private WorkspaceGitAccessCheckRepository repository;

    @BeforeAll
    void setUp() throws Exception {
        Assumptions.assumeTrue(System.getenv("TEST_AGENT_TEST_DB_HOST") != null,
                "需要从仓库根目录 .env.test 导出真实 PostgreSQL 变量");
        administrativeDataSource = dataSource(null);
        schema = "test_workspace_git_access_" + UUID.randomUUID().toString().replace("-", "");
        JdbcClient.create(administrativeDataSource).sql("create schema " + schema).update();
        PGSimpleDataSource schemaDataSource = dataSource(schema);
        Flyway.configure()
                .dataSource(schemaDataSource)
                .defaultSchema(schema)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        insertFixtures(JdbcClient.create(schemaDataSource));
        repository = repository(schemaDataSource);
    }

    @AfterAll
    void tearDown() {
        if (administrativeDataSource != null && schema != null) {
            JdbcClient.create(administrativeDataSource)
                    .sql("drop schema if exists " + schema + " cascade")
                    .update();
        }
    }

    @Test
    void shouldPageBothRuntimeKindsAndUpsertUserLevelChecks() {
        assertThat(repository.findApplicationWorkspaceCandidatesAfter(null, null, 200))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.userId()).isEqualTo(USER_ID);
                    assertThat(candidate.applicationWorkspaceId()).isEqualTo("awp_git_access");
                    assertThat(candidate.versionId()).isEqualTo("awv_git_access");
                });
        assertThat(repository.findLocalWorkspaceCandidatesAfter(null, null, 200))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.workspaceId().value()).isEqualTo("wrk_local_git_access");
                    assertThat(candidate.clientInstanceId()).isEqualTo("lci_git_access");
                    assertThat(candidate.rootDigest()).isEqualTo("a".repeat(64));
                });

        repository.save(check(
                WorkspaceGitAccessCheck.TargetKind.APPLICATION_WORKSPACE,
                "awp_git_access",
                WorkspaceGitAccessCheck.Status.INACCESSIBLE,
                "REPOSITORY_PERMISSION_REQUIRED",
                "Git 仓库读取权限已失效"));
        repository.save(check(
                WorkspaceGitAccessCheck.TargetKind.LOCAL_WORKSPACE,
                "wrk_local_git_access",
                WorkspaceGitAccessCheck.Status.UNKNOWN,
                "NETWORK_UNAVAILABLE",
                "Git 远端网络暂不可用，权限状态待下次巡检确认"));
        repository.save(check(
                WorkspaceGitAccessCheck.TargetKind.APPLICATION_WORKSPACE,
                "awp_git_access",
                WorkspaceGitAccessCheck.Status.ACCESSIBLE,
                null,
                null));

        assertThat(repository.find(
                USER_ID,
                WorkspaceGitAccessCheck.TargetKind.APPLICATION_WORKSPACE,
                "awp_git_access"))
                .get()
                .extracting(WorkspaceGitAccessCheck::status)
                .isEqualTo(WorkspaceGitAccessCheck.Status.ACCESSIBLE);
        assertThat(repository.find(
                USER_ID,
                WorkspaceGitAccessCheck.TargetKind.LOCAL_WORKSPACE,
                "wrk_local_git_access"))
                .get()
                .satisfies(check -> {
                    assertThat(check.status()).isEqualTo(WorkspaceGitAccessCheck.Status.UNKNOWN);
                    assertThat(check.reason()).isEqualTo("NETWORK_UNAVAILABLE");
                });
    }

    private static WorkspaceGitAccessCheck check(
            WorkspaceGitAccessCheck.TargetKind kind,
            String targetId,
            WorkspaceGitAccessCheck.Status status,
            String reason,
            String message) {
        return new WorkspaceGitAccessCheck(USER_ID, kind, targetId, status, reason, message, NOW);
    }

    private static void insertFixtures(JdbcClient jdbc) {
        Timestamp now = Timestamp.from(NOW);
        jdbc.sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values (:userId, '99880001', 'Git巡检用户', 'hash', 'ACTIVE', :now, :now)
                        """)
                .param("userId", USER_ID.value()).param("now", now).update();
        jdbc.sql("""
                        insert into applications(app_id, app_name, enabled, created_at, updated_at)
                        values ('app_git_access', 'Git巡检应用', true, :now, :now)
                        """).param("now", now).update();
        jdbc.sql("""
                        insert into application_members(app_id, user_id, created_at, updated_at)
                        values ('app_git_access', :userId, :now, :now)
                        """).param("userId", USER_ID.value()).param("now", now).update();
        jdbc.sql("""
                        insert into code_repositories(
                            repository_id, git_url, name, standard, repository_type, created_at, updated_at)
                        values ('repo_git_access', 'ssh://git.example.test/team/repo.git', '巡检仓库', false,
                                'APPLICATION_CODE_REPOSITORY', :now, :now)
                        """).param("now", now).update();
        jdbc.sql("""
                        insert into application_workspaces(
                            workspace_id, app_id, repository_id, branch, directory_path,
                            workspace_name, enabled, created_at, updated_at)
                        values ('awp_git_access', 'app_git_access', 'repo_git_access', 'main', 'src',
                                '服务器巡检工作空间', true, :now, :now)
                        """).param("now", now).update();
        jdbc.sql("""
                        insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                        values ('wrk_version_git_access', '版本运行工作空间', '/server/version', 'ACTIVE', 'trace-test', :now, :now),
                               ('wrk_local_git_access', '本地运行工作空间', '/local/workspace', 'ACTIVE', 'trace-test', :now, :now)
                        """).param("now", now).update();
        jdbc.sql("""
                        insert into application_workspace_versions(
                            version_id, application_workspace_id, app_id, repository_id, version, branch,
                            repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
                            status, created_at, updated_at)
                        values ('awv_git_access', 'awp_git_access', 'app_git_access', 'repo_git_access', '20260823',
                                'main', '/server/repo', '/server/version', 'wrk_version_git_access', :userId,
                                'ACTIVE', :now, :now)
                        """).param("userId", USER_ID.value()).param("now", now).update();
        jdbc.sql("""
                        insert into local_client_instances(
                            client_instance_id, user_id, client_name, platform, architecture,
                            client_version, opencode_version, launcher_version, self_update_capabilities,
                            self_update_supported, created_at, updated_at, last_connected_at)
                        values ('lci_git_access', :userId, 'Git巡检客户端', 'macos', 'aarch64',
                                '20260823190000', '1.18.4', '1', 'WORKSPACE_GIT_ACCESS_V1', false, :now, :now, :now)
                        """).param("userId", USER_ID.value()).param("now", now).update();
        jdbc.sql("""
                        insert into local_client_workspaces(
                            workspace_id, user_id, client_instance_id, normalized_root_path,
                            root_digest, file_system_identity, created_at, updated_at)
                        values ('wrk_local_git_access', :userId, 'lci_git_access', '/local/workspace',
                                :rootDigest, 'fs-git-access', :now, :now)
                        """)
                .param("userId", USER_ID.value())
                .param("rootDigest", "a".repeat(64))
                .param("now", now)
                .update();
    }

    private static WorkspaceGitAccessCheckRepository repository(PGSimpleDataSource dataSource) throws Exception {
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
        WorkspaceGitAccessCheckMapper mapper =
                new SqlSessionTemplate(factory).getMapper(WorkspaceGitAccessCheckMapper.class);
        return new MyBatisWorkspaceGitAccessCheckRepository(mapper);
    }

    private PGSimpleDataSource dataSource(String currentSchema) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {required("TEST_AGENT_TEST_DB_HOST")});
        dataSource.setPortNumbers(new int[] {Integer.parseInt(required("TEST_AGENT_TEST_DB_PORT"))});
        dataSource.setDatabaseName(required("TEST_AGENT_TEST_DB_NAME"));
        dataSource.setUser(required("TEST_AGENT_TEST_DB_USERNAME"));
        dataSource.setPassword(required("TEST_AGENT_TEST_DB_PASSWORD"));
        if (currentSchema != null) {
            dataSource.setCurrentSchema(currentSchema);
        }
        return dataSource;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 未通过 .env.test 提供");
        }
        return value;
    }
}
