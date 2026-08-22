package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 真实 PostgreSQL 验证旧自动化工作空间历史升级为应用级唯一引用配置。 */
@Testcontainers(disabledWithoutDocker = true)
class ApplicationAutomationReferenceMigrationPostgresqlIntegrationTest {

    private static final String PREVIOUS_HEAD = "20260820153926";
    private static final String MIGRATION_VERSION = "20260821113000";
    private static final String READ_LEASE_MIGRATION_VERSION = "20260822075000";
    private static final String ALIAS_MIGRATION_VERSION = "20260822103625";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withStartupTimeout(Duration.ofMinutes(3));

    @Test
    void upgradesDeployedHistoryAndCollapsesEachApplicationRepositoryToOneGeneration() {
        DataSource dataSource = dataSource();
        new ResourceDatabasePopulator(
                new ClassPathResource("fixtures/application-automation-reference-history.sql"))
                .execute(dataSource);

        // 夹具代表旧版本已经部署的业务表与数据，Flyway 从真实上一版 HEAD 向前升级。
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion(PREVIOUS_HEAD))
                .load()
                .migrate();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        assertThat(jdbc.sql("""
                        select count(*) from flyway_schema_history
                        where version = :version and success = true
                        """)
                .param("version", MIGRATION_VERSION)
                .query(Long.class)
                .single()).isEqualTo(1L);
        assertThat(jdbc.sql("select count(*) from application_automation_references")
                .query(Long.class).single()).isEqualTo(2L);
        assertThat(jdbc.sql("""
                        select count(*) from flyway_schema_history
                        where version = :version and success = true
                        """)
                .param("version", READ_LEASE_MIGRATION_VERSION)
                .query(Long.class)
                .single()).isEqualTo(1L);
        assertThat(jdbc.sql("select count(*) from application_automation_reference_read_leases")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("""
                        select count(*) from flyway_schema_history
                        where version = :version and success = true
                        """)
                .param("version", ALIAS_MIGRATION_VERSION)
                .query(Long.class)
                .single()).isEqualTo(1L);
        assertThat(jdbc.sql("""
                        select branch || ':' || directory_path || ':' || reference_alias
                        from application_automation_reference_generations
                        where app_id = 'app_alpha' and repository_id = 'repo_automation'
                        """)
                .query(String.class).single()).isEqualTo("feature/e2e:src/test:automation-automation-repo");
        assertThat(jdbc.sql("""
                        select count(*) from application_workspaces workspace
                        join code_repositories repository on repository.repository_id = workspace.repository_id
                        where repository.repository_type = 'AUTOMATION_CODE_REPOSITORY' and workspace.enabled
                        """)
                .query(Long.class).single()).isZero();
    }

    private static DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }
}
