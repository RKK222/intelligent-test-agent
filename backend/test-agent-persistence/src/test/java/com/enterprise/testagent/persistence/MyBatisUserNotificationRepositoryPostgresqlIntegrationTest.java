package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.notification.UserNotificationRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.MyBatisUserNotificationRepository;
import com.enterprise.testagent.persistence.mybatis.UserNotificationMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
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

/** 真实 PostgreSQL 覆盖空库与已部署分享基线升级，并核验审计回填和 PostgreSQL ON CONFLICT。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisUserNotificationRepositoryPostgresqlIntegrationTest {

    private static final String MIGRATION_VERSION = "20260810170000";
    private static final String PREVIOUS_VERSION = "20260809170001";
    private static final Instant NOW = Instant.parse("2026-08-10T09:00:00Z");
    private static final String SHARE_ID =
            "shr_1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            // 本机 Docker Desktop 冷启动可能超过 Testcontainers 默认窗口，业务断言不应受镜像解压/I/O 抖动影响。
            .withStartupTimeout(Duration.ofMinutes(3));

    @Test
    void migratesEmptyDatabaseAndBackfillsKnownShareBaseline() throws Exception {
        DataSource emptyDataSource = dataSource("notification_empty");
        migrate(emptyDataSource, "notification_empty", null);
        JdbcClient emptyJdbc = JdbcClient.create(emptyDataSource);
        assertThat(emptyJdbc.sql("select count(*) from user_notifications").query(Long.class).single())
                .isZero();
        assertThat(emptyJdbc.sql("""
                        select count(*) from flyway_schema_history
                        where version = :version and success = true
                        """)
                .param("version", MIGRATION_VERSION)
                .query(Long.class)
                .single()).isEqualTo(1L);

        DataSource upgradeDataSource = dataSource("notification_upgrade");
        migrate(upgradeDataSource, "notification_upgrade", PREVIOUS_VERSION);
        JdbcClient jdbc = JdbcClient.create(upgradeDataSource);
        seedShareBaseline(jdbc);
        migrate(upgradeDataSource, "notification_upgrade", null);

        UserNotificationRepository repository = repository(upgradeDataSource);
        var read = repository.findPage(
                new UserId("usr_notification_pg_read"), false,
                NOW.plusSeconds(3), new PageRequest(1, 20));
        var unread = repository.findPage(
                new UserId("usr_notification_pg_unread"), false,
                NOW.plusSeconds(3), new PageRequest(1, 20));
        var sameTimeAudit = repository.findPage(
                new UserId("usr_notification_pg_same_time"), false,
                NOW.plusSeconds(3), new PageRequest(1, 20));
        assertThat(read.items()).singleElement().satisfies(item -> {
            assertThat(item.readAt()).isEqualTo(NOW.plusSeconds(1));
            assertThat(item.unread()).isFalse();
        });
        assertThat(unread.items()).singleElement().satisfies(item -> {
            assertThat(item.readAt()).isNull();
            assertThat(item.unread()).isTrue();
            assertThat(item.actionAvailable()).isTrue();
        });
        assertThat(sameTimeAudit.items()).singleElement().satisfies(item -> {
            // 与授权同一时刻的审计不满足“shared_at 之后”，不能误判新授权代际已读。
            assertThat(item.readAt()).isNull();
            assertThat(item.unread()).isTrue();
        });
        assertThat(repository.countUnread(
                new UserId("usr_notification_pg_unread"), NOW.plusSeconds(3)))
                .isEqualTo(1L);
    }

    private static void seedShareBaseline(JdbcClient jdbc) {
        Timestamp now = Timestamp.from(NOW);
        // 回填 SQL 使用数据库 current_timestamp；到期时间必须相对真实执行时钟，避免固定夹具随日期失效。
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(Duration.ofDays(1)));
        jdbc.sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values
                          ('usr_notification_pg_owner', 'ucid_pg_owner', 'PostgreSQL所属人', 'hash', 'ACTIVE', :now, :now),
                          ('usr_notification_pg_read', 'ucid_pg_read', 'PostgreSQL已读成员', 'hash', 'ACTIVE', :now, :now),
                          ('usr_notification_pg_unread', 'ucid_pg_unread', 'PostgreSQL未读成员', 'hash', 'ACTIVE', :now, :now),
                          ('usr_notification_pg_same_time', 'ucid_pg_same_time', 'PostgreSQL同刻成员', 'hash', 'ACTIVE', :now, :now)
                        """)
                .param("now", now)
                .update();
        jdbc.sql("""
                        insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                        values ('wrk_notification_pg', '通知 PostgreSQL 工作区', '/tmp/notification-pg',
                                'ACTIVE', 'trace_notification_pg', :now, :now)
                        """)
                .param("now", now)
                .update();
        jdbc.sql("""
                        insert into sessions(session_id, workspace_id, title, status, trace_id,
                                             created_at, updated_at, created_by_user_id)
                        values ('ses_notification_pg', 'wrk_notification_pg', 'PostgreSQL 通知回填',
                                'ACTIVE', 'trace_notification_pg', :now, :now, 'usr_notification_pg_owner')
                        """)
                .param("now", now)
                .update();
        jdbc.sql("""
                        insert into session_shares(share_id, session_id, workspace_id, owner_user_id,
                                                   status, expires_at, lock_version, trace_id,
                                                   created_at, updated_at)
                        values (:shareId, 'ses_notification_pg', 'wrk_notification_pg',
                                'usr_notification_pg_owner', 'ACTIVE', :expiresAt, 0,
                                'trace_notification_pg', :now, :now)
                        """)
                .param("shareId", SHARE_ID)
                .param("expiresAt", expiresAt)
                .param("now", now)
                .update();
        jdbc.sql("""
                        insert into session_share_memberships(share_id, user_id, unified_auth_id, username,
                                                              can_chat, status, shared_at, updated_at)
                        values
                          (:shareId, 'usr_notification_pg_read', 'ucid_pg_read', 'PostgreSQL已读成员',
                           false, 'ACTIVE', :now, :now),
                          (:shareId, 'usr_notification_pg_unread', 'ucid_pg_unread', 'PostgreSQL未读成员',
                           true, 'ACTIVE', :now, :now),
                          (:shareId, 'usr_notification_pg_same_time', 'ucid_pg_same_time', 'PostgreSQL同刻成员',
                           false, 'ACTIVE', :now, :now)
                        """)
                .param("shareId", SHARE_ID)
                .param("now", now)
                .update();
        jdbc.sql("""
                        insert into session_share_audit_events(
                          audit_event_id, share_id, session_id, workspace_id, actor_user_id,
                          execution_owner_user_id, action, resource_type, resource_id,
                          outcome, trace_id, occurred_at)
                        values
                          ('ssa_notification_pg_read', :shareId, 'ses_notification_pg',
                           'wrk_notification_pg', 'usr_notification_pg_read',
                           'usr_notification_pg_owner', 'READ_ACCESS_GRANTED', 'SESSION',
                           'ses_notification_pg', 'SUCCESS', 'trace_notification_pg_read', :readAt),
                          ('ssa_notification_pg_same_time', :shareId, 'ses_notification_pg',
                           'wrk_notification_pg', 'usr_notification_pg_same_time',
                           'usr_notification_pg_owner', 'READ_ACCESS_GRANTED', 'SESSION',
                           'ses_notification_pg', 'SUCCESS', 'trace_notification_pg_same_time', :sharedAt)
                        """)
                .param("shareId", SHARE_ID)
                .param("readAt", Timestamp.from(NOW.plusSeconds(1)))
                .param("sharedAt", now)
                .update();
    }

    private static DataSource dataSource(String schema) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setCurrentSchema(schema);
        return dataSource;
    }

    private static void migrate(DataSource dataSource, String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static UserNotificationRepository repository(DataSource dataSource) throws Exception {
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
        return new MyBatisUserNotificationRepository(
                new SqlSessionTemplate(factory).getMapper(UserNotificationMapper.class));
    }
}
