package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.session.SessionHistoryItem;
import com.icbc.testagent.domain.session.SessionHistoryRepository;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.persistence.mybatis.MyBatisSessionHistoryRepository;
import com.icbc.testagent.persistence.mybatis.SessionHistoryMapper;
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
 * 验证用户级历史会话查询只读链路使用 MyBatis XML，并能补齐应用/工作区/版本上下文。
 */
class MyBatisSessionHistoryRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-08T00:00:00Z");
    private static final UserId CURRENT_USER = new UserId("usr_history_current");

    private SingleConnectionDataSource dataSource;
    private SessionHistoryRepository repository;
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_session_history_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcClient = JdbcClient.create(dataSource);
        seedData();

        SqlSessionFactory sqlSessionFactory = sqlSessionFactory();
        SessionHistoryMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(SessionHistoryMapper.class);
        repository = new MyBatisSessionHistoryRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void userHistoryReturnsCurrentUserSessionsWithWorkspaceContextByUpdatedAtDesc() {
        PageResponse<SessionHistoryItem> page = repository.findUserHistory(CURRENT_USER, "", new PageRequest(1, 30));

        assertThat(page.total()).isEqualTo(4);
        assertThat(page.items())
                .extracting(item -> item.session().sessionId().value())
                .containsExactly(
                        "ses_history_created",
                        "ses_history_run",
                        "ses_history_message",
                        "ses_history_empty_context");

        assertThat(page.items().get(0).session().pinned()).isFalse();
        assertThat(page.items().get(0).workspaceContext()).satisfies(context -> {
            assertThat(context.appId()).isEqualTo("app_history");
            assertThat(context.appName()).isEqualTo("智能测试平台");
            assertThat(context.applicationWorkspaceId()).isEqualTo("aw_history_main");
            assertThat(context.workspaceName()).isEqualTo("主干工作区模板");
            assertThat(context.versionId()).isEqualTo("ver_history_main");
            assertThat(context.version()).isEqualTo("20260708");
        });
        assertThat(page.items().get(1).session().pinned()).isTrue();
        assertThat(page.items().get(1).workspaceContext().versionId()).isEqualTo("ver_history_replica");
        assertThat(page.items().get(2).workspaceContext()).satisfies(context -> {
            assertThat(context.appId()).isNull();
            assertThat(context.workspaceName()).isEqualTo("非托管工作区");
            assertThat(context.version()).isNull();
        });
        assertThat(page.items().get(3).workspaceContext()).isNull();
    }

    @Test
    void userHistorySearchesOnlyCurrentUserAttributedSessions() {
        PageResponse<SessionHistoryItem> page = repository.findUserHistory(
                CURRENT_USER,
                "消息",
                new PageRequest(1, 30));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items())
                .extracting(item -> item.session().sessionId().value())
                .containsExactly("ses_history_message");
    }

    @Test
    void userHistoryExcludesInternalSideQuestionSessions() {
        PageResponse<SessionHistoryItem> page = repository.findUserHistory(CURRENT_USER, "", new PageRequest(1, 30));

        assertThat(page.items())
                .extracting(item -> item.session().sessionId().value())
                .doesNotContain("ses_history_side_question", "ses_history_side_question_active");
    }

    private void seedData() {
        seedUsers();
        seedWorkspaces();
        seedApplicationContext();
        seedSessions();
    }

    private void seedUsers() {
        jdbcClient.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values
                    ('usr_history_current', 'auth_history_current', 'history_current', 'hash', 'ACTIVE', :now, :now),
                    ('usr_history_other', 'auth_history_other', 'history_other', 'hash', 'ACTIVE', :now, :now),
                    ('usr_history_creator', 'auth_history_creator', 'history_creator', 'hash', 'ACTIVE', :now, :now)
                """)
                .param("now", NOW)
                .update();
    }

    private void seedWorkspaces() {
        jdbcClient.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values
                    ('wrk_history_personal', 'personal runtime', '/tmp/personal', 'ACTIVE', 'trace_history', :now, :now),
                    ('wrk_history_version', 'version runtime', '/tmp/version', 'ACTIVE', 'trace_history', :now, :now),
                    ('wrk_history_replica', 'replica runtime', '/tmp/replica', 'ACTIVE', 'trace_history', :now, :now),
                    ('wrk_history_replica_base', 'replica base', '/tmp/replica-base', 'ACTIVE', 'trace_history', :now, :now),
                    ('wrk_history_unmanaged', '非托管工作区', '/tmp/unmanaged', 'ACTIVE', 'trace_history', :now, :now),
                    ('wrk_history_blank', '', '/tmp/blank', 'ACTIVE', 'trace_history', :now, :now),
                    ('wrk_history_other', 'other runtime', '/tmp/other', 'ACTIVE', 'trace_history', :now, :now)
                """)
                .param("now", NOW)
                .update();
    }

    private void seedApplicationContext() {
        jdbcClient.sql("""
                insert into applications(app_id, app_name, enabled, created_at, updated_at)
                values('app_history', '智能测试平台', true, :now, :now)
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into code_repositories(repository_id, git_url, name, standard, created_at, updated_at)
                values('repo_history', 'ssh://git/history.git', 'history', true, :now, :now)
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into application_workspaces(
                    workspace_id, app_id, repository_id, branch, directory_path, workspace_name, created_at, updated_at)
                values('aw_history_main', 'app_history', 'repo_history', 'main', '/', '主干工作区模板', :now, :now)
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into application_workspace_versions(
                    version_id, application_workspace_id, app_id, repository_id, version, branch,
                    repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
                    status, created_at, updated_at)
                values
                    ('ver_history_main', 'aw_history_main', 'app_history', 'repo_history', '20260708', 'main',
                     '/repo/main', '/repo/main', 'wrk_history_version', 'usr_history_creator', 'ACTIVE', :now, :now),
                    ('ver_history_replica', 'aw_history_main', 'app_history', 'repo_history', '20260707', 'main',
                     '/repo/replica', '/repo/replica', 'wrk_history_replica_base', 'usr_history_creator', 'ACTIVE', :now, :now)
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into personal_workspaces(
                    personal_workspace_id, app_workspace_version_id, app_id, application_workspace_id, user_id,
                    workspace_name, branch, repo_root_path, workspace_root_path, runtime_workspace_id,
                    base_commit, status, created_at, updated_at)
                values(
                    'pw_history_current', 'ver_history_main', 'app_history', 'aw_history_main', 'usr_history_current',
                    '个人副本', 'codex/current', '/repo/personal', '/repo/personal', 'wrk_history_personal',
                    'abc123', 'ACTIVE', :now, :now)
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into application_workspace_version_replicas(
                    replica_id, version_id, linux_server_id, repo_root_path, workspace_root_path,
                    runtime_workspace_id, sync_status, trace_id, created_at, updated_at)
                values(
                    'awr_history_replica', 'ver_history_replica', 'linux_1', '/repo/replica-node', '/repo/replica-node',
                    'wrk_history_replica', 'READY', 'trace_history', :now, :now)
                """)
                .param("now", NOW)
                .update();
    }

    private void seedSessions() {
        jdbcClient.sql("""
                insert into sessions(
                    session_id, workspace_id, title, status, trace_id, created_at, updated_at, pinned, created_by_user_id)
                values
                    ('ses_history_created', 'wrk_history_personal', '创建人历史', 'ACTIVE', 'trace_history',
                     :now, :updatedCreated, false, 'usr_history_current'),
                    ('ses_history_run', 'wrk_history_replica', 'Run 兜底历史', 'ACTIVE', 'trace_history',
                     :now, :updatedRun, true, null),
                    ('ses_history_message', 'wrk_history_unmanaged', '消息 兜底历史', 'ACTIVE', 'trace_history',
                     :now, :updatedMessage, false, null),
                    ('ses_history_empty_context', 'wrk_history_blank', '空上下文历史', 'ACTIVE', 'trace_history',
                     :now, :updatedEmpty, false, 'usr_history_current'),
                    ('ses_history_other', 'wrk_history_other', '其他用户历史', 'ACTIVE', 'trace_history',
                     :now, :updatedOther, false, 'usr_history_other'),
                    ('ses_history_unknown', 'wrk_history_other', '无归因历史', 'ACTIVE', 'trace_history',
                     :now, :updatedOther, false, null),
                    ('ses_history_archived', 'wrk_history_other', '已归档历史', 'ARCHIVED', 'trace_history',
                     :now, :updatedOther, false, 'usr_history_current'),
                    ('ses_history_side_question', 'wrk_history_other', '宠物旁路问答（内部）', 'ARCHIVED', 'trace_history',
                     :now, :updatedCreated, false, 'usr_history_current'),
                    ('ses_history_side_question_active', 'wrk_history_other', '异常未归档旁路会话', 'ACTIVE', 'trace_history',
                     :now, :updatedCreated, false, 'usr_history_current')
                """)
                .param("now", NOW)
                .param("updatedCreated", NOW.plusSeconds(50))
                .param("updatedRun", NOW.plusSeconds(40))
                .param("updatedMessage", NOW.plusSeconds(30))
                .param("updatedEmpty", NOW.plusSeconds(20))
                .param("updatedOther", NOW.plusSeconds(60))
                .update();
        jdbcClient.sql("""
                update sessions
                set source_type = 'SIDE_QUESTION', source_ref_id = 'ses_history_created'
                where session_id in ('ses_history_side_question', 'ses_history_side_question_active')
                """)
                .update();
        jdbcClient.sql("""
                insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at, triggered_by_user_id)
                values('run_history_current', 'ses_history_run', 'wrk_history_replica', 'SUCCEEDED',
                       'trace_history', :now, :now, 'usr_history_current')
                """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into session_messages(
                    message_id, session_id, role, content, trace_id, created_at, updated_at, sender_user_id)
                values('msg_history_current', 'ses_history_message', 'USER', 'message attribution',
                       'trace_history', :now, :now, 'usr_history_current')
                """)
                .param("now", NOW)
                .update();
    }

    /**
     * 直接构造 mapper，避免 Spring Boot 自动配置掩盖 XML namespace 或 resultMap 问题。
     */
    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
