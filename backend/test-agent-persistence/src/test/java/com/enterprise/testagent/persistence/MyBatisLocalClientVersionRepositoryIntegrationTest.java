package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.persistence.mybatis.LocalClientVersionMapper;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 验证 deadline 失败只允许收敛到迟到物理终态，并可重新汇总已结束 rollout。 */
class MyBatisLocalClientVersionRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;
    private LocalClientVersionMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_local_client_version_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        create table local_client_update_rollouts (
                            rollout_id varchar(128) primary key,
                            rollout_scope varchar(16) not null,
                            requested_user_id varchar(128),
                            status varchar(32) not null,
                            created_by varchar(128) not null,
                            created_at timestamp with time zone not null,
                            completed_at timestamp with time zone
                        )
                        """).update();
        jdbc.sql("""
                        create table local_client_update_attempts (
                            command_id varchar(128) primary key,
                            rollout_id varchar(128) not null,
                            client_instance_id varchar(128) not null,
                            user_id varchar(128) not null,
                            connection_generation bigint not null,
                            policy_revision bigint not null,
                            current_version varchar(64) not null,
                            target_version varchar(64) not null,
                            direction varchar(16) not null,
                            status varchar(32) not null,
                            release_digest varchar(64),
                            error_code varchar(128),
                            created_at timestamp with time zone not null,
                            updated_at timestamp with time zone not null,
                            completed_at timestamp with time zone
                        )
                        """).update();

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mybatis/LocalClientVersionMapper.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        mapper = new SqlSessionTemplate(sessionFactory).getMapper(LocalClientVersionMapper.class);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void deadlineFailureCanConvergeToLateSuccessAndRecomputeCompletedRollout() {
        insertRollout("lcrl_deadline_success", "PARTIAL_FAILED");
        insertAttempt("lcuc_deadline_success", "lcrl_deadline_success", "DELIVERY_DEADLINE_EXCEEDED");

        assertThat(mapper.findRolloutForUpdate("lcrl_deadline_success"))
                .extracting(row -> row.rolloutId(), row -> row.status())
                .containsExactly("lcrl_deadline_success", "PARTIAL_FAILED");

        assertThat(mapper.transitionAttempt(
                "lcuc_deadline_success",
                "FAILED",
                "SUCCEEDED",
                true,
                "a".repeat(64),
                null,
                NOW)).isEqualTo(1);
        assertThat(jdbc.sql("select status from local_client_update_attempts where command_id=:commandId")
                .param("commandId", "lcuc_deadline_success")
                .query(String.class).single()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.sql("select error_code from local_client_update_attempts where command_id=:commandId")
                .param("commandId", "lcuc_deadline_success")
                .query(String.class).optional()).isEmpty();

        assertThat(mapper.completeRollout("lcrl_deadline_success", "COMPLETED", NOW)).isEqualTo(1);
        assertThat(jdbc.sql("select status from local_client_update_rollouts where rollout_id=:rolloutId")
                .param("rolloutId", "lcrl_deadline_success")
                .query(String.class).single()).isEqualTo("COMPLETED");
    }

    @Test
    void ordinaryFailureCannotBeRewrittenAsLateSuccess() {
        insertRollout("lcrl_ordinary_failure", "PARTIAL_FAILED");
        insertAttempt("lcuc_ordinary_failure", "lcrl_ordinary_failure", "VERIFY_FAILED");

        assertThat(mapper.transitionAttempt(
                "lcuc_ordinary_failure",
                "FAILED",
                "SUCCEEDED",
                true,
                "a".repeat(64),
                null,
                NOW)).isZero();
        assertThat(jdbc.sql("select status from local_client_update_attempts where command_id=:commandId")
                .param("commandId", "lcuc_ordinary_failure")
                .query(String.class).single()).isEqualTo("FAILED");
        assertThat(jdbc.sql("select error_code from local_client_update_attempts where command_id=:commandId")
                .param("commandId", "lcuc_ordinary_failure")
                .query(String.class).single()).isEqualTo("VERIFY_FAILED");
    }

    private void insertRollout(String rolloutId, String status) {
        jdbc.sql("""
                        insert into local_client_update_rollouts(
                            rollout_id, rollout_scope, requested_user_id, status,
                            created_by, created_at, completed_at
                        ) values (
                            :rolloutId, 'ALL_ONLINE', null, :status,
                            'usr_local_version_admin', :createdAt, :completedAt
                        )
                        """)
                .param("rolloutId", rolloutId)
                .param("status", status)
                .param("createdAt", NOW.minusSeconds(3600))
                .param("completedAt", NOW.minusSeconds(60))
                .update();
    }

    private void insertAttempt(String commandId, String rolloutId, String errorCode) {
        jdbc.sql("""
                        insert into local_client_update_attempts(
                            command_id, rollout_id, client_instance_id, user_id,
                            connection_generation, policy_revision, current_version, target_version,
                            direction, status, release_digest, error_code,
                            created_at, updated_at, completed_at
                        ) values (
                            :commandId, :rolloutId, 'lci_local_version', 'usr_local_version',
                            7, 11, '20260820180000', '20260820190000',
                            'UPDATE', 'FAILED', :releaseDigest, :errorCode,
                            :createdAt, :updatedAt, :completedAt
                        )
                        """)
                .param("commandId", commandId)
                .param("rolloutId", rolloutId)
                .param("releaseDigest", "a".repeat(64))
                .param("errorCode", errorCode)
                .param("createdAt", NOW.minusSeconds(3600))
                .param("updatedAt", NOW.minusSeconds(60))
                .param("completedAt", NOW.minusSeconds(60))
                .update();
    }
}
