package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceGeneration;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRepository;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceRunLease;
import com.enterprise.testagent.domain.automationreference.ApplicationAutomationReferenceState;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceGenerationStatus;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceOperationType;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryReplicaStatus;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryStatus;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
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

/** 真实 migration + MyBatis XML 验证 `(appId, repositoryId)` 唯一配置和副本 fencing。 */
class MyBatisApplicationAutomationReferenceRepositoryIntegrationTest {

    private static final ApplicationId APP_ALPHA = new ApplicationId("app_alpha");
    private static final ApplicationId APP_BETA = new ApplicationId("app_beta");
    private static final CodeRepositoryId REPOSITORY_ID = new CodeRepositoryId("repo_automation");
    private static final UserId ADMIN_ID = new UserId("usr_admin");
    private static final LinuxServerId SERVER_A = new LinuxServerId("server-a");
    private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");

    private SingleConnectionDataSource schemaDataSource;
    private JdbcClient jdbcClient;
    private ApplicationAutomationReferenceRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource h2 = new JdbcDataSource();
        String url = "jdbc:h2:mem:testagent_automation_reference_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                .formatted(UUID.randomUUID().toString().replace("-", ""));
        h2.setURL(url);
        h2.setUser("sa");
        h2.setPassword("");
        schemaDataSource = new SingleConnectionDataSource(url, "sa", "", true);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("fixtures/application-automation-reference-history.sql"),
                new ClassPathResource("db/migration/V20260821113000__application_automation_references_create.sql"),
                new ClassPathResource("db/migration/V20260822075000__application_automation_reference_read_leases_create.sql"));
        populator.execute(schemaDataSource);
        jdbcClient = JdbcClient.create(h2);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(h2);
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("H2", "h2");
        databaseIdProvider.setProperties(databaseIds);
        factoryBean.setDatabaseIdProvider(databaseIdProvider);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/ApplicationAutomationReferenceMapper.xml"));
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        ApplicationAutomationReferenceMapper mapper = new SqlSessionTemplate(sqlSessionFactory)
                .getMapper(ApplicationAutomationReferenceMapper.class);
        repository = new MyBatisApplicationAutomationReferenceRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        schemaDataSource.destroy();
    }

    @Test
    void migrationCollapsesLegacyTemplatesAndBranchesToOneConfigurationPerApplicationRepository() {
        assertThat(jdbcClient.sql("select count(*) from application_automation_references")
                .query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbcClient.sql("""
                        select count(*)
                        from application_automation_references
                        where app_id = 'app_alpha' and repository_id = 'repo_automation'
                        """).query(Integer.class).single()).isEqualTo(1);

        assertThat(repository.findGeneration(APP_ALPHA, REPOSITORY_ID, 1L)).get().satisfies(generation -> {
            assertThat(generation.branch()).isEqualTo("feature/e2e");
            assertThat(generation.directoryPath()).isEqualTo("src/test");
            assertThat(generation.targetCommitHash()).isEqualTo("commit-feature");
            assertThat(generation.description())
                    .isEqualTo("自动化代码库 / feature/e2e / src/test，只读自动化引用");
            assertThat(generation.merge()).isFalse();
        });
        assertThat(repository.findGeneration(APP_BETA, REPOSITORY_ID, 1L)).get().satisfies(generation -> {
            assertThat(generation.branch()).isEqualTo("release");
            assertThat(generation.directoryPath()).isEqualTo("integration");
        });
        assertThat(repository.findReplicas(APP_ALPHA, REPOSITORY_ID, 1L))
                .extracting(replica -> replica.linuxServerId().value())
                .containsExactly("server-a", "server-b");
        assertThat(jdbcClient.sql("""
                        select count(*) from application_workspaces workspace
                        join code_repositories repository on repository.repository_id = workspace.repository_id
                        where repository.repository_type = 'AUTOMATION_CODE_REPOSITORY' and workspace.enabled
                        """).query(Integer.class).single()).isZero();
        assertThat(jdbcClient.sql("select enabled from application_workspaces where workspace_id = 'aws_code'")
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void readyActivationAndNextGenerationUseOptimisticLockAndKeepApplicationsIndependent() {
        ApplicationAutomationReferenceState migrated = repository.findState(APP_ALPHA, REPOSITORY_ID).orElseThrow();
        assertThat(migrated.activeGeneration()).isEqualTo(1L);
        assertThat(migrated.status()).isEqualTo(ReferenceRepositoryStatus.SYNCHRONIZING);
        assertThat(repository.claimReplica(
                APP_ALPHA, REPOSITORY_ID, 1L, SERVER_A, "lease-1", NOW.plusSeconds(60), NOW)).isPresent();
        assertThat(repository.markReady(
                APP_ALPHA,
                REPOSITORY_ID,
                1L,
                SERVER_A,
                "lease-1",
                "feature/e2e",
                "commit-feature",
                NOW.plusSeconds(1),
                NOW.plusSeconds(1))).isTrue();
        assertThat(repository.completeGeneration(
                APP_ALPHA, REPOSITORY_ID, 1L, ReferenceRepositoryStatus.READY, null, NOW.plusSeconds(2)))
                .isTrue();

        ApplicationAutomationReferenceState ready = repository.findState(APP_ALPHA, REPOSITORY_ID).orElseThrow();
        ApplicationAutomationReferenceGeneration second = generation(APP_ALPHA, 2L, "op-switch-2");
        assertThat(repository.reserveGeneration(
                second, 1L, ready.lockVersion(), NOW.plusSeconds(3))).contains(second);
        assertThat(repository.reserveGeneration(
                generation(APP_ALPHA, 3L, "op-stale"), 1L, ready.lockVersion(), NOW.plusSeconds(4)))
                .isEmpty();
        assertThat(repository.findByOperationId(APP_ALPHA, REPOSITORY_ID, "op-switch-2"))
                .contains(second);

        assertThat(repository.findState(APP_BETA, REPOSITORY_ID)).get().satisfies(beta -> {
            assertThat(beta.activeGeneration()).isEqualTo(1L);
            assertThat(beta.pendingGeneration()).isNull();
            assertThat(beta.nextGeneration()).isEqualTo(2L);
        });
    }

    @Test
    void expiredReplicaWorkerCannotWriteBackAndOfflineServerCanRecover() {
        assertThat(repository.claimReplica(
                APP_ALPHA, REPOSITORY_ID, 1L, SERVER_A, "lease-old", NOW.plusSeconds(30), NOW)).isPresent();
        assertThat(repository.claimReplica(
                APP_ALPHA,
                REPOSITORY_ID,
                1L,
                SERVER_A,
                "lease-new",
                NOW.plusSeconds(91),
                NOW.plusSeconds(31))).isPresent();
        assertThat(repository.markReady(
                APP_ALPHA,
                REPOSITORY_ID,
                1L,
                SERVER_A,
                "lease-old",
                "feature/e2e",
                "commit-feature",
                NOW.plusSeconds(32),
                NOW.plusSeconds(32))).isFalse();
        assertThat(repository.markReady(
                APP_ALPHA,
                REPOSITORY_ID,
                1L,
                SERVER_A,
                "lease-new",
                "feature/e2e",
                "commit-feature",
                NOW.plusSeconds(32),
                NOW.plusSeconds(32))).isTrue();

        assertThat(repository.deferOfflineReplicas(
                APP_ALPHA, REPOSITORY_ID, 1L, Set.of(SERVER_A), NOW.plusSeconds(33)))
                .isEqualTo(1);
        LinuxServerId serverB = new LinuxServerId("server-b");
        repository.upsertTargets(APP_ALPHA, REPOSITORY_ID, 1L, Set.of(serverB), NOW.plusSeconds(34));
        assertThat(repository.claimReplica(
                APP_ALPHA,
                REPOSITORY_ID,
                1L,
                serverB,
                "lease-recovered",
                NOW.plusSeconds(90),
                NOW.plusSeconds(35))).isPresent();
        assertThat(repository.markReady(
                APP_ALPHA,
                REPOSITORY_ID,
                1L,
                serverB,
                "lease-recovered",
                "feature/e2e",
                "commit-feature",
                NOW.plusSeconds(36),
                NOW.plusSeconds(36))).isTrue();
        assertThat(repository.findReplicas(APP_ALPHA, REPOSITORY_ID, 1L))
                .filteredOn(replica -> replica.linuxServerId().equals(serverB))
                .singleElement()
                .extracting(replica -> replica.status())
                .isEqualTo(ReferenceRepositoryReplicaStatus.READY);
    }

    @Test
    void historicalGenerationIsRetiredOnlyAfterReadLeaseExpires() {
        assertThat(repository.completeGeneration(
                APP_ALPHA, REPOSITORY_ID, 1L, ReferenceRepositoryStatus.READY, null, NOW.minusSeconds(1)))
                .isTrue();
        String tokenHash = "read-lease-token-hash";
        WorkspaceId workspaceId = new WorkspaceId("wrk_history");
        assertThat(repository.saveReadLease(
                tokenHash,
                ADMIN_ID,
                workspaceId,
                APP_ALPHA,
                REPOSITORY_ID,
                1L,
                NOW.plusSeconds(60),
                NOW)).isTrue();
        ApplicationAutomationReferenceState migrated = repository.findState(APP_ALPHA, REPOSITORY_ID).orElseThrow();
        ApplicationAutomationReferenceGeneration second = generation(APP_ALPHA, 2L, "op-switch-2");
        assertThat(repository.reserveGeneration(second, 1L, migrated.lockVersion(), NOW)).contains(second);
        repository.upsertTargets(APP_ALPHA, REPOSITORY_ID, 2L, Set.of(SERVER_A), NOW);
        assertThat(repository.claimReplica(
                APP_ALPHA, REPOSITORY_ID, 2L, SERVER_A, "lease-second", NOW.plusSeconds(60), NOW)).isPresent();
        assertThat(repository.markReady(
                APP_ALPHA,
                REPOSITORY_ID,
                2L,
                SERVER_A,
                "lease-second",
                "release/next",
                "commit-next",
                NOW.plusSeconds(1),
                NOW.plusSeconds(1))).isTrue();
        assertThat(repository.completeGeneration(
                APP_ALPHA, REPOSITORY_ID, 2L, ReferenceRepositoryStatus.READY, null, NOW.plusSeconds(2)))
                .isTrue();
        assertThat(repository.saveReadLease(
                "late-token-hash",
                ADMIN_ID,
                workspaceId,
                APP_ALPHA,
                REPOSITORY_ID,
                1L,
                NOW.plusSeconds(60),
                NOW.plusSeconds(3))).isFalse();

        assertThat(repository.renewReadLease(
                tokenHash,
                ADMIN_ID,
                workspaceId,
                APP_ALPHA,
                REPOSITORY_ID,
                1L,
                NOW.plusSeconds(120),
                NOW.plusSeconds(30))).isTrue();
        assertThat(repository.findRetirableGenerations(NOW.plusSeconds(31), 10)).isEmpty();

        assertThat(repository.deleteExpiredReadLeases(NOW.plusSeconds(121))).isEqualTo(1);
        RunId runId = new RunId("run_historical_generation");
        jdbcClient.sql("insert into runs(run_id) values (:runId)")
                .param("runId", runId.value())
                .update();
        ApplicationAutomationReferenceRunLease runLease = new ApplicationAutomationReferenceRunLease(
                APP_ALPHA, REPOSITORY_ID, 1L, SERVER_A);
        repository.replaceRunLeases(runId, List.of(runLease), NOW.plusSeconds(121));
        assertThat(repository.findRetirableGenerations(NOW.plusSeconds(121), 10)).isEmpty();
        repository.deleteRunLeases(runId);
        assertThat(repository.findRetirableGenerations(NOW.plusSeconds(121), 10))
                .extracting(ApplicationAutomationReferenceGeneration::generation)
                .containsExactly(1L);
        assertThat(repository.retireGeneration(APP_ALPHA, REPOSITORY_ID, 1L, NOW.plusSeconds(122))).isTrue();
        assertThatThrownBy(() -> repository.replaceRunLeases(
                        new RunId("run_retired_generation"), List.of(runLease), NOW.plusSeconds(123)))
                .hasMessageContaining("配置已切换");
        assertThat(repository.findRetiredReplicas(SERVER_A, 10))
                .extracting(replica -> replica.generation())
                .containsExactly(1L);
        assertThat(repository.deleteRetiredReplica(APP_ALPHA, REPOSITORY_ID, 1L, SERVER_A)).isTrue();
    }

    private ApplicationAutomationReferenceGeneration generation(
            ApplicationId appId, long generation, String operationId) {
        return new ApplicationAutomationReferenceGeneration(
                appId,
                REPOSITORY_ID,
                generation,
                "release/next",
                "src/e2e",
                "下一代自动化引用",
                false,
                "commit-next",
                AutomationReferenceGenerationStatus.SYNCHRONIZING,
                AutomationReferenceOperationType.CONFIGURE,
                ADMIN_ID,
                operationId,
                "trace-next",
                null,
                null,
                NOW,
                NOW);
    }
}
