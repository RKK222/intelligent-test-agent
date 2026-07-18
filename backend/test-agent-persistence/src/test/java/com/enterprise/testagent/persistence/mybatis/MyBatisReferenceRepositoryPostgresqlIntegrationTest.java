package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryState;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.user.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;
import java.util.Set;
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

/** 通过真实 PostgreSQL 验证引用资产库的 ON CONFLICT 方言和完整 Flyway 链。 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisReferenceRepositoryPostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_reference_pg");
    private static final UserId USER_ID = new UserId("usr_reference_pg");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-pg");
    private static final LinuxServerId SERVER_B_ID = new LinuxServerId("server-pg-b");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static MyBatisReferenceRepositoryRepository repository;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        insertCredentialUserAndRepository(dataSource);
        repository = repository(dataSource);
    }

    @Test
    void postgresqlStatementsPreserveCasAndGenerationFencing() {
        ReferenceRepositoryState initial = state(
                "main", "commit-1", 1L, ReferenceRepositoryStatus.INITIALIZING);
        ReferenceRepositoryState duplicate = state(
                "release", "commit-other", 1L, ReferenceRepositoryStatus.INITIALIZING);

        assertThat(repository.initializeIfAbsent(initial)).contains(initial);
        assertThat(repository.initializeIfAbsent(duplicate)).isEmpty();
        repository.upsertTargets(REPOSITORY_ID, 1L, "main", Set.of(SERVER_ID), NOW);
        assertThat(repository.findReplicas(REPOSITORY_ID)).singleElement().satisfies(replica -> {
            assertThat(replica.generation()).isEqualTo(1L);
            assertThat(replica.status()).isEqualTo(ReferenceRepositoryReplicaStatus.PENDING);
            assertThat(replica.currentBranch()).isNull();
        });

        assertThat(repository.updateOverallStatus(
                REPOSITORY_ID, 1L, ReferenceRepositoryStatus.READY, null, NOW.plusSeconds(1))).isTrue();
        ReferenceRepositoryState next = state(
                "main", "commit-2", 2L, ReferenceRepositoryStatus.SYNCHRONIZING);
        assertThat(repository.advanceGenerationIfCurrent(1L, next)).contains(next);
        repository.upsertTargets(REPOSITORY_ID, 2L, "main", Set.of(SERVER_ID), NOW.plusSeconds(2));

        assertThat(repository.findReplicas(REPOSITORY_ID)).singleElement().satisfies(replica -> {
            assertThat(replica.generation()).isEqualTo(2L);
            assertThat(replica.status()).isEqualTo(ReferenceRepositoryReplicaStatus.PENDING);
            assertThat(replica.currentCommitHash()).isNull();
        });

        repository.upsertTargets(REPOSITORY_ID, 2L, "main", Set.of(SERVER_B_ID), NOW.plusSeconds(3));
        assertThat(repository.claimReplica(
                REPOSITORY_ID,
                2L,
                SERVER_B_ID,
                "lease-pg-offline",
                NOW.plusSeconds(30),
                NOW.plusSeconds(4))).isPresent();
        assertThat(repository.deferOfflineReplicas(
                REPOSITORY_ID, 2L, Set.of(SERVER_ID), NOW.plusSeconds(5))).isEqualTo(1);
        assertThat(repository.renewLease(
                REPOSITORY_ID,
                2L,
                SERVER_B_ID,
                "lease-pg-offline",
                NOW.plusSeconds(60),
                NOW.plusSeconds(6))).isFalse();

        repository.upsertTargets(REPOSITORY_ID, 2L, "main", Set.of(SERVER_B_ID), NOW.plusSeconds(7));
        assertThat(repository.findReplicas(REPOSITORY_ID))
                .filteredOn(replica -> replica.linuxServerId().equals(SERVER_B_ID))
                .singleElement()
                .satisfies(replica -> assertThat(replica.status())
                        .isEqualTo(ReferenceRepositoryReplicaStatus.PENDING));
        assertThat(repository.claimReplica(
                REPOSITORY_ID,
                2L,
                SERVER_B_ID,
                "lease-pg-recovered",
                NOW.plusSeconds(60),
                NOW.plusSeconds(8))).isPresent();
    }

    private static MyBatisReferenceRepositoryRepository repository(DataSource dataSource) throws Exception {
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        databaseIdProvider.setProperties(databaseIds);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setDatabaseIdProvider(databaseIdProvider);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        ReferenceRepositoryMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(ReferenceRepositoryMapper.class);
        return new MyBatisReferenceRepositoryRepository(mapper);
    }

    private static ReferenceRepositoryState state(
            String branch,
            String commit,
            long generation,
            ReferenceRepositoryStatus status) {
        return new ReferenceRepositoryState(
                REPOSITORY_ID,
                branch,
                commit,
                generation,
                status,
                USER_ID,
                "trace_pg_" + generation,
                null,
                NOW,
                NOW,
                NOW.plusSeconds(generation));
    }

    private static void insertCredentialUserAndRepository(DataSource dataSource) {
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        jdbcClient.sql("""
                        insert into users(
                            user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values (:userId, :unifiedAuthId, :username, :passwordHash, 'ACTIVE', :now, :now)
                        """)
                .param("userId", USER_ID.value())
                .param("unifiedAuthId", "reference-pg")
                .param("username", "reference-pg")
                .param("passwordHash", "hash")
                .param("now", Timestamp.from(NOW))
                .update();
        jdbcClient.sql("""
                        insert into code_repositories(
                            repository_id, git_url, name, english_name, repository_type,
                            deployment_mode, standard, created_at, updated_at)
                        values (:repositoryId, :gitUrl, :name, :englishName, 'APPLICATION_ASSET_REPOSITORY',
                            'EXTERNAL', false, :now, :now)
                        """)
                .param("repositoryId", REPOSITORY_ID.value())
                .param("gitUrl", "https://git.example.test/reference-pg.git")
                .param("name", "PostgreSQL 引用资产库")
                .param("englishName", "reference-pg")
                .param("now", Timestamp.from(NOW))
                .update();
    }
}
