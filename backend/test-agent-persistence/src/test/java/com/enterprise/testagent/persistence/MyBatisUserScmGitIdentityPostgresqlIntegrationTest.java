package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserScmGitIdentity;
import com.enterprise.testagent.domain.user.UserScmGitIdentityCandidate;
import com.enterprise.testagent.domain.user.UserScmGitIdentityRepository;
import com.enterprise.testagent.domain.user.UserScmGitIdentitySource;
import com.enterprise.testagent.persistence.mybatis.MyBatisUserScmGitIdentityRepository;
import com.enterprise.testagent.persistence.mybatis.UserScmGitIdentityMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用真实 PostgreSQL 验证新增 migration、游标分页和两类证据优先级。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisUserScmGitIdentityPostgresqlIntegrationTest {

    private static final UserId USER_ID = new UserId("usr_scm_git_identity");
    private static final Instant FIRST = Instant.parse("2026-08-13T10:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static UserScmGitIdentityRepository repository;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260812204207")
                .load()
                .migrate();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values (:userId, '123456789', '测试用户1', 'hash', 'ACTIVE', :now, :now)
                        """)
                .param("userId", USER_ID.value())
                .param("now", Timestamp.from(FIRST))
                .update();
        jdbc.sql("""
                        insert into user_ssh_keys(
                            ssh_key_id, user_id, name, fingerprint, encrypted_private_key,
                            encryption_nonce, encrypted_aes_key, created_at
                        ) values ('ssh_scm_git_identity', :userId, 'work', 'fingerprint', 'cipher', 'nonce', 'aes', :now)
                        """)
                .param("userId", USER_ID.value())
                .param("now", Timestamp.from(FIRST))
                .update();
        repository = repository(dataSource);
    }

    @Test
    void remoteRejectionProtectsCurrentNameFromLaterHistoryCompensation() {
        assertThat(repository.findSyncCandidatesAfter(null, 500))
                .singleElement()
                .extracting(UserScmGitIdentityCandidate::unifiedAuthId)
                .isEqualTo("123456789");
        assertThat(repository.findSyncCandidatesAfter(USER_ID.value(), 500)).isEmpty();

        assertThat(repository.upsertAcceptedCommitIdentities(List.of(identity(
                "历史姓名", UserScmGitIdentitySource.ACCEPTED_COMMIT_HISTORY, FIRST, "1111111"))))
                .isOne();
        assertThat(repository.upsertAcceptedCommitIdentities(List.of(identity(
                "同证据校准姓名", UserScmGitIdentitySource.ACCEPTED_COMMIT_HISTORY, FIRST, "1111111"))))
                .isOne();
        assertThat(repository.findByUserId(USER_ID)).get().extracting(UserScmGitIdentity::gitName)
                .isEqualTo("同证据校准姓名");
        repository.upsertRemoteRejection(identity(
                "测试用户", UserScmGitIdentitySource.REMOTE_REJECTION, FIRST.plusSeconds(60), "2222222"));
        assertThat(repository.upsertAcceptedCommitIdentities(List.of(identity(
                "更晚历史姓名", UserScmGitIdentitySource.ACCEPTED_COMMIT_HISTORY,
                FIRST.plusSeconds(120), "3333333"))))
                .isZero();

        assertThat(repository.findByUserId(USER_ID))
                .get()
                .satisfies(identity -> {
                    assertThat(identity.gitName()).isEqualTo("测试用户");
                    assertThat(identity.source()).isEqualTo(UserScmGitIdentitySource.REMOTE_REJECTION);
                });
    }

    private static UserScmGitIdentity identity(
            String name,
            UserScmGitIdentitySource source,
            Instant evidenceAt,
            String commit) {
        return new UserScmGitIdentity(USER_ID, name, source, commit, evidenceAt, evidenceAt);
    }

    private static UserScmGitIdentityRepository repository(DataSource dataSource) throws Exception {
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
        UserScmGitIdentityMapper mapper =
                new SqlSessionTemplate(factory).getMapper(UserScmGitIdentityMapper.class);
        return new MyBatisUserScmGitIdentityRepository(mapper);
    }
}
