package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.xxljob.admin.XxlJobMigrationCompatibilityCustomizer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用真实 MySQL 8.4 验证独立 migration location、空库初始化和任务基线。 */
@Testcontainers(disabledWithoutDocker = true)
class XxlJobMysqlMigrationTest {

    private static final String MAIN_MIGRATION_LOCATION = "classpath:xxl-job/db/migration";
    private static final String ANALYTICS_V12_COMPATIBILITY_LOCATION =
            "classpath:xxl-job/db/migration-compat/analytics-v12-applied";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("xxl_job")
            .withUsername("xxl_job")
            .withPassword("xxl_job_local");

    @BeforeAll
    static void migrateTwice() {
        Flyway flyway = compatibleFlyway(MYSQL.getJdbcUrl());
        assertThat(flyway.migrate().success).isTrue();
        assertThat(flyway.migrate().success).isTrue();
    }

    @Test
    void keepsMysqlMigrationsOutsidePlatformPostgresFlywayLocation() {
        assertThat(new ClassPathResource("xxl-job/db/migration/V1__xxl_job_3_4_2_base_schema.sql").exists())
                .isTrue();
        assertThat(new ClassPathResource("db/migration/xxl-job/V1__xxl_job_3_4_2_base_schema.sql").exists())
                .isFalse();
        assertThat(new ClassPathResource(
                "xxl-job/db/migration-compat/analytics-v12-applied/"
                        + "V12__register_analytics_clickhouse_ingestion_task.sql").exists())
                .isTrue();
        assertThat(new ClassPathResource(
                "xxl-job/db/migration/V12__register_analytics_clickhouse_ingestion_task.sql").exists())
                .isFalse();
    }

