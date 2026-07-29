package com.enterprise.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 使用真实 Spring Boot Flyway 初始化验证三套已部署 PostgreSQL 历史都能升级。 */
@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest {

    private static final String MAIN_LOCATION = "classpath:db/migration";
    private static final String COMPATIBILITY_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.LEGACY_TOOLBOX_MIGRATION_LOCATION;
    private static final String IDEMPOTENT_COMPATIBILITY_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.CURRENT_TOOLBOX_IDEMPOTENT_LOCATION;
    private static final String ENTERPRISE_BASELINE = "20260728160000";
    private static final String LEGACY_TOOLBOX_VERSION =
            DatabaseMigrationCompatibilityCustomizer.LEGACY_TOOLBOX_MIGRATION_VERSION;
    private static final String CURRENT_TOOLBOX_VERSION =
            DatabaseMigrationCompatibilityCustomizer.CURRENT_TOOLBOX_MIGRATION_VERSION;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    @Test
    void enterpriseBaselineUsesOnlyMainLocationAndMigratesInOrder() {
        DataSource dataSource = dataSource("toolbox_enterprise_baseline");
        migrateTo(dataSource, ENTERPRISE_BASELINE, MAIN_LOCATION);

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway))
                    .doesNotContain(COMPATIBILITY_LOCATION, IDEMPOTENT_COMPATIBILITY_LOCATION);
            assertThat(applied(dataSource, LEGACY_TOOLBOX_VERSION)).isFalse();
            assertThat(applied(dataSource, CURRENT_TOOLBOX_VERSION)).isTrue();
            assertThat(appliedChecksum(dataSource, CURRENT_TOOLBOX_VERSION))
                    .isEqualTo(DatabaseMigrationCompatibilityCustomizer.CURRENT_TOOLBOX_ENTERPRISE_CHECKSUM);
            assertToolboxTables(dataSource);
        });
    }

    @Test
    void appliedEnterpriseMigrationKeepsItsOriginalChecksum() {
        DataSource dataSource = dataSource("toolbox_enterprise_applied");
        migrateTo(dataSource, CURRENT_TOOLBOX_VERSION, MAIN_LOCATION);

        runBootFlyway(dataSource, flyway -> {
            assertThat(locationDescriptors(flyway))
                    .doesNotContain(COMPATIBILITY_LOCATION, IDEMPOTENT_COMPATIBILITY_LOCATION);
            assertThat(appliedChecksum(dataSource, CURRENT_TOOLBOX_VERSION))
                    .isEqualTo(DatabaseMigrationCompatibilityCustomizer.CURRENT_TOOLBOX_ENTERPRISE_CHECKSUM);
            assertToolboxTables(dataSource);
        });
    }

    @Test
    void appliedLegacyMigrationIsResolvedWithoutReapplyingCurrentVersion() {
        DataSource dataSource = dataSource("toolbox_legacy_baseline");
        migrateTo(dataSource, ENTERPRISE_BASELINE, MAIN_LOCATION, COMPATIBILITY_LOCATION);
        assertThat(applied(dataSource, LEGACY_TOOLBOX_VERSION)).isTrue();

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).contains(COMPATIBILITY_LOCATION);
            assertThat(locationDescriptors(flyway)).doesNotContain(IDEMPOTENT_COMPATIBILITY_LOCATION);
            assertThat(applied(dataSource, LEGACY_TOOLBOX_VERSION)).isTrue();
            assertThat(applied(dataSource, CURRENT_TOOLBOX_VERSION)).isFalse();
            assertToolboxTables(dataSource);
        });
    }

    @Test
    void appliedIdempotentCurrentMigrationIsResolvedByItsByteExactCompatibilityCopy() {
        DataSource dataSource = dataSource("toolbox_idempotent_current");
        migrateTo(dataSource, CURRENT_TOOLBOX_VERSION, MAIN_LOCATION);
        // 仅在测试 fixture 中模拟曾经误发并执行过的 SQL checksum；生产流程禁止改 history。
        overwriteAppliedChecksum(
                dataSource,
                CURRENT_TOOLBOX_VERSION,
                DatabaseMigrationCompatibilityCustomizer.CURRENT_TOOLBOX_IDEMPOTENT_CHECKSUM);

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).contains(IDEMPOTENT_COMPATIBILITY_LOCATION);
            assertThat(locationDescriptors(flyway)).doesNotContain(COMPATIBILITY_LOCATION);
            assertThat(appliedChecksum(dataSource, CURRENT_TOOLBOX_VERSION))
                    .isEqualTo(DatabaseMigrationCompatibilityCustomizer.CURRENT_TOOLBOX_IDEMPOTENT_CHECKSUM);
            assertToolboxTables(dataSource);
        });
    }

    @Test
    void unknownCurrentMigrationChecksumStillFailsClosed() {
        DataSource dataSource = dataSource("toolbox_unknown_current");
        migrateTo(dataSource, CURRENT_TOOLBOX_VERSION, MAIN_LOCATION);
        // 未知 checksum 只用于验证启动闸门，不能被兼容程序静默接受。
        overwriteAppliedChecksum(dataSource, CURRENT_TOOLBOX_VERSION, 123456789);

        bootFlywayRunner(dataSource).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("Migration checksum mismatch for migration version 20260728160800");
        });
    }

    /** 为每套历史创建独立 schema，避免测试之间共享 Flyway history。 */
    private static DataSource dataSource(String schema) {
        PGSimpleDataSource admin = postgresDataSource();
        JdbcClient.create(admin).sql("create schema " + schema).update();
        PGSimpleDataSource dataSource = postgresDataSource();
        dataSource.setCurrentSchema(schema);
        return dataSource;
    }

    private static PGSimpleDataSource postgresDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static void migrateTo(DataSource dataSource, String target, String... locations) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .target(target)
                .load()
                .migrate();
    }

    private static void runBootFlyway(
            DataSource dataSource,
            java.util.function.Consumer<Flyway> assertions) {
        bootFlywayRunner(dataSource).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(Flyway.class);
            assertions.accept(context.getBean(Flyway.class));
        });
    }

    private static ApplicationContextRunner bootFlywayRunner(DataSource dataSource) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withUserConfiguration(CompatibilityConfiguration.class)
                .withBean(DataSource.class, () -> dataSource)
                .withPropertyValues(
                        "spring.flyway.enabled=true",
                        "spring.flyway.locations=" + MAIN_LOCATION,
                        "spring.flyway.out-of-order=false");
    }

    private static String[] locationDescriptors(Flyway flyway) {
        return Arrays.stream(flyway.getConfiguration().getLocations())
                .map(location -> location.getDescriptor())
                .toArray(String[]::new);
    }

    private static boolean applied(DataSource dataSource, String version) {
        return JdbcClient.create(dataSource)
                .sql("select count(*) from flyway_schema_history where version = :version and success = true")
                .param("version", version)
                .query(Long.class)
                .single() == 1L;
    }

    private static Integer appliedChecksum(DataSource dataSource, String version) {
        return JdbcClient.create(dataSource)
                .sql("select checksum from flyway_schema_history where version = :version and success = true")
                .param("version", version)
                .query(Integer.class)
                .single();
    }

    private static void overwriteAppliedChecksum(DataSource dataSource, String version, int checksum) {
        int updated = JdbcClient.create(dataSource)
                .sql("update flyway_schema_history set checksum = :checksum where version = :version")
                .param("checksum", checksum)
                .param("version", version)
                .update();
        assertThat(updated).isEqualTo(1);
    }

    private static void assertToolboxTables(DataSource dataSource) {
        Long tableCount = JdbcClient.create(dataSource)
                .sql("""
                        select count(*)
                        from information_schema.tables
                        where table_schema = current_schema()
                          and table_name in (
                              'toolbox_tool_click_events',
                              'toolbox_tool_click_totals',
                              'toolbox_tool_user_click_states'
                          )
                        """)
                .query(Long.class)
                .single();
        assertThat(tableCount).isEqualTo(3L);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DatabaseMigrationCompatibilityCustomizer.class)
    static class CompatibilityConfiguration {
    }
}
