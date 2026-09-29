package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.LocalClientPublicCapabilityMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisLocalClientPublicCapabilityRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 使用根目录 .env.test 指定的真实 PostgreSQL，在隔离 schema 中验证 migration、MyBatis 映射和幂等约束。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyBatisLocalClientPublicCapabilityPostgresqlIntegrationTest {

    private static final UserId USER_ID = new UserId("usr_public_capability_test");
    private static final LocalClientInstanceId INSTANCE_ID = new LocalClientInstanceId("lci_public_capability_test");
    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");
    private PGSimpleDataSource administrativeDataSource;
    private PGSimpleDataSource schemaDataSource;
    private String schema;
    private LocalClientPublicCapabilityRepository repository;

    @BeforeAll
    void setUp() throws Exception {
        String host = System.getenv("TEST_AGENT_TEST_DB_HOST");
        Assumptions.assumeTrue(host != null && !host.isBlank(),
                "需要从仓库根目录 .env.test 导出真实 PostgreSQL 变量");
        administrativeDataSource = dataSource(null);
        schema = "test_public_capability_" + UUID.randomUUID().toString().replace("-", "");
        JdbcClient.create(administrativeDataSource).sql("create schema " + schema).update();
        schemaDataSource = dataSource(schema);
        Flyway.configure()
                .dataSource(schemaDataSource)
                .defaultSchema(schema)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        JdbcClient jdbc = JdbcClient.create(schemaDataSource);
        jdbc.sql("""
                        insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values (:userId, '999999998', '公共能力测试用户', 'hash', 'ACTIVE', :now, :now)
                        """)
                .param("userId", USER_ID.value()).param("now", Timestamp.from(NOW)).update();
        jdbc.sql("""
                        insert into local_client_instances(
                            client_instance_id, user_id, client_name, platform, architecture,
                            client_version, opencode_version, launcher_version, self_update_capabilities,
                            self_update_supported, created_at, updated_at, last_connected_at
                        ) values (
                            :instanceId, :userId, 'postgres-test', 'darwin', 'arm64',
                            '0.1.0-dev', '2.0.18', '1', 'PUBLIC_CAPABILITY_SYNC_V1',
                            false, :now, :now, :now
                        )
                        """)
                .param("instanceId", INSTANCE_ID.value()).param("userId", USER_ID.value())
                .param("now", Timestamp.from(NOW))
                .update();
        repository = repository(schemaDataSource);
    }

    @AfterAll
    void tearDown() {
        if (administrativeDataSource != null && schema != null) {
            JdbcClient.create(administrativeDataSource).sql("drop schema if exists " + schema + " cascade").update();
        }
    }

    @Test
    void migratesAndPersistsReleaseStateAttemptWithIdempotencyConstraint() {
        byte[] artifact = "portable-capability".getBytes(StandardCharsets.UTF_8);
        String artifactDigest = sha256(artifact);
        String bundleDigest = "b".repeat(64);
        String commit = "c".repeat(40);
        LocalClientPublicCapabilityModels.Release release = new LocalClientPublicCapabilityModels.Release(
                commit, bundleDigest, artifactDigest, LocalClientPublicCapabilityModels.Compatibility.AVAILABLE,
                null, "{\"schemaVersion\":1}", "{\"agentsChanged\":true}",
                new LocalClientPublicCapabilityModels.Counts(1, 2, 3), true, artifact, artifact.length,
                artifact.length, 6, NOW);

        repository.insertRelease(release);
        repository.saveInstanceState(new LocalClientPublicCapabilityModels.InstanceState(
                INSTANCE_ID, null, null, commit, bundleDigest,
                LocalClientPublicCapabilityModels.InstanceStatus.UPDATE_AVAILABLE, null, NOW, NOW));
        LocalClientPublicCapabilityModels.Attempt attempt = new LocalClientPublicCapabilityModels.Attempt(
                "lcpc_" + "d".repeat(32), INSTANCE_ID, USER_ID, 0, commit, bundleDigest,
                LocalClientPublicCapabilityModels.AttemptStatus.PENDING, null, NOW, NOW, null);
        repository.insertAttempt(attempt);

        assertThat(repository.findReleaseBySourceCommit(commit)).get().satisfies(saved -> {
            assertThat(saved.bundleDigest()).isEqualTo(bundleDigest);
            assertThat(saved.artifact()).isEqualTo(artifact);
        });
        assertThat(repository.findReleaseByDigest(bundleDigest)).get()
                .extracting(LocalClientPublicCapabilityModels.Release::sourceCommit)
                .isEqualTo(commit);
        assertThat(repository.findInstanceState(INSTANCE_ID)).get().satisfies(state -> {
            assertThat(state.pendingDigest()).isEqualTo(bundleDigest);
            assertThat(state.status()).isEqualTo(LocalClientPublicCapabilityModels.InstanceStatus.UPDATE_AVAILABLE);
        });
        assertThat(repository.bindAttemptGeneration(attempt.commandId(), 0, 9, NOW.plusSeconds(1))).isTrue();
        assertThat(repository.transitionAttempt(attempt.commandId(), "PENDING", "SENT", null, NOW.plusSeconds(2)))
                .isTrue();
        assertThat(repository.transitionAttempt(attempt.commandId(), "PENDING", "SENT", null, NOW.plusSeconds(3)))
                .isFalse();

        LocalClientPublicCapabilityModels.Attempt duplicate = new LocalClientPublicCapabilityModels.Attempt(
                "lcpc_" + "e".repeat(32), INSTANCE_ID, USER_ID, 9, commit, bundleDigest,
                LocalClientPublicCapabilityModels.AttemptStatus.PENDING, null, NOW, NOW, null);
        assertThatThrownBy(() -> repository.insertAttempt(duplicate))
                .isInstanceOfAny(DuplicateKeyException.class, org.springframework.dao.DataIntegrityViolationException.class);

        repository.saveInstanceState(new LocalClientPublicCapabilityModels.InstanceState(
                INSTANCE_ID, commit, bundleDigest, null, null,
                LocalClientPublicCapabilityModels.InstanceStatus.CURRENT, null,
                NOW.plusSeconds(4), NOW.plusSeconds(4)));
        repository.markUpdateAvailableForCapableInstances(
                commit, bundleDigest, NOW.plusSeconds(5), "PUBLIC_CAPABILITY_SYNC_V1");
        assertThat(repository.findInstanceState(INSTANCE_ID)).get().satisfies(current -> {
            assertThat(current.status()).isEqualTo(LocalClientPublicCapabilityModels.InstanceStatus.CURRENT);
            assertThat(current.pendingCommit()).isNull();
            assertThat(current.pendingDigest()).isNull();
        });
    }

    private static LocalClientPublicCapabilityRepository repository(PGSimpleDataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        LocalClientPublicCapabilityMapper mapper =
                new SqlSessionTemplate(factory).getMapper(LocalClientPublicCapabilityMapper.class);
        return new MyBatisLocalClientPublicCapabilityRepository(mapper);
    }

    private PGSimpleDataSource dataSource(String currentSchema) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {required("TEST_AGENT_TEST_DB_HOST")});
        dataSource.setPortNumbers(new int[] {Integer.parseInt(required("TEST_AGENT_TEST_DB_PORT"))});
        dataSource.setDatabaseName(required("TEST_AGENT_TEST_DB_NAME"));
        dataSource.setUser(required("TEST_AGENT_TEST_DB_USERNAME"));
        dataSource.setPassword(required("TEST_AGENT_TEST_DB_PASSWORD"));
        if (currentSchema != null) {
            dataSource.setCurrentSchema(currentSchema);
        }
        return dataSource;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 未通过 .env.test 提供");
        }
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