    @Test
    void initializesExecutorAndFifteenPlatformTasksWithoutLocalAdmin() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(singleInt(statement, "select count(*) from xxl_job_group where app_name='test-agent-backend'"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_group where app_name='test-agent-backend' and address_type=0 and address_list is null"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key is not null"))
                    .isEqualTo(15);
            assertThat(singleInt(statement, "select count(*) from xxl_job_user"))
                    .isZero();
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where executor_route_strategy='ROUND' and executor_block_strategy='DISCARD_LATER' and misfire_strategy='DO_NOTHING' and executor_fail_retry_count=0"))
                    .isEqualTo(15);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='opencode-runtime.analytics-ingestion' and schedule_conf='0 * * * * ? *' and trigger_status=1"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='opencode-runtime.night-execution-dispatch' and schedule_conf='0 0/1 * * * ? *' and trigger_status=1"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='workspace-management.app-source-cleanup' and schedule_conf='0 0/1 * * * ? *' and executor_param like '%GLOBAL_MUTEX%' and trigger_status=1"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='workspace-management.personal-workspace-relocation' and schedule_conf='0 0/30 * * * ? *' and executor_param like '%GLOBAL_MUTEX%' and trigger_status=1"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='opencode-runtime.inactive-user-process-cleanup' and schedule_conf='0 0 2 * * ? *' and executor_param like '%GLOBAL_MUTEX%' and trigger_status=1"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='configuration-management.scm-git-name-sync' and schedule_conf='0 10 4 * * ? *' and executor_param like '%GLOBAL_MUTEX%' and trigger_status=1"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='workspace-management.git-access-inspection' and schedule_conf='0 0 0/2 * * ? *' and executor_param like '%GLOBAL_MUTEX%' and trigger_status=1"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where executor_param like '%executionAffinity%' or executor_param like '%linuxServerId%'"))
                    .isZero();
        }
    }

    @Test
    void upgradesExecutedV8WithoutChangingItsHistory() throws Exception {
        String schema = "xxl_job_v8_upgrade";
        createDatabase(schema);
        String schemaUrl = MYSQL.getJdbcUrl().replace("/xxl_job", "/" + schema);

        Flyway.configure()
                .dataSource(schemaUrl, MYSQL.getUsername(), MYSQL.getPassword())
                .locations(MAIN_MIGRATION_LOCATION)
                .target("8")
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(
                schemaUrl, MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(singleInt(statement, "select count(*) from flyway_schema_history where success=1"))
                    .isEqualTo(8);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='workspace-management.personal-workspace-relocation' and schedule_conf='0 0/30 * * * ? *'"))
                    .isEqualTo(1);
        }

        Flyway flyway = compatibleFlyway(schemaUrl);
        assertThat(flyway.migrate().success).isTrue();
        try (Connection connection = DriverManager.getConnection(
                schemaUrl, MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(singleInt(statement, "select count(*) from flyway_schema_history where success=1"))
                    .isEqualTo(14);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='workspace-management.personal-workspace-relocation' and schedule_conf='0 0/30 * * * ? *' and trigger_next_time=0"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='opencode-runtime.inactive-user-process-cleanup' and schedule_conf='0 0 2 * * ? *'"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='opencode-runtime.internal-model-probe' and schedule_conf='0 */5 * * * ? *'"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='opencode-runtime.internal-model-observability-retention' and schedule_conf='0 30 3 * * ? *'"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='opencode-runtime.analytics-ingestion' and schedule_conf='0 * * * * ? *'"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='configuration-management.scm-git-name-sync' and schedule_conf='0 10 4 * * ? *'"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='workspace-management.git-access-inspection' and schedule_conf='0 0 0/2 * * ? *'"))
                    .isEqualTo(1);
        }
    }

    @Test
    void upgradesExecutedAnalyticsV12HistoryWithoutChangingItsChecksum() throws Exception {
        String schema = "xxl_job_analytics_v12_upgrade";
        createDatabase(schema);
        String schemaUrl = MYSQL.getJdbcUrl().replace("/xxl_job", "/" + schema);

        Flyway.configure()
                .dataSource(schemaUrl, MYSQL.getUsername(), MYSQL.getPassword())
                .locations(MAIN_MIGRATION_LOCATION)
                .target("11")
                .load()
                .migrate();
        // 用原始隔离资源构造 dev 已执行 V12 的真实历史，不改写 checksum。
        Flyway.configure()
                .dataSource(schemaUrl, MYSQL.getUsername(), MYSQL.getPassword())
                .locations(ANALYTICS_V12_COMPATIBILITY_LOCATION)
                .validateOnMigrate(false)
                .load()
                .migrate();

        assertThat(compatibleFlyway(schemaUrl).migrate().success).isTrue();
        assertCompatibleV12Upgrade(
                schemaUrl, XxlJobMigrationCompatibilityCustomizer.ANALYTICS_V12_CHECKSUM);
    }

    @Test
    void upgradesExecutedScmV12HistoryWithoutChangingItsChecksum() throws Exception {
        String schema = "xxl_job_scm_v12_upgrade";
        createDatabase(schema);
        String schemaUrl = MYSQL.getJdbcUrl().replace("/xxl_job", "/" + schema);

        Flyway.configure()
                .dataSource(schemaUrl, MYSQL.getUsername(), MYSQL.getPassword())
                .locations(MAIN_MIGRATION_LOCATION)
                .target("12")
                .load()
                .migrate();

        assertThat(compatibleFlyway(schemaUrl).migrate().success).isTrue();
        assertCompatibleV12Upgrade(
                schemaUrl, XxlJobMigrationCompatibilityCustomizer.SCM_GIT_NAME_SYNC_V12_CHECKSUM);
    }

    @Test
    void rejectsUnknownExecutedV12Checksum() throws Exception {
        String schema = "xxl_job_unknown_v12";
        createDatabase(schema);
        String schemaUrl = MYSQL.getJdbcUrl().replace("/xxl_job", "/" + schema);
        Flyway.configure()
                .dataSource(schemaUrl, MYSQL.getUsername(), MYSQL.getPassword())
                .locations(MAIN_MIGRATION_LOCATION)
                .target("12")
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(
                schemaUrl, MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("update flyway_schema_history set checksum=0 where version='12'");
        }

        assertThatThrownBy(() -> compatibleFlyway(schemaUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未知的 XXL V12 migration checksum");
    }

    @Test
    void concurrentAdminMigrationsSerializeOnFreshSchema() throws Exception {
        String schema = "xxl_job_concurrent";
        createDatabase(schema);

        String schemaUrl = MYSQL.getJdbcUrl().replace("/xxl_job", "/" + schema);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(
                () -> migrateAfterBarrier(schemaUrl, ready, start));
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(
                () -> migrateAfterBarrier(schemaUrl, ready, start));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(first.get(60, TimeUnit.SECONDS)).isTrue();
        assertThat(second.get(60, TimeUnit.SECONDS)).isTrue();
        try (Connection connection = DriverManager.getConnection(
                schemaUrl, MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(singleInt(statement, "select count(*) from flyway_schema_history where success=1"))
                    .isEqualTo(14);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key is not null"))
                    .isEqualTo(15);
        }
    }

    private static void createDatabase(String schema) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl().replace("/xxl_job", "/mysql"), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            statement.execute("GRANT ALL PRIVILEGES ON `" + schema + "`.* TO 'xxl_job'@'%'");
        }
    }

    private static boolean migrateAfterBarrier(
            String jdbcUrl,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                return false;
            }
            return compatibleFlyway(jdbcUrl)
                    .migrate()
                    .success;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Flyway compatibleFlyway(String jdbcUrl) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(jdbcUrl, MYSQL.getUsername(), MYSQL.getPassword())
                .locations(MAIN_MIGRATION_LOCATION);
        new XxlJobMigrationCompatibilityCustomizer().customize(configuration);
        return configuration.load();
    }

    private static void assertCompatibleV12Upgrade(String schemaUrl, int expectedChecksum)
            throws Exception {
        try (Connection connection = DriverManager.getConnection(
                schemaUrl, MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(singleInt(statement, "select count(*) from flyway_schema_history where success=1"))
                    .isEqualTo(14);
            assertThat(singleInt(statement, "select checksum from flyway_schema_history where version='12'"))
                    .isEqualTo(expectedChecksum);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='opencode-runtime.analytics-ingestion'"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='configuration-management.scm-git-name-sync'"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='workspace-management.git-access-inspection'"))
                    .isEqualTo(1);
        }
    }

    private static int singleInt(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
