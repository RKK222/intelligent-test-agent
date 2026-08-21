package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.localclient.LocalClientCredential;
import com.enterprise.testagent.domain.localclient.LocalClientCredentialStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.LocalClientMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisLocalClientCredentialRepository;
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

/** 验证 BIGINT 版本、revealed_at 映射和一次性消费使用的 FOR UPDATE 查询。 */
class MyBatisLocalClientCredentialRepositoryIntegrationTest {

    private static final UserId USER_ID = new UserId("usr_local_credential");
    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;
    private MyBatisLocalClientCredentialRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_local_client_mapper_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("create table users (user_id varchar(128) primary key)").update();
        jdbc.sql("""
                        create table local_client_credentials (
                            user_id varchar(128) primary key,
                            encrypted_client_key text not null,
                            client_key_fingerprint varchar(64) not null unique,
                            key_hint varchar(64) not null,
                            version bigint not null,
                            status varchar(32) not null,
                            created_at timestamp with time zone not null,
                            updated_at timestamp with time zone not null,
                            revealed_at timestamp with time zone,
                            revoked_at timestamp with time zone
                        )
                        """).update();
        jdbc.sql("insert into users(user_id) values (:userId)")
                .param("userId", USER_ID.value())
                .update();

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mybatis/LocalClientMapper.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        LocalClientMapper mapper = new SqlSessionTemplate(sessionFactory).getMapper(LocalClientMapper.class);
        repository = new MyBatisLocalClientCredentialRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void loadsPrimitiveLongVersionFromCredentialQueries() {
        Instant now = Instant.parse("2026-08-17T09:00:00Z");
        LocalClientCredential credential = new LocalClientCredential(
                USER_ID,
                "rsa-ciphertext",
                "a".repeat(64),
                "tack_v1_ABC...WXYZ",
                7L,
                LocalClientCredentialStatus.ACTIVE,
                now,
                now.plusSeconds(30),
                null,
                null);
        insertCredential(credential);

        assertThat(repository.findByUserId(credential.userId())).contains(credential);
        assertThat(repository.findActiveByFingerprint(credential.clientKeyFingerprint())).contains(credential);
    }

    @Test
    void roundTripsNullableRevealTimestampAndLocksTheCredentialRow() {
        Instant createdAt = Instant.parse("2026-08-20T10:00:00Z");
        LocalClientCredential unrevealed = credential(createdAt, createdAt, null);
        insertCredential(unrevealed);

        assertThat(repository.findByUserIdForUpdate(USER_ID)).contains(unrevealed);

        Instant revealedAt = Instant.parse("2026-08-20T10:05:00Z");
        LocalClientCredential revealed = credential(createdAt, revealedAt, revealedAt);
        jdbc.sql("""
                        update local_client_credentials
                        set updated_at = :updatedAt, revealed_at = :revealedAt
                        where user_id = :userId
                        """)
                .param("updatedAt", revealedAt)
                .param("revealedAt", revealedAt)
                .param("userId", USER_ID.value())
                .update();

        assertThat(repository.findByUserId(USER_ID)).contains(revealed);
    }

    private void insertCredential(LocalClientCredential credential) {
        jdbc.sql("""
                        insert into local_client_credentials(
                            user_id, encrypted_client_key, client_key_fingerprint, key_hint,
                            version, status, created_at, updated_at, revealed_at, revoked_at
                        ) values (
                            :userId, :encryptedClientKey, :fingerprint, :keyHint,
                            :version, :status, :createdAt, :updatedAt, :revealedAt, :revokedAt
                        )
                        """)
                .param("userId", credential.userId().value())
                .param("encryptedClientKey", credential.encryptedClientKey())
                .param("fingerprint", credential.clientKeyFingerprint())
                .param("keyHint", credential.keyHint())
                .param("version", credential.version())
                .param("status", credential.status().name())
                .param("createdAt", credential.createdAt())
                .param("updatedAt", credential.updatedAt())
                .param("revealedAt", credential.revealedAt(), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("revokedAt", credential.revokedAt(), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
    }

    private static LocalClientCredential credential(
            Instant createdAt,
            Instant updatedAt,
            Instant revealedAt) {
        return new LocalClientCredential(
                USER_ID,
                "rsa-ciphertext",
                "c".repeat(64),
                "tack_v1_ABC...WXYZ",
                1,
                LocalClientCredentialStatus.ACTIVE,
                createdAt,
                updatedAt,
                revealedAt,
                null);
    }
}
