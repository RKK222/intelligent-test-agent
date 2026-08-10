package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.sessionshare.SessionShare;
import com.enterprise.testagent.domain.sessionshare.SessionShareAccessStatus;
import com.enterprise.testagent.domain.sessionshare.SessionShareAuditEvent;
import com.enterprise.testagent.domain.sessionshare.SessionShareId;
import com.enterprise.testagent.domain.sessionshare.SessionShareMembership;
import com.enterprise.testagent.domain.sessionshare.SessionShareRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.persistence.mybatis.MyBatisSessionShareRepository;
import com.enterprise.testagent.persistence.mybatis.SessionShareMapper;
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

/** 使用 H2 PostgreSQL 模式验证分享链接、成员历史、乐观锁、候选人和审计 SQL。 */
class MyBatisSessionShareRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-09T08:00:00Z");
    private static final UserId OWNER = new UserId("usr_share_owner");
    private static final UserId MEMBER = new UserId("usr_share_member");
    private static final SessionId SESSION = new SessionId("ses_share_repository");
    private static final WorkspaceId WORKSPACE = new WorkspaceId("wrk_share_repository");

    private SingleConnectionDataSource dataSource;
    private SessionShareRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                ("jdbc:h2:mem:testagent_session_share_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=true;"
                        + "INIT=CREATE DOMAIN IF NOT EXISTS timestamptz AS TIMESTAMP WITH TIME ZONE")
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("20260715000000").load().migrate();
        new JdbcTemplate(dataSource).execute(
                "alter table session_messages add column sent_by_shared_user boolean not null default false");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260809170000__session_shares_create_collaboration_share.sql")).execute(dataSource);

        jdbc = new JdbcTemplate(dataSource);
        seedScope();
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        SessionShareMapper mapper = new SqlSessionTemplate(factory).getMapper(SessionShareMapper.class);
        repository = new MyBatisSessionShareRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void storesOneStableShareAndUpdatesWithVersionCompareAndSet() {
        SessionShare initial = share();
        repository.insert(initial);

        assertThat(repository.findBySessionId(SESSION)).contains(initial);
        assertThat(repository.findByShareId(initial.shareId())).contains(initial);

        SessionShare updated = initial.update(
                NOW.plus(Duration.ofDays(2)),
                List.of(SessionShareMembership.active(
                        MEMBER, "ucid_share_member", "分享成员", false, NOW.plusSeconds(10))),
                NOW.plusSeconds(10),
                "trace_share_update");
        assertThat(repository.update(updated, 0)).isTrue();
        assertThat(repository.update(updated, 0)).isFalse();
        assertThat(repository.findBySessionId(SESSION)).get().satisfies(saved -> {
            assertThat(saved.version()).isEqualTo(1);
            assertThat(saved.membership(MEMBER)).get().satisfies(member -> assertThat(member.canChat()).isFalse());
        });
    }

    @Test
    void listsActiveAndRemovedMembershipHistoryWithoutWorkspaceMembershipCheck() {
        SessionShare initial = share();
        repository.insert(initial);

        assertThat(repository.findSharedWith(MEMBER, new PageRequest(1, 20)).items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.status()).isEqualTo(SessionShareAccessStatus.ACTIVE);
                    assertThat(item.ownerUsername()).isEqualTo("会话所属人");
                    assertThat(item.sessionTitle()).isEqualTo("分享会话");
                    assertThat(item.canChat()).isTrue();
                });

        SessionShare removed = initial.update(
                NOW.plus(Duration.ofDays(2)), List.of(), NOW.plusSeconds(20), "trace_share_remove");
        assertThat(repository.update(removed, 0)).isTrue();
        assertThat(repository.findSharedWith(MEMBER, new PageRequest(1, 20)).items())
                .singleElement()
                .extracting(item -> item.status())
                .isEqualTo(SessionShareAccessStatus.REMOVED);
    }

    @Test
    void candidatesReturnOnlyOtherActiveUsersAndAuditNeverNeedsSensitiveBody() {
        assertThat(repository.findCandidates(OWNER, "成员", new PageRequest(1, 20)).items())
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.userId()).isEqualTo(MEMBER);
                    assertThat(candidate.unifiedAuthId()).isEqualTo("ucid_share_member");
                    assertThat(candidate.username()).isEqualTo("分享成员");
                });

        SessionShare initial = share();
        repository.insert(initial);
        repository.appendAudit(new SessionShareAuditEvent(
                "ssa_repository_event", initial.shareId(), SESSION, WORKSPACE,
                MEMBER, OWNER, "MESSAGE_READ", "SESSION", SESSION.value(), null,
                "SUCCESS", null, "trace_share_audit", NOW));

        assertThat(jdbc.queryForObject(
                "select count(*) from session_share_audit_events where audit_event_id='ssa_repository_event'",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void deletesOnlyAuditEventsOlderThanRetentionCutoff() {
        SessionShare initial = share();
        repository.insert(initial);
        repository.appendAudit(new SessionShareAuditEvent(
                "ssa_old_event", initial.shareId(), SESSION, WORKSPACE,
                MEMBER, OWNER, "MESSAGE_READ", "SESSION", SESSION.value(), null,
                "SUCCESS", null, "trace_old_audit", NOW.minus(Duration.ofDays(366))));
        repository.appendAudit(new SessionShareAuditEvent(
                "ssa_retained_event", initial.shareId(), SESSION, WORKSPACE,
                MEMBER, OWNER, "MESSAGE_READ", "SESSION", SESSION.value(), null,
                "SUCCESS", null, "trace_retained_audit", NOW.minus(Duration.ofDays(364))));

        assertThat(repository.deleteAuditEventsBefore(NOW.minus(Duration.ofDays(365))))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                "select audit_event_id from session_share_audit_events order by audit_event_id",
                String.class)).containsExactly("ssa_retained_event");
    }

    @Test
    void claimsLegacyOwnerOnlyWhenAllOrdinaryAttributionMatchesActor() {
        jdbc.update("insert into sessions(session_id,workspace_id,title,status,trace_id,created_at,updated_at) "
                        + "values(?,?,?,?,?,?,?)",
                "ses_share_legacy", WORKSPACE.value(), "旧会话", "ACTIVE", "trace_share", NOW, NOW);
        jdbc.update("insert into session_messages(message_id,session_id,role,content,trace_id,created_at,updated_at,"
                        + "sender_user_id,sent_by_shared_user) values(?,?,?,?,?,?,?,?,?)",
                "msg_share_legacy", "ses_share_legacy", "USER", "legacy", "trace_share", NOW, NOW,
                OWNER.value(), false);

        assertThat(repository.claimLegacySessionOwner(new SessionId("ses_share_legacy"), MEMBER)).isFalse();
        assertThat(repository.claimLegacySessionOwner(new SessionId("ses_share_legacy"), OWNER)).isTrue();
        assertThat(jdbc.queryForObject(
                "select created_by_user_id from sessions where session_id='ses_share_legacy'", String.class))
                .isEqualTo(OWNER.value());
    }

    private SessionShare share() {
        return SessionShare.create(
                new SessionShareId("shr_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
                SESSION, WORKSPACE, OWNER, NOW.plus(Duration.ofDays(1)),
                List.of(SessionShareMembership.active(
                        MEMBER, "ucid_share_member", "分享成员", true, NOW)),
                NOW, "trace_share_repository");
    }

    private void seedScope() {
        jdbc.update("insert into users(user_id,unified_auth_id,username,password_hash,status,created_at,updated_at) "
                        + "values(?,?,?,?,?,?,?),(?,?,?,?,?,?,?),(?,?,?,?,?,?,?)",
                OWNER.value(), "ucid_share_owner", "会话所属人", "hash", "ACTIVE", NOW, NOW,
                MEMBER.value(), "ucid_share_member", "分享成员", "hash", "ACTIVE", NOW, NOW,
                "usr_share_inactive", "ucid_share_inactive", "停用成员", "hash", "INACTIVE", NOW, NOW);
        jdbc.update("insert into workspaces(workspace_id,name,root_path,status,trace_id,created_at,updated_at) "
                        + "values(?,?,?,?,?,?,?)",
                WORKSPACE.value(), "分享工作区", "/tmp/share", "ACTIVE", "trace_share", NOW, NOW);
        jdbc.update("insert into sessions(session_id,workspace_id,title,status,trace_id,created_at,updated_at,created_by_user_id) "
                        + "values(?,?,?,?,?,?,?,?)",
                SESSION.value(), WORKSPACE.value(), "分享会话", "ACTIVE", "trace_share", NOW, NOW, OWNER.value());
    }
}
