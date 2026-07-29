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

/** 使用真实 Spring Boot Flyway 初始化验证两套 PostgreSQL 历史都能升级。 */
@Testcontainers(disabledWithoutDocker = true)
class DatabaseMigrationCompatibilityCustomizerPostgresqlIntegrationTest {

    private static final String MAIN_LOCATION = "classpath:db/migration";
    private static final String COMPATIBILITY_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.LEGACY_TOOLBOX_MIGRATION_LOCATION;
    private static final String ENTERPRISE_BASELINE = "20260728160000";
    private static final String LEGACY_TOOLBOX_VERSION =
            DatabaseMigrationCompatibilityCustomizer.LEGACY_TOOLBOX_MIGRATION_VERSION;
    private static final String CURRENT_TOOLBOX_VERSION = "20260728160800";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    @Test
    void enterpriseBaselineUsesOnlyMainLocationAndMigratesInOrder() {
        DataSource dataSource = dataSource("toolbox_enterprise_baseline");
        migrateTo(dataSource, ENTERPRISE_BASELINE, MAIN_LOCATION);

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).doesNotContain(COMPATIBILITY_LOCATION);
            assertThat(applied(dataSource, LEGACY_TOOLBOX_VERSION)).isFalse();
            assertThat(applied(dataSource, CURRENT_TOOLBOX_VERSION)).isTrue();
            assertToolboxTables(dataSource);
        });
    }

    @Test
    void appliedLegacyMigrationIsResolvedBeforeBootFlywayValidation() {
        DataSource dataSource = dataSource("toolbox_legacy_baseline");
        migrateTo(dataSource, ENTERPRISE_BASELINE, MAIN_LOCATION, COMPATIBILITY_LOCATION);
        assertThat(applied(dataSource, LEGACY_TOOLBOX_VERSION)).isTrue();

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).contains(COMPATIBILITY_LOCATION);
            assertThat(applied(dataSource, LEGACY_TOOLBOX_VERSION)).isTrue();
            assertThat(applied(dataSource, CURRENT_TOOLBOX_VERSION)).isTrue();
            assertToolboxTables(dataSource);
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
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
                .withUserConfiguration(CompatibilityConfiguration.class)
                .withBean(DataSource.class, () -> dataSource)
                .withPropertyValues(
                        "spring.flyway.enabled=true",
                        "spring.flyway.locations=" + MAIN_LOCATION,
                        "spring.flyway.out-of-order=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(Flyway.class);
                    assertions.accept(context.getBean(Flyway.class));
                });
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
