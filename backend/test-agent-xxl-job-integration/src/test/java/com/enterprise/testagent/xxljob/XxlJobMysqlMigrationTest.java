package com.enterprise.testagent.xxljob;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
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

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("xxl_job")
            .withUsername("xxl_job")
            .withPassword("xxl_job_local");

    @BeforeAll
    static void migrateTwice() {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:xxl-job/db/migration")
                .load();
        assertThat(flyway.migrate().success).isTrue();
        assertThat(flyway.migrate().success).isTrue();
    }

    @Test
    void keepsMysqlMigrationsOutsidePlatformPostgresFlywayLocation() {
        assertThat(new ClassPathResource("xxl-job/db/migration/V1__xxl_job_3_4_2_base_schema.sql").exists())
                .isTrue();
        assertThat(new ClassPathResource("db/migration/xxl-job/V1__xxl_job_3_4_2_base_schema.sql").exists())
                .isFalse();
    }

    @Test
    void initializesExecutorAndSevenPlatformTasksWithoutLocalAdmin() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(singleInt(statement, "select count(*) from xxl_job_group where app_name='test-agent-backend'"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_group where app_name='test-agent-backend' and address_type=0 and address_list is null"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key is not null"))
                    .isEqualTo(7);
            assertThat(singleInt(statement, "select count(*) from xxl_job_user"))
                    .isZero();
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where executor_route_strategy='ROUND' and executor_block_strategy='DISCARD_LATER' and misfire_strategy='DO_NOTHING' and executor_fail_retry_count=0"))
                    .isEqualTo(7);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key='opencode-runtime.night-execution-dispatch' and schedule_conf='0 0/15 * * * ? *' and trigger_status=1"))
                    .isEqualTo(1);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where executor_param like '%executionAffinity%' or executor_param like '%linuxServerId%'"))
                    .isZero();
        }
    }

    @Test
    void concurrentAdminMigrationsSerializeOnFreshSchema() throws Exception {
        String schema = "xxl_job_concurrent";
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl().replace("/xxl_job", "/mysql"), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            statement.execute("GRANT ALL PRIVILEGES ON `" + schema + "`.* TO 'xxl_job'@'%'");
        }

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
                    .isEqualTo(4);
            assertThat(singleInt(statement, "select count(*) from xxl_job_info where platform_task_key is not null"))
                    .isEqualTo(7);
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
            return Flyway.configure()
                    .dataSource(jdbcUrl, MYSQL.getUsername(), MYSQL.getPassword())
                    .locations("classpath:xxl-job/db/migration")
                    .load()
                    .migrate()
                    .success;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static int singleInt(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
