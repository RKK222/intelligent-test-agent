package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceBinding;
import com.enterprise.testagent.domain.workspace.ExperienceWorkspaceRepository;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.persistence.mybatis.ExperienceWorkspaceMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisExperienceWorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 使用真实 PostgreSQL 验证体验迁移升级和并发幂等登记，避免 H2 的 MERGE 语义掩盖 ON CONFLICT 竞争。
 */
@Testcontainers(disabledWithoutDocker = true)
class MyBatisExperienceWorkspacePostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-09T14:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    private static JdbcClient jdbc;
    private static ExperienceWorkspaceRepository repository;
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("20260809120000")
                .load()
                .migrate();
        jdbc = JdbcClient.create(dataSource);
        assertThat(tableExists("experience_workspace_bindings")).isFalse();

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(tableExists("experience_workspace_bindings")).isTrue();
        assertThat(jdbc.sql("""
                        select parameter_value, platform, editable
                        from common_parameters
                        where parameter_english = 'OPENCODE_EXPERIENCE_WORKSPACE_DIR'
                        """)
                .query((resultSet, rowNum) -> List.of(
                        resultSet.getString("parameter_value"),
                        resultSet.getString("platform"),
                        resultSet.getBoolean("editable")))
                .single()).containsExactly("${SYS_DATA_ROOT_DIR}/agent-opencode/workspace/experience", "all", true);
        assertThat(jdbc.sql("""
                        select checksum from flyway_schema_history
                        where version = '20260812104911' and success = true
                        """)
                .query(Integer.class)
                .single()).isNotZero();

        SqlSessionTemplate template = new SqlSessionTemplate(sqlSessionFactory(dataSource));
        context = new AnnotationConfigApplicationContext();
        context.register(TransactionTestConfiguration.class);
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(
                PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(dataSource));
        context.registerBean(
                MyBatisExperienceWorkspaceRepository.class,
                () -> new MyBatisExperienceWorkspaceRepository(
                        template.getMapper(ExperienceWorkspaceMapper.class)));
        context.refresh();
        repository = context.getBean(ExperienceWorkspaceRepository.class);
        assertThat(AopUtils.isAopProxy(repository)).isTrue();
    }

    @AfterAll
    static void tearDown() {
        if (context != null) context.close();
    }

    @BeforeEach
    void clearExperienceRows() {
        jdbc.sql("delete from experience_workspace_bindings").update();
        jdbc.sql("delete from workspaces where workspace_id like 'wrk_exp_pg_%'").update();
    }

    @Test
    void concurrentOpenOnOneServerCreatesOneWorkspaceAndOneCurrentBinding() throws Exception {
        Workspace workspace = workspace(
                "wrk_exp_pg_server_a", "/srv/experience-a", "server-a", "trace_concurrent", NOW);
        int workers = 12;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < workers; index++) {
                int worker = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    while (true) {
                        Optional<ExperienceWorkspaceBinding> expected =
                                repository.findCurrentByLinuxServerId("server-a");
                        if (expected.filter(binding -> binding.workspaceId().equals(workspace.workspaceId())
                                        && binding.configuredParameterValue().equals("${ROOT}/experience-a")).isPresent()
                                || repository.registerCurrentIfUnchanged(
                                        workspace, "${ROOT}/experience-a", "trace_" + worker, expected)) {
                            break;
                        }
                    }
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.sql("select count(*) from workspaces where workspace_id = 'wrk_exp_pg_server_a'")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("select count(*) from experience_workspace_bindings where linux_server_id = 'server-a'")
                .query(Long.class).single()).isOne();
        assertThat(repository.findCurrentByLinuxServerId("server-a"))
                .get()
                .extracting(ExperienceWorkspaceBinding::workspaceId)
                .isEqualTo(workspace.workspaceId());
    }

    @Test
    void differentServersStayIndependentAndDirectoryChangeRetainsHistoricalWorkspace() {
        Workspace serverAFirst = workspace(
                "wrk_exp_pg_server_a", "/srv/experience-a", "server-a", "trace_a", NOW);
        Workspace serverB = workspace(
                "wrk_exp_pg_server_b", "/srv/experience-b", "server-b", "trace_b", NOW);
        assertThat(repository.registerCurrentIfUnchanged(
                serverAFirst, "${ROOT}/experience-a", "trace_a", Optional.empty())).isTrue();
        assertThat(repository.registerCurrentIfUnchanged(
                serverB, "${ROOT}/experience-b", "trace_b", Optional.empty())).isTrue();

        Workspace serverASecond = workspace(
                "wrk_exp_pg_server_a_next",
                "/srv/experience-a-next",
                "server-a",
                "trace_a_next",
                NOW.plusSeconds(60));
        ExperienceWorkspaceBinding serverAExpected =
                repository.findCurrentByLinuxServerId("server-a").orElseThrow();
        assertThat(repository.registerCurrentIfUnchanged(
                serverASecond,
                "${ROOT}/experience-a-next",
                "trace_a_next",
                Optional.of(serverAExpected))).isTrue();

        assertThat(jdbc.sql("select count(*) from experience_workspace_bindings")
                .query(Long.class).single()).isEqualTo(2L);
        assertThat(jdbc.sql("select count(*) from workspaces where workspace_id like 'wrk_exp_pg_%'")
                .query(Long.class).single()).isEqualTo(3L);
        assertThat(repository.findCurrentByLinuxServerId("server-a"))
                .get()
                .satisfies(binding -> {
                    assertThat(binding.workspaceId()).isEqualTo(serverASecond.workspaceId());
                    assertThat(binding.configuredParameterValue()).isEqualTo("${ROOT}/experience-a-next");
                    assertThat(binding.createdAt()).isEqualTo(NOW);
                    assertThat(binding.updatedAt()).isEqualTo(NOW.plusSeconds(60));
                });
        assertThat(repository.findCurrentByLinuxServerId("server-b"))
                .get()
                .extracting(ExperienceWorkspaceBinding::workspaceId)
                .isEqualTo(serverB.workspaceId());
    }

    @Test
    void staleJavaInstanceCannotOverwriteNewerConfigurationBinding() {
        Workspace original = workspace(
                "wrk_exp_pg_original", "/srv/experience-original", "server-a", "trace_original", NOW);
        assertThat(repository.registerCurrentIfUnchanged(
                original, "${ROOT}/experience-original", "trace_original", Optional.empty())).isTrue();
        ExperienceWorkspaceBinding staleSnapshot =
                repository.findCurrentByLinuxServerId("server-a").orElseThrow();
        Workspace current = workspace(
                "wrk_exp_pg_current",
                "/srv/experience-current",
                "server-a",
                "trace_current",
                NOW.plusSeconds(60));
        assertThat(repository.registerCurrentIfUnchanged(
                current,
                "${ROOT}/experience-current",
                "trace_current",
                Optional.of(staleSnapshot))).isTrue();
        Workspace lateOld = workspace(
                "wrk_exp_pg_late_old",
                "/srv/experience-old",
                "server-a",
                "trace_late_old",
                NOW.plusSeconds(120));

        assertThat(repository.registerCurrentIfUnchanged(
                lateOld,
                "${ROOT}/experience-old",
                "trace_late_old",
                Optional.of(staleSnapshot))).isFalse();
        assertThat(repository.findCurrentByLinuxServerId("server-a"))
                .get()
                .satisfies(binding -> {
                    assertThat(binding.workspaceId()).isEqualTo(current.workspaceId());
                    assertThat(binding.configuredParameterValue()).isEqualTo("${ROOT}/experience-current");
                });
    }

    private static boolean tableExists(String tableName) {
        return jdbc.sql("""
                        select count(*) from information_schema.tables
                        where table_schema = 'public' and table_name = :tableName
                        """)
                .param("tableName", tableName)
                .query(Long.class)
                .single() > 0;
    }

    private static Workspace workspace(
            String workspaceId,
            String rootPath,
            String linuxServerId,
            String traceId,
            Instant time) {
        return new Workspace(
                new WorkspaceId(workspaceId),
                "体验工作区",
                rootPath,
                WorkspaceStatus.ACTIVE,
                time,
                time,
                linuxServerId,
                traceId);
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        databaseIdProvider.setProperties(databaseIds);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setDatabaseIdProvider(databaseIdProvider);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factory.getObject();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionTestConfiguration {
    }
}
