package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplica;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryState;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.user.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 真实 Flyway + MyBatis 验证引用资产副本租约、generation 和过期 worker fencing。 */
class MyBatisReferenceRepositoryRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_reference_assets");
    private static final UserId USER_ID = new UserId("usr_reference_admin");
    private static final LinuxServerId SERVER_ID = new LinuxServerId("server-a");
    private static final LinuxServerId SERVER_B_ID = new LinuxServerId("server-b");

    private DataSource dataSource;
    private SingleConnectionDataSource schemaDataSource;
    private JdbcClient jdbcClient;
    private MyBatisReferenceRepositoryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        String url = "jdbc:h2:mem:testagent_reference_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                .formatted(UUID.randomUUID().toString().replace("-", ""));
        h2.setURL(url);
        h2.setUser("sa");
        h2.setPassword("");
        dataSource = h2;
        // H2 2.4 的 CHECK IN 常量集合绑定到创建它的 Database 生命周期；用常驻 schema 连接创建 migration，
        // 业务 MyBatis 仍通过独立连接执行，才能真实覆盖并发 CAS。
        schemaDataSource = new SingleConnectionDataSource(url, "sa", "", true);
        // 存量 V20260717173000 使用 H2 不识别的 timestamptz。本测试先由 Flyway 建到其前一稳定版本，
        // 再原样执行本需求 migration；PostgreSQL 完整链由部署环境 Flyway validate/migrate 负责。
        Flyway.configure()
                .dataSource(schemaDataSource)
                .locations("classpath:db/migration")
                .target("20260715213000")
                .load()
                .migrate();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260718110000__create_reference_repository_replica_tables.sql"))
                .execute(schemaDataSource);
        jdbcClient = JdbcClient.create(dataSource);
        insertCredentialUserAndRepository();

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        ReferenceRepositoryMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(ReferenceRepositoryMapper.class);
        repository = new MyBatisReferenceRepositoryRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        schemaDataSource.destroy();
    }

    @Test
    void migrationCreatesBothReferenceRepositoryTables() {
        assertThat(jdbcClient.sql("select count(*) from reference_repository_states")
                .query(Integer.class).single()).isZero();
        assertThat(jdbcClient.sql("select count(*) from reference_repository_replicas")
                .query(Integer.class).single()).isZero();
    }

    @Test
    void onlyOneWorkerPerServerOwnsLeaseAndExpiredWorkerCannotWriteBack() {
        assertThat(repository.initializeIfAbsent(state(1L))).isPresent();
        repository.upsertTargets(REPOSITORY_ID, 1L, "main", Set.of(SERVER_ID), NOW);

        var first = repository.claimReplica(
                REPOSITORY_ID, 1L, SERVER_ID, "lease-first", NOW.plusSeconds(30), NOW);
        var concurrent = repository.claimReplica(
                REPOSITORY_ID, 1L, SERVER_ID, "lease-concurrent", NOW.plusSeconds(30), NOW);
        var replacement = repository.claimReplica(
                REPOSITORY_ID,
                1L,
                SERVER_ID,
                "lease-replacement",
                NOW.plusSeconds(91),
                NOW.plusSeconds(31));

        assertThat(first).isPresent();
        assertThat(concurrent).isEmpty();
        assertThat(replacement).isPresent();
        assertThat(repository.markReady(
                REPOSITORY_ID,
                1L,
                SERVER_ID,
                "lease-first",
                "main",
                "commit-1",
                NOW.plusSeconds(32),
                NOW.plusSeconds(32))).isFalse();
        assertThat(repository.markReady(
                REPOSITORY_ID,
                1L,
                SERVER_ID,
                "lease-replacement",
                "main",
                "commit-1",
                NOW.plusSeconds(32),
                NOW.plusSeconds(32))).isTrue();
        assertThat(repository.findReplicas(REPOSITORY_ID)).singleElement().satisfies(replica -> {
            assertThat(replica.status()).isEqualTo(ReferenceRepositoryReplicaStatus.READY);
            assertThat(replica.currentCommitHash()).isEqualTo("commit-1");
        });
    }

    @Test
    void leaseRenewalPreventsReplacementUntilRenewedDeadlineAndOldTokenCannotRenewAfterTakeover() {
        assertThat(repository.initializeIfAbsent(state(1L))).isPresent();
        repository.upsertTargets(REPOSITORY_ID, 1L, "main", Set.of(SERVER_ID), NOW);
        assertThat(repository.claimReplica(
                REPOSITORY_ID, 1L, SERVER_ID, "lease-first", NOW.plusSeconds(30), NOW)).isPresent();

        assertThat(repository.renewLease(
                REPOSITORY_ID,
                1L,
                SERVER_ID,
                "lease-first",
                NOW.plusSeconds(80),
                NOW.plusSeconds(20))).isTrue();
        assertThat(repository.claimReplica(
                REPOSITORY_ID,
                1L,
                SERVER_ID,
                "lease-too-early",
                NOW.plusSeconds(90),
                NOW.plusSeconds(31))).isEmpty();
        assertThat(repository.claimReplica(
                REPOSITORY_ID,
                1L,
                SERVER_ID,
                "lease-replacement",
                NOW.plusSeconds(150),
                NOW.plusSeconds(81))).isPresent();
        assertThat(repository.renewLease(
                REPOSITORY_ID,
                1L,
                SERVER_ID,
                "lease-first",
                NOW.plusSeconds(160),
                NOW.plusSeconds(82))).isFalse();
    }

    @Test
    void offlineProcessingReplicaIsDeferredAndOldTokenIsFencedBeforeRecovery() {
        assertThat(repository.initializeIfAbsent(state(
                "main", "commit-1", 1L, ReferenceRepositoryStatus.SYNCHRONIZING))).isPresent();
        repository.upsertTargets(REPOSITORY_ID, 1L, "main", Set.of(SERVER_ID, SERVER_B_ID), NOW);
        assertThat(repository.claimReplica(
                REPOSITORY_ID, 1L, SERVER_B_ID, "lease-offline", NOW.plusSeconds(30), NOW)).isPresent();

        assertThat(repository.deferOfflineReplicas(
                REPOSITORY_ID, 1L, Set.of(SERVER_ID), NOW.plusSeconds(1))).isEqualTo(1);

        assertThat(repository.findReplicas(REPOSITORY_ID))
                .filteredOn(replica -> replica.linuxServerId().equals(SERVER_B_ID))
                .singleElement()
                .satisfies(replica -> {
                    assertThat(replica.status()).isEqualTo(ReferenceRepositoryReplicaStatus.DEFERRED);
                    assertThat(replica.leaseToken()).isNull();
                    assertThat(replica.leaseUntil()).isNull();
                });
        assertThat(repository.renewLease(
                REPOSITORY_ID,
                1L,
                SERVER_B_ID,
                "lease-offline",
                NOW.plusSeconds(60),
                NOW.plusSeconds(2))).isFalse();

        repository.upsertTargets(REPOSITORY_ID, 1L, "main", Set.of(SERVER_B_ID), NOW.plusSeconds(3));
        assertThat(repository.findReplicas(REPOSITORY_ID))
                .filteredOn(replica -> replica.linuxServerId().equals(SERVER_B_ID))
                .singleElement()
                .satisfies(replica -> {
                    assertThat(replica.status()).isEqualTo(ReferenceRepositoryReplicaStatus.PENDING);
                    assertThat(replica.retryCount()).isZero();
                    assertThat(replica.nextRetryAt()).isNull();
                });
        assertThat(repository.claimReplica(
                REPOSITORY_ID,
                1L,
                SERVER_B_ID,
                "lease-recovered",
                NOW.plusSeconds(60),
                NOW.plusSeconds(4))).isPresent();
        assertThat(repository.markReady(
                REPOSITORY_ID,
                1L,
                SERVER_B_ID,
                "lease-recovered",
                "main",
                "commit-1",
                NOW.plusSeconds(5),
                NOW.plusSeconds(5))).isTrue();
        assertThat(repository.findReplicas(REPOSITORY_ID))
                .filteredOn(replica -> replica.linuxServerId().equals(SERVER_B_ID))
                .singleElement()
                .satisfies(replica -> assertThat(replica.status())
                        .isEqualTo(ReferenceRepositoryReplicaStatus.READY));
    }

    @Test
    void emptyLiveServerSetSafelyDefersEveryNonTerminalReplica() {
        assertThat(repository.initializeIfAbsent(state(
                "main", "commit-1", 1L, ReferenceRepositoryStatus.SYNCHRONIZING))).isPresent();
        repository.upsertTargets(REPOSITORY_ID, 1L, "main", Set.of(SERVER_ID, SERVER_B_ID), NOW);

        assertThat(repository.deferOfflineReplicas(
                REPOSITORY_ID, 1L, Set.of(), NOW.plusSeconds(1))).isEqualTo(2);

        assertThat(repository.findReplicas(REPOSITORY_ID))
                .extracting(ReferenceRepositoryReplica::status)
                .containsOnly(ReferenceRepositoryReplicaStatus.DEFERRED);
    }

    @Test
    void staleGenerationCannotDeferReplicaAfterStateGenerationAdvances() {
        assertThat(repository.initializeIfAbsent(state(
                "main", "commit-1", 1L, ReferenceRepositoryStatus.READY))).isPresent();
        repository.upsertTargets(REPOSITORY_ID, 1L, "main", Set.of(SERVER_ID), NOW);
        assertThat(repository.advanceGenerationIfCurrent(
                1L,
                state("main", "commit-2", 2L, ReferenceRepositoryStatus.SYNCHRONIZING))).isPresent();

        assertThat(repository.deferOfflineReplicas(
                REPOSITORY_ID, 1L, Set.of(), NOW.plusSeconds(1))).isZero();

        assertThat(repository.findReplicas(REPOSITORY_ID)).singleElement().satisfies(replica -> {
            assertThat(replica.generation()).isEqualTo(1L);
            assertThat(replica.status()).isEqualTo(ReferenceRepositoryReplicaStatus.PENDING);
        });
    }

    @Test
    void newGenerationMakesPreviousGenerationWorkerStale() {
        assertThat(repository.initializeIfAbsent(state(
                "main", "commit-1", 1L, ReferenceRepositoryStatus.READY))).isPresent();
        repository.upsertTargets(REPOSITORY_ID, 1L, "main", Set.of(SERVER_ID), NOW);
        assertThat(repository.claimReplica(
                REPOSITORY_ID, 1L, SERVER_ID, "lease-generation-1", NOW.plusSeconds(60), NOW)).isPresent();

        assertThat(repository.advanceGenerationIfCurrent(1L, state(2L))).isPresent();
        repository.upsertTargets(REPOSITORY_ID, 2L, "main", Set.of(SERVER_ID), NOW.plusSeconds(1));

        assertThat(repository.markBlocked(
                REPOSITORY_ID,
                1L,
                SERVER_ID,
                "lease-generation-1",
                "stale error",
                NOW.plusSeconds(2))).isFalse();
        assertThat(repository.findReplicas(REPOSITORY_ID)).singleElement().satisfies(replica -> {
            assertThat(replica.generation()).isEqualTo(2L);
            assertThat(replica.status()).isEqualTo(ReferenceRepositoryReplicaStatus.PENDING);
            assertThat(replica.lastError()).isNull();
        });
    }

    @Test
    void concurrentDifferentBranchInitializationKeepsBranchAndCommitFromSingleWinner() throws Exception {
        ReferenceRepositoryState main = state("main", "commit-main", 1L, ReferenceRepositoryStatus.INITIALIZING);
        ReferenceRepositoryState release = state(
                "release", "commit-release", 1L, ReferenceRepositoryStatus.INITIALIZING);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var mainResult = executor.submit(() -> {
                barrier.await();
                return repository.initializeIfAbsent(main);
            });
            var releaseResult = executor.submit(() -> {
                barrier.await();
                return repository.initializeIfAbsent(release);
            });

            List<Optional<ReferenceRepositoryState>> results = List.of(mainResult.get(), releaseResult.get());
            assertThat(results).filteredOn(Optional::isPresent).hasSize(1);
            ReferenceRepositoryState winner = results.stream()
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElseThrow();
            assertThat(repository.findState(REPOSITORY_ID)).contains(winner);
            assertThat(winner.branch() + ":" + winner.targetCommitHash())
                    .isIn("main:commit-main", "release:commit-release");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentGenerationAdvanceAllowsOneWinnerAndLoserReadsWinningState() throws Exception {
        assertThat(repository.initializeIfAbsent(state(
                "main", "commit-1", 1L, ReferenceRepositoryStatus.FAILED))).isPresent();
        ReferenceRepositoryState first = state(
                "main", "commit-first", 2L, ReferenceRepositoryStatus.SYNCHRONIZING);
        ReferenceRepositoryState second = state(
                "main", "commit-second", 2L, ReferenceRepositoryStatus.SYNCHRONIZING);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var firstResult = executor.submit(() -> {
                barrier.await();
                return repository.advanceGenerationIfCurrent(1L, first);
            });
            var secondResult = executor.submit(() -> {
                barrier.await();
                return repository.advanceGenerationIfCurrent(1L, second);
            });

            List<Optional<ReferenceRepositoryState>> results = List.of(firstResult.get(), secondResult.get());
            assertThat(results).filteredOn(Optional::isPresent).hasSize(1);
            ReferenceRepositoryState winner = results.stream()
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElseThrow();
            ReferenceRepositoryState loserRead = repository.findState(REPOSITORY_ID).orElseThrow();
            assertThat(loserRead).isEqualTo(winner);
            assertThat(loserRead.generation()).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void stateCursorPaginationReturnsEveryRepositoryExactlyOnce() {
        List<CodeRepositoryId> additionalIds = List.of(
                new CodeRepositoryId("repo_cursor_a"),
                new CodeRepositoryId("repo_cursor_b"),
                new CodeRepositoryId("repo_cursor_c"));
        additionalIds.forEach(id -> insertRepository(id, id.value().replace('_', '-')));
        assertThat(repository.initializeIfAbsent(state(1L))).isPresent();
        additionalIds.forEach(id -> assertThat(repository.initializeIfAbsent(state(
                id, "main", "commit-1", 1L, ReferenceRepositoryStatus.INITIALIZING))).isPresent());

        List<ReferenceRepositoryState> first = repository.findStatesAfter(null, 2);
        List<ReferenceRepositoryState> second = repository.findStatesAfter(
                first.get(first.size() - 1).repositoryId(), 2);

        assertThat(java.util.stream.Stream.concat(first.stream(), second.stream())
                        .map(state -> state.repositoryId().value())
                        .toList())
                .containsExactly(
                        "repo_cursor_a", "repo_cursor_b", "repo_cursor_c", REPOSITORY_ID.value());
    }

    private ReferenceRepositoryState state(long generation) {
        return state(
                "main",
                "commit-" + generation,
                generation,
                generation == 1L ? ReferenceRepositoryStatus.INITIALIZING : ReferenceRepositoryStatus.SYNCHRONIZING);
    }

    private ReferenceRepositoryState state(
            String branch,
            String commit,
            long generation,
            ReferenceRepositoryStatus status) {
        return state(REPOSITORY_ID, branch, commit, generation, status);
    }

    private ReferenceRepositoryState state(
            CodeRepositoryId repositoryId,
            String branch,
            String commit,
            long generation,
            ReferenceRepositoryStatus status) {
        return new ReferenceRepositoryState(
                repositoryId,
                branch,
                commit,
                generation,
                status,
                USER_ID,
                "trace_generation_" + generation,
                null,
                NOW,
                NOW,
                NOW.plusSeconds(generation));
    }

    private void insertCredentialUserAndRepository() {
        jdbcClient.sql("""
                        insert into users(
                            user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                        values (:userId, :unifiedAuthId, :username, :passwordHash, 'ACTIVE', :now, :now)
                        """)
                .param("userId", USER_ID.value())
                .param("unifiedAuthId", "001")
                .param("username", "reference-admin")
                .param("passwordHash", "hash")
                .param("now", Timestamp.from(NOW))
                .update();
        insertRepository(REPOSITORY_ID, "reference-assets");
    }

    private void insertRepository(CodeRepositoryId repositoryId, String englishName) {
        jdbcClient.sql("""
                        insert into code_repositories(
                            repository_id, git_url, name, english_name, repository_type,
                            deployment_mode, standard, created_at, updated_at)
                        values (:repositoryId, :gitUrl, :name, :englishName, 'APPLICATION_ASSET_REPOSITORY',
                            'EXTERNAL', false, :now, :now)
                        """)
                .param("repositoryId", repositoryId.value())
                .param("gitUrl", "https://git.example.test/" + englishName + ".git")
                .param("name", "引用资产库")
                .param("englishName", englishName)
                .param("now", Timestamp.from(NOW))
                .update();
    }
}
