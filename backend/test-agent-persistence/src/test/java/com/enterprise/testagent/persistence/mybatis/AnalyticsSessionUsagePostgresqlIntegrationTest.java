package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.analytics.AnalyticsModels;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 使用真实 PostgreSQL 锁定用户×会话发送次数统计口径：
 * LEGACY_FULL/REDIS_SUMMARY 互斥、来源排除、人员归属回退、未知用户与筛选分页。
 */
@Testcontainers(disabledWithoutDocker = true)
class AnalyticsSessionUsagePostgresqlIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    /** 表单时间用上海墙钟：2026-08-31 00:00 与 2026-09-11 00:00。 */
    private static final Instant RANGE_START = OffsetDateTime
            .of(2026, 8, 31, 0, 0, 0, 0, ZoneOffset.ofHours(8)).toInstant();
    private static final Instant RANGE_END = OffsetDateTime
            .of(2026, 9, 11, 0, 0, 0, 0, ZoneOffset.ofHours(8)).toInstant();

    private static JdbcClient jdbc;
    private static MyBatisAnalyticsSessionUsageRepository repository;

    @BeforeAll
    static void setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration", "classpath:db/migration-postgresql")
                .load()
                .migrate();
        jdbc = JdbcClient.create(dataSource);
        repository = repository(dataSource);
        seed();
    }

    @Test
    void groupsByUserAndSessionWithMutuallyExclusiveStorageModes() {
        PageResponse<AnalyticsModels.SessionUsageRow> page = repository.sessionMessageUsage(filter(null, null, null, null, 1, 20));

        assertThat(page.total()).isEqualTo(5);
        assertThat(page.items()).hasSize(5);

        AnalyticsModels.SessionUsageRow busiest = row(page, "usr_a", "ses_1");
        assertThat(busiest.userId()).isEqualTo("usr_a");
        assertThat(busiest.sessionId()).isEqualTo("ses_1");
        // 旧存储按 USER 消息 2 条(08-31、09-08)+1 条(09-09)+ 摘要锚点 1 条(09-10)=4
        assertThat(busiest.userMessageCount()).isEqualTo(4);
        assertThat(busiest.username()).isEqualTo("张三");
        assertThat(busiest.sessionTitle()).isEqualTo("正常会话");
        assertThat(busiest.firstMessageAt()).isEqualTo(shanghai(2026, 8, 31, 0, 0, 0));
        assertThat(busiest.lastMessageAt()).isEqualTo(shanghai(2026, 9, 10, 9, 0, 0));

        // 摘要 Run 只算锚点一次，且该 Run 的 USER 消息不再重复计入
        assertThat(row(page, "usr_a", "ses_3").userMessageCount()).isEqualTo(1);
        // 共享会话归属实际发送人
        assertThat(row(page, "usr_a", "ses_2").userMessageCount()).isEqualTo(1);
        // Run 执行人回退：消息无发送人时归 Run 的 triggered_by_user_id
        assertThat(row(page, "usr_c", "ses_1").userMessageCount()).isEqualTo(1);
        // 全空归属保留为未知用户，userId 为空
        AnalyticsModels.SessionUsageRow unknown = row(page, null, "ses_6");
        assertThat(unknown.userId()).isNull();
        assertThat(unknown.username()).isEqualTo("未知用户");
        assertThat(unknown.userMessageCount()).isEqualTo(1);
    }

    @Test
    void excludesScheduledSideQuestionAssistantAndNextDay() {
        PageResponse<AnalyticsModels.SessionUsageRow> page = repository.sessionMessageUsage(filter(null, null, null, null, 1, 20));

        // SCHEDULED_TASK 自动来源(run_5)、SIDE_QUESTION 会话(ses_4)、AI 回复(msg_11)、次日(msg_8) 全部排除
        assertThat(page.items()).noneMatch(item -> "ses_4".equals(item.sessionId()));
        assertThat(page.items()).noneMatch(item -> "ses_3".equals(item.sessionId()) && item.userMessageCount() > 1);
        assertThat(row(page, "usr_a", "ses_1").userMessageCount()).isEqualTo(4);
    }

    @Test
    void appliesOrganizationDepartmentAndKeywordFilters() {
        assertThat(repository.sessionMessageUsage(filter("分行", null, null, null, 1, 20)).total()).isEqualTo(1);
        assertThat(repository.sessionMessageUsage(filter(null, null, "质量部", null, 1, 20)).total()).isZero();
        assertThat(repository.sessionMessageUsage(filter(null, null, null, "李四", 1, 20)).total()).isEqualTo(1);
        assertThat(repository.sessionMessageUsage(filter(null, null, null, "AUTH_A", 1, 20)).total()).isEqualTo(3);
    }

    @Test
    void ordersByLatestActivityFirstAndPaginatesServerSide() {
        PageResponse<AnalyticsModels.SessionUsageRow> first = repository.sessionMessageUsage(filter(null, null, null, null, 1, 2));
        PageResponse<AnalyticsModels.SessionUsageRow> second = repository.sessionMessageUsage(filter(null, null, null, null, 2, 2));

        assertThat(first.total()).isEqualTo(5);
        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(2);
        // 最近发送优先：09-10 12:00 的未知用户会话排第一，09-10 10:00 的 usr_c 次之
        assertThat(first.items().get(0).sessionId()).isEqualTo("ses_6");
        assertThat(second.items().get(0).userId()).isEqualTo("usr_a");
    }

    @Test
    void summarizesPerUserAcrossSessions() {
        PageResponse<AnalyticsModels.SessionUsageSummaryRow> page =
                repository.sessionMessageSummary(filter(null, null, null, null, 1, 20));

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items().get(0).userId()).isEqualTo("usr_a");

        AnalyticsModels.SessionUsageSummaryRow usrA = page.items().stream()
                .filter(row -> "usr_a".equals(row.userId()))
                .findFirst()
                .orElseThrow();
        assertThat(usrA.sessionCount()).isEqualTo(3);
        assertThat(usrA.userMessageCount()).isEqualTo(6);
        assertThat(usrA.username()).isEqualTo("张三");
        assertThat(usrA.unifiedAuthId()).isEqualTo("AUTH_A");
        assertThat(usrA.organization()).isEqualTo("总行");
        assertThat(usrA.rdDepartment()).isEqualTo("研发一部");
        assertThat(usrA.department()).isEqualTo("平台部");
        assertThat(usrA.firstMessageAt()).isEqualTo(shanghai(2026, 8, 31, 0, 0, 0));
        assertThat(usrA.lastMessageAt()).isEqualTo(shanghai(2026, 9, 10, 9, 0, 0));

        assertThat(page.items()).anyMatch(row -> row.userId() == null && row.sessionCount() == 1);
        assertThat(page.items()).anyMatch(row -> "usr_c".equals(row.userId()) && row.userMessageCount() == 1);
    }

    private static AnalyticsModels.SessionUsageRow row(
            PageResponse<AnalyticsModels.SessionUsageRow> page,
            String userId,
            String sessionId) {
        return page.items().stream()
                .filter(item -> sessionId.equals(item.sessionId())
                        && (userId == null ? item.userId() == null : userId.equals(item.userId())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少分组 " + userId + "/" + sessionId));
    }

    private static AnalyticsModels.Filter filter(
            String organization,
            String rdDepartment,
            String department,
            String userKeyword,
            int page,
            int pageSize) {
        return new AnalyticsModels.Filter(
                RANGE_START,
                RANGE_END,
                AnalyticsModels.Granularity.DAY,
                organization,
                rdDepartment,
                department,
                userKeyword,
                null,
                null,
                null,
                100,
                page,
                pageSize,
                "active");
    }

    private static Instant shanghai(int year, int month, int day, int hour, int minute, int second) {
        return OffsetDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.ofHours(8)).toInstant();
    }

    private static MyBatisAnalyticsSessionUsageRepository repository(PGSimpleDataSource dataSource) {
        try {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mybatis/AnalyticsSessionUsageMapper.xml"));
            SqlSessionFactory sqlSessionFactory = factory.getObject();
            return new MyBatisAnalyticsSessionUsageRepository(
                    new SqlSessionTemplate(sqlSessionFactory).getMapper(AnalyticsSessionUsageMapper.class));
        } catch (Exception exception) {
            throw new IllegalStateException("初始化会话发送统计 mapper 失败", exception);
        }
    }

    private static void seed() {
        jdbc.sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash,
                                          organization, rd_department, department, status, created_at, updated_at)
                        values ('usr_a', 'AUTH_A', '张三', 'hash', '总行', '研发一部', '平台部', 'ACTIVE', now(), now()),
                               ('usr_b', 'AUTH_B', '王五', 'hash', '总行', '研发一部', '质量部', 'ACTIVE', now(), now()),
                               ('usr_c', 'AUTH_C', '李四', 'hash', '分行', '研发二部', '业务组', 'ACTIVE', now(), now())
                        """).update();
        jdbc.sql("""
                        insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                        values ('wrk_ses', 'ses', '/tmp/ses', 'ACTIVE', 'trace_ses', now(), now())
                        """).update();
        jdbc.sql("""
                        insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at,
                                             created_by_user_id, source_type)
                        values ('ses_1', 'wrk_ses', '正常会话', 'ACTIVE', 'trace_ses', now(), now(), 'usr_a', 'MANUAL'),
                               ('ses_2', 'wrk_ses', '共享会话', 'ACTIVE', 'trace_ses', now(), now(), 'usr_b', 'MANUAL'),
                               ('ses_3', 'wrk_ses', '定时会话', 'ACTIVE', 'trace_ses', now(), now(), 'usr_a', 'SCHEDULED_TASK'),
                               ('ses_4', 'wrk_ses', '内部会话', 'ACTIVE', 'trace_ses', now(), now(), 'usr_a', 'SIDE_QUESTION'),
                               ('ses_6', 'wrk_ses', '未知来源会话', 'ACTIVE', 'trace_ses', now(), now(), null, 'MANUAL')
                        """).update();
        jdbc.sql("""
                        insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at,
                                         storage_mode, source_type, message_sender_user_id, triggered_by_user_id)
                        values ('run_1', 'ses_1', 'wrk_ses', 'SUCCEEDED', 'trace_ses', timestamp '2026-08-31 00:00:00', timestamp '2026-08-31 00:00:01', 'LEGACY_FULL', 'MANUAL', 'usr_a', 'usr_a'),
                               ('run_2', 'ses_1', 'wrk_ses', 'SUCCEEDED', 'trace_ses', timestamp '2026-09-08 23:59:59.999999', timestamp '2026-09-09 00:00:01', 'LEGACY_FULL', 'MANUAL', 'usr_a', 'usr_a'),
                               ('run_3', 'ses_2', 'wrk_ses', 'SUCCEEDED', 'trace_ses', timestamp '2026-09-09 00:00:00', timestamp '2026-09-09 00:00:01', 'LEGACY_FULL', 'MANUAL', 'usr_a', 'usr_b'),
                               ('run_4', 'ses_3', 'wrk_ses', 'SUCCEEDED', 'trace_ses', timestamp '2026-09-09 10:00:00', timestamp '2026-09-09 10:00:01', 'REDIS_SUMMARY', 'MANUAL', 'usr_a', 'usr_a'),
                               ('run_5', 'ses_3', 'wrk_ses', 'SUCCEEDED', 'trace_ses', timestamp '2026-09-09 11:00:00', timestamp '2026-09-09 11:00:01', 'REDIS_SUMMARY', 'SCHEDULED_TASK', 'usr_a', 'usr_a'),
                               ('run_6', 'ses_1', 'wrk_ses', 'SUCCEEDED', 'trace_ses', timestamp '2026-09-10 09:00:00', timestamp '2026-09-10 09:00:01', 'REDIS_SUMMARY', 'MANUAL', 'usr_a', 'usr_a'),
                               ('run_7', 'ses_1', 'wrk_ses', 'SUCCEEDED', 'trace_ses', timestamp '2026-09-10 10:00:00', timestamp '2026-09-10 10:00:01', 'LEGACY_FULL', 'MANUAL', null, 'usr_c'),
                               ('run_8', 'ses_6', 'wrk_ses', 'SUCCEEDED', 'trace_ses', timestamp '2026-09-10 12:00:00', timestamp '2026-09-10 12:00:01', 'LEGACY_FULL', 'MANUAL', null, null)
                        """).update();
        jdbc.sql("""
                        insert into session_messages(message_id, session_id, role, content, trace_id, created_at,
                                                     run_id, sender_user_id, source_type)
                        values ('msg_1', 'ses_1', 'USER', 'p1', 'trace_ses', timestamp '2026-08-31 00:00:00.000000', 'run_1', 'usr_a', 'MANUAL'),
                               ('msg_2', 'ses_1', 'USER', 'p1', 'trace_ses', timestamp '2026-09-08 23:59:59.999999', 'run_2', 'usr_a', 'MANUAL'),
                               ('msg_3', 'ses_2', 'USER', 'p2', 'trace_ses', timestamp '2026-09-09 00:00:00', 'run_3', 'usr_a', 'MANUAL'),
                               ('msg_4', 'ses_3', 'USER', 'p2', 'trace_ses', timestamp '2026-09-09 10:00:00', 'run_4', 'usr_a', 'MANUAL'),
                               ('msg_5', 'ses_3', 'USER', 'p2', 'trace_ses', timestamp '2026-09-09 11:00:00', 'run_5', 'usr_a', 'SCHEDULED_TASK'),
                               ('msg_6', 'ses_4', 'USER', 'p2', 'trace_ses', timestamp '2026-09-10 08:00:00', null, 'usr_a', 'MANUAL'),
                               ('msg_7', 'ses_6', 'USER', 'p2', 'trace_ses', timestamp '2026-09-10 12:00:00', 'run_8', null, 'MANUAL'),
                               ('msg_8', 'ses_1', 'USER', 'p2', 'trace_ses', timestamp '2026-09-11 00:00:00', 'run_1', 'usr_a', 'MANUAL'),
                               ('msg_9', 'ses_1', 'USER', 'p2', 'trace_ses', timestamp '2026-09-10 10:00:00', 'run_7', null, 'MANUAL'),
                               ('msg_10', 'ses_1', 'USER', 'p2', 'trace_ses', timestamp '2026-09-09 05:00:00', null, 'usr_a', 'MANUAL'),
                               ('msg_11', 'ses_1', 'ASSISTANT', 'p2', 'trace_ses', timestamp '2026-09-09 06:00:00', 'run_1', 'usr_a', 'MANUAL')
                        """).update();
    }
}
