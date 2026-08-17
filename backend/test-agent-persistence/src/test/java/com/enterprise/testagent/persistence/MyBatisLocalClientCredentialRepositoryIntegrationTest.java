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
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 验证本地客户端凭据的 BIGINT 版本可由 MyBatis XML 精确映射到领域基本类型。 */
class MyBatisLocalClientCredentialRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-17T09:00:00Z");
    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;
    private MyBatisLocalClientCredentialRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_local_client_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        jdbcClient = JdbcClient.create(dataSource);
        jdbcClient.sql("create table users(user_id varchar(128) primary key)").update();
        jdbcClient.sql("""
                create table local_client_credentials(
                    user_id varchar(128) primary key references users(user_id),
                    encrypted_client_key text not null,
                    client_key_fingerprint varchar(64) not null unique,
                    key_hint varchar(64) not null,
                    version bigint not null,
                    status varchar(32) not null,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null,
                    revoked_at timestamp with time zone
                )
                """).update();
        jdbcClient.sql("insert into users(user_id) values ('usr_local_client_test')").update();

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        LocalClientMapper mapper = new SqlSessionTemplate(sessionFactory)
                .getMapper(LocalClientMapper.class);
        repository = new MyBatisLocalClientCredentialRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void loadsPrimitiveLongVersionFromCredentialQueries() {
        LocalClientCredential credential = new LocalClientCredential(
                new UserId("usr_local_client_test"),
                "rsa-ciphertext",
                "a".repeat(64),
                "tack_v1_ABC...WXYZ",
                7L,
                LocalClientCredentialStatus.ACTIVE,
                NOW,
                NOW.plusSeconds(30),
                null);
        // H2 不支持生产 PostgreSQL 的 ON CONFLICT 方言；这里直接造数，聚焦覆盖本次读取构造器映射。
        jdbcClient.sql("""
                insert into local_client_credentials(
                    user_id, encrypted_client_key, client_key_fingerprint, key_hint,
                    version, status, created_at, updated_at, revoked_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(
                        credential.userId().value(),
                        credential.encryptedClientKey(),
                        credential.clientKeyFingerprint(),
                        credential.keyHint(),
                        credential.version(),
                        credential.status().name(),
                        credential.createdAt(),
                        credential.updatedAt(),
                        credential.revokedAt())
                .update();

        assertThat(repository.findByUserId(credential.userId())).contains(credential);
        assertThat(repository.findActiveByFingerprint(credential.clientKeyFingerprint())).contains(credential);
    }
}
