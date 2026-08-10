package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.notification.UserNotificationActionType;
import com.enterprise.testagent.domain.notification.UserNotificationRepository;
import com.enterprise.testagent.domain.notification.UserNotificationStatus;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShare;
import com.enterprise.testagent.domain.sessionshare.SessionShareAuditEvent;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.sessionshare.SessionShareMembership;
import com.enterprise.testagent.domain.sessionshare.SessionShareRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.persistence.mybatis.MyBatisSessionShareRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisUserNotificationRepository;
import com.enterprise.testagent.persistence.mybatis.SessionShareMapper;
import com.enterprise.testagent.persistence.mybatis.UserNotificationMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 使用 H2 PostgreSQL 模式验证通知 MyBatis SQL、有效状态派生及历史分享审计回填。 */
class MyBatisUserNotificationRepositoryIntegrationTest {

    private static final UserId OWNER = new UserId("usr_notification_owner");
    private static final UserId READ_MEMBER = new UserId("usr_notification_read");
    private static final UserId UNREAD_MEMBER = new UserId("usr_notification_unread");
    private static final SessionId SESSION = new SessionId("ses_notification_repository");
    private static final WorkspaceId WORKSPACE = new WorkspaceId("wrk_notification_repository");
    private static final SessionShareId SHARE = new SessionShareId(
            "shr_abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private UserNotificationRepository repository;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        now = Instant.now();
        dataSource = new SingleConnectionDataSource(
                ("jdbc:h2:mem:testagent_notification_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=true;"
                        + "INIT=CREATE DOMAIN IF NOT EXISTS timestamptz AS TIMESTAMP WITH TIME ZONE")
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("20260715000000").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("alter table session_messages add column sent_by_shared_user boolean not null default false");
        executeMigration("db/migration/V20260809170000__session_shares_create_collaboration_share.sql");
        seedScope();

        SqlSessionFactory factory = sqlSessionFactory();
        SqlSessionTemplate template = new SqlSessionTemplate(factory);
        SessionShareRepository shareRepository = new MyBatisSessionShareRepository(
                template.getMapper(SessionShareMapper.class));
        SessionShare share = SessionShare.create(
                SHARE, SESSION, WORKSPACE, OWNER, now.plus(Duration.ofDays(1)),
                List.of(
                        SessionShareMembership.active(
                                READ_MEMBER, "ucid_notification_read", "已读成员", false, now),
                        SessionShareMembership.active(
                                UNREAD_MEMBER, "ucid_notification_unread", "未读成员", true, now)),
                now, "trace_notification_share");
        shareRepository.insert(share);
        shareRepository.appendAudit(new SessionShareAuditEvent(
                "ssa_notification_read", SHARE, SESSION, WORKSPACE,
                READ_MEMBER, OWNER, "READ_ACCESS_GRANTED", "SESSION", SESSION.value(), null,
                "SUCCESS", null, "trace_notification_read", now.plusMillis(1)));

        executeMigration("db/migration/V20260810170000__user_notifications_create_notification_center.sql");
        repository = new MyBatisUserNotificationRepository(
                template.getMapper(UserNotificationMapper.class));
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void backfillsOnlyCurrentMembershipGenerationAndUsesReadAccessAudit() {
        var readPage = repository.findPage(READ_MEMBER, false, now.plusSeconds(1), new PageRequest(1, 20));
        var unreadPage = repository.findPage(UNREAD_MEMBER, false, now.plusSeconds(1), new PageRequest(1, 20));

        assertThat(readPage.items()).singleElement().satisfies(notification -> {
            assertThat(notification.readAt()).isNotNull();
            assertThat(notification.unread()).isFalse();
            assertThat(notification.actionAvailable()).isTrue();
            assertThat(notification.body()).isEqualTo("通知回填会话 · 只读");
        });
        assertThat(unreadPage.items()).singleElement().satisfies(notification -> {
            assertThat(notification.readAt()).isNull();
            assertThat(notification.unread()).isTrue();
            assertThat(notification.body()).isEqualTo("通知回填会话 · 可对话");
        });
        assertThat(repository.countUnread(READ_MEMBER, now.plusSeconds(1))).isZero();
        assertThat(repository.countUnread(UNREAD_MEMBER, now.plusSeconds(1))).isEqualTo(1L);
        assertThat(repository.findPage(UNREAD_MEMBER, true, now.plusSeconds(1), new PageRequest(1, 20)).total())
                .isEqualTo(1L);
    }

    @Test
    void marksReadIdempotentlyAndDerivesInvalidStateFromShareAndSessionFacts() {
        assertThat(repository.markReadByAction(
                UNREAD_MEMBER,
                UserNotificationActionType.SESSION_SHARE,
                SHARE.value(),
                now.plusSeconds(2),
                "trace_notification_mark_read")).isTrue();
        assertThat(repository.markReadByAction(
                UNREAD_MEMBER,
                UserNotificationActionType.SESSION_SHARE,
                SHARE.value(),
                now.plusSeconds(3),
                "trace_notification_mark_read_again")).isFalse();

        jdbc.update("update sessions set status='ARCHIVED', updated_at=? where session_id=?",
                now.plusSeconds(4), SESSION.value());
        assertThat(repository.findPage(
                UNREAD_MEMBER, false, now.plusSeconds(5), new PageRequest(1, 20)).items())
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.status()).isEqualTo(UserNotificationStatus.INVALIDATED);
                    assertThat(notification.actionAvailable()).isFalse();
                    assertThat(notification.invalidationReason()).isEqualTo("SESSION_ARCHIVED");
                    assertThat(notification.unread()).isFalse();
                });
    }

    @Test
    void invalidatesByActionAndDeletesOnlyRowsBeforeRetentionCutoff() {
        assertThat(repository.findActiveRecipientsByAction(
                UserNotificationActionType.SESSION_SHARE, SHARE.value(), null))
                .containsExactlyInAnyOrder(READ_MEMBER, UNREAD_MEMBER);
        assertThat(repository.invalidateActiveByAction(
                UserNotificationActionType.SESSION_SHARE,
                SHARE.value(),
                UNREAD_MEMBER,
                "REMOVED",
                "trace_notification_remove",
                now.plusSeconds(2))).isEqualTo(1);
        assertThat(repository.findActiveRecipientsByAction(
                UserNotificationActionType.SESSION_SHARE, SHARE.value(), null))
                .containsExactly(READ_MEMBER);

        jdbc.update("update user_notifications set created_at=? where recipient_user_id=?",
                now.minus(Duration.ofDays(91)), READ_MEMBER.value());
        assertThat(repository.deleteCreatedBefore(now.minus(Duration.ofDays(90)))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from user_notifications", Long.class)).isEqualTo(1L);
    }

    private void seedScope() {
        jdbc.update("insert into users(user_id,unified_auth_id,username,password_hash,status,created_at,updated_at) "
                        + "values(?,?,?,?,?,?,?),(?,?,?,?,?,?,?),(?,?,?,?,?,?,?)",
                OWNER.value(), "ucid_notification_owner", "会话所属人", "hash", "ACTIVE", now, now,
                READ_MEMBER.value(), "ucid_notification_read", "已读成员", "hash", "ACTIVE", now, now,
                UNREAD_MEMBER.value(), "ucid_notification_unread", "未读成员", "hash", "ACTIVE", now, now);
        jdbc.update("insert into workspaces(workspace_id,name,root_path,status,trace_id,created_at,updated_at) "
                        + "values(?,?,?,?,?,?,?)",
                WORKSPACE.value(), "通知工作区", "/tmp/notification", "ACTIVE", "trace_notification", now, now);
        jdbc.update("insert into sessions(session_id,workspace_id,title,status,trace_id,created_at,updated_at,created_by_user_id) "
                        + "values(?,?,?,?,?,?,?,?)",
                SESSION.value(), WORKSPACE.value(), "通知回填会话", "ACTIVE",
                "trace_notification", now, now, OWNER.value());
    }

    private void executeMigration(String classpathLocation) {
        new ResourceDatabasePopulator(new ClassPathResource(classpathLocation)).execute(dataSource);
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
