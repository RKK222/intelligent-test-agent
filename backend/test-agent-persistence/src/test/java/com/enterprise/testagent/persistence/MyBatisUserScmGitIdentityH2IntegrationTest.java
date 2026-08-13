package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserScmGitIdentity;
import com.enterprise.testagent.domain.user.UserScmGitIdentityRepository;
import com.enterprise.testagent.domain.user.UserScmGitIdentitySource;
import com.enterprise.testagent.persistence.mybatis.MyBatisUserScmGitIdentityRepository;
import com.enterprise.testagent.persistence.mybatis.UserScmGitIdentityMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 无 Docker 时验证新增 migration 及 H2 MyBatis 兼容 SQL。 */
class MyBatisUserScmGitIdentityH2IntegrationTest {

    private static final UserId USER_ID = new UserId("usr_scm_git_identity_h2");
    private static final Instant FIRST = Instant.parse("2026-08-13T10:00:00Z");

    private SingleConnectionDataSource dataSource;
    private UserScmGitIdentityRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_scm_git_identity_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        new ResourceDatabasePopulator(new ByteArrayResource("""
                create table users (
                    user_id varchar(128) primary key,
                    unified_auth_id varchar(255) not null unique,
                    username varchar(128) not null unique,
                    password_hash varchar(255) not null,
                    status varchar(32) not null,
                    created_at timestamp not null,
                    updated_at timestamp not null
                );
                create table user_ssh_keys (
                    ssh_key_id varchar(128) primary key,
                    user_id varchar(128) not null unique,
                    name varchar(255) not null,
                    fingerprint varchar(255) not null,
                    encrypted_private_key text not null,
                    encryption_nonce varchar(255) not null,
                    created_at timestamp not null,
                    constraint fk_user_ssh_keys_user foreign key (user_id) references users(user_id)
                );
                """.getBytes(StandardCharsets.UTF_8))).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260813190929__user_scm_git_identities_create.sql")).execute(dataSource);

        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values (:userId, '123456789', '测试用户1', 'hash', 'ACTIVE', :now, :now)
                        """)
                .param("userId", USER_ID.value())
                .param("now", FIRST)
                .update();
        jdbc.sql("""
                        insert into user_ssh_keys(
                            ssh_key_id, user_id, name, fingerprint, encrypted_private_key, encryption_nonce, created_at
                        ) values ('ssh_scm_git_identity_h2', :userId, 'work', 'fingerprint', 'cipher', 'nonce', :now)
                        """)
                .param("userId", USER_ID.value())
                .param("now", FIRST)
                .update();
        repository = repository();
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void remoteRejectionHasPriorityOverAcceptedCommitHistory() {
        assertThat(repository.findSyncCandidatesAfter(null, 500))
                .singleElement()
                .extracting(candidate -> candidate.unifiedAuthId())
                .isEqualTo("123456789");

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

    private UserScmGitIdentity identity(
            String name,
            UserScmGitIdentitySource source,
            Instant evidenceAt,
            String commit) {
        return new UserScmGitIdentity(USER_ID, name, source, commit, evidenceAt, evidenceAt);
    }

    private UserScmGitIdentityRepository repository() throws Exception {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("H2", "h2");
        provider.setProperties(databaseIds);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setDatabaseIdProvider(provider);
        factoryBean.setMapperLocations(new ClassPathResource("mybatis/UserScmGitIdentityMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        UserScmGitIdentityMapper mapper =
                new SqlSessionTemplate(factory).getMapper(UserScmGitIdentityMapper.class);
        return new MyBatisUserScmGitIdentityRepository(mapper);
    }
}
