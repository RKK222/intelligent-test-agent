package com.enterprise.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.scanner.Scanner;
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

/** 使用真实 Spring Boot Flyway 初始化验证各套已部署 PostgreSQL 历史都能升级。 */
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
    private static final String LOBEHUB_COMPATIBILITY_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.LOBEHUB_FORWARD_COMPATIBILITY_LOCATION;
    private static final String LOBEHUB_RELEASE_COMPATIBILITY_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.LOBEHUB_RELEASE_FORWARD_COMPATIBILITY_LOCATION;
    private static final String LOBEHUB_MAIN_RESOURCE =
            "db/migration/V20260730090000__add_lobehub_model_gateway.sql";
    private static final String DEPLOYED_ENTERPRISE_BASELINE_COMMIT =
            "0352efa987219b9dde5c09e77b1eabfa719fc068";
    private static final String DEPLOYED_ENTERPRISE_BASELINE_MAX_VERSION = "20260801104000";
    private static final String DEPLOYED_CEC_BASELINE_MAX_VERSION = "20260804123000";
    private static final String SUPPORT_ACCESS_VERSION = "20260805132000";
    private static final String SKILL_HUB_CLASSIFICATION_VERSION = "20260806143000";
    private static final String PUBLIC_SKILL_HUB_SNAPSHOT_VERSION = "20260806190000";
    private static final String PUBLIC_SKILL_HUB_CLASSIFICATION_VERSION = "20260806190500";
    private static final String CURRENT_LOCAL_APPLIED_MAX_VERSION = "20260807230000";
    private static final String INTERNAL_MODEL_OBSERVABILITY_VERSION = "20260808143300";
    private static final String INTERNAL_MODEL_FIRST_TOKEN_VERSION = "20260808143301";
    private static final String INTERNAL_MODEL_STREAM_COMPLETE_VERSION = "20260808143302";
    private static final String INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_VERSION =
            DatabaseMigrationCompatibilityCustomizer.INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_VERSION;
    private static final String INTERNAL_MODEL_STREAM_COMPLETE_LEGACY_VERSION =
            DatabaseMigrationCompatibilityCustomizer.INTERNAL_MODEL_STREAM_COMPLETE_LEGACY_VERSION;
    private static final String INTERNAL_MODEL_OBSERVABILITY_OLD_RESOURCE =
            "db/migration/V20260807130134__create_internal_model_observability.sql";
    private static final String INTERNAL_MODEL_FIRST_TOKEN_OLD_RESOURCE =
            "db/migration/V20260807203000__add_internal_model_first_token_metrics.sql";
    private static final String INTERNAL_MODEL_STREAM_COMPLETE_OLD_RESOURCE =
            "db/migration/V20260807222227__add_internal_model_stream_complete_metrics.sql";
    private static final String INTERNAL_MODEL_LEGACY_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.INTERNAL_MODEL_LEGACY_COMPATIBILITY_LOCATION;
    private static final String RUN_RESEND_FORWARD_BEFORE_BATCH_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.RUN_RESEND_FORWARD_BEFORE_BATCH_LOCATION;
    private static final String RUN_RESEND_FORWARD_AFTER_BATCH_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.RUN_RESEND_FORWARD_AFTER_BATCH_LOCATION;
    private static final String RUN_RESEND_MAIN_RESOURCE =
            "db/migration/V20260807190000__create_run_resends.sql";
    private static final String RUN_RESEND_MIGRATION_VERSION =
            DatabaseMigrationCompatibilityCustomizer.RUN_RESEND_MIGRATION_VERSION;
    private static final String RUN_RESEND_FORWARD_BEFORE_BATCH_VERSION =
            DatabaseMigrationCompatibilityCustomizer.RUN_RESEND_FORWARD_BEFORE_BATCH_VERSION;
    private static final String RUN_RESEND_FORWARD_AFTER_BATCH_VERSION =
            DatabaseMigrationCompatibilityCustomizer.RUN_RESEND_FORWARD_AFTER_BATCH_VERSION;
    private static final String BATCH_SESSION_ATTRIBUTION_VERSION =
            DatabaseMigrationCompatibilityCustomizer.BATCH_SESSION_ATTRIBUTION_MIGRATION_VERSION;
    private static final String EXTERNAL_API_CREDENTIALS_VERSION =
            DatabaseMigrationCompatibilityCustomizer.EXTERNAL_API_CREDENTIALS_MIGRATION_VERSION;
    private static final String EXTERNAL_API_CREDENTIALS_MAIN_RESOURCE =
            "db/migration/V20260809110000__create_external_api_credentials.sql";
    private static final String QA_MEMORY_APPLIED_VERSION =
            DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_APPLIED_MIGRATION_VERSION;
    private static final String QA_MEMORY_APPLIED_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_APPLIED_COMPATIBILITY_LOCATION;
    private static final String QA_MEMORY_APPLIED_MAIN_RESOURCE =
            "db/migration/V20260809120000__create_qa_memory_governance.sql";
    private static final String QA_MEMORY_GENERALIZE_VERSION =
            DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_GENERALIZE_MIGRATION_VERSION;
    private static final String QA_MEMORY_IDENTITY_VERSION =
            DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_IDENTITY_MIGRATION_VERSION;
    private static final String QA_MEMORY_EXTENDED_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_EXTENDED_COMPATIBILITY_LOCATION;
    private static final String QA_MEMORY_GENERALIZE_MAIN_RESOURCE =
            "db/migration/V20260809230000__generalize_memory_and_embedding_profiles.sql";
    private static final String QA_MEMORY_IDENTITY_MAIN_RESOURCE =
            "db/migration/V20260810090000__enforce_qa_memory_identity.sql";
    private static final String QA_MEMORY_AFTER_SESSION_SHARE_FORWARD_VERSION =
            DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_AFTER_SESSION_SHARE_FORWARD_VERSION;
    private static final String QA_MEMORY_AFTER_SESSION_SHARE_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_AFTER_SESSION_SHARE_COMPATIBILITY_LOCATION;
    private static final String QA_MEMORY_AFTER_TOKEN_LATENCY_FORWARD_VERSION =
            DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_AFTER_TOKEN_LATENCY_FORWARD_VERSION;
    private static final String QA_MEMORY_AFTER_TOKEN_LATENCY_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_AFTER_TOKEN_LATENCY_COMPATIBILITY_LOCATION;
    private static final String EXTERNAL_API_FORWARD_VERSION =
            DatabaseMigrationCompatibilityCustomizer.EXTERNAL_API_FORWARD_MIGRATION_VERSION;
    private static final String EXTERNAL_API_FORWARD_LOCATION =
            DatabaseMigrationCompatibilityCustomizer.EXTERNAL_API_FORWARD_COMPATIBILITY_LOCATION;
    private static final String SESSION_SHARE_VERSION =
            DatabaseMigrationCompatibilityCustomizer.SESSION_SHARE_MIGRATION_VERSION;
    private static final String SESSION_SHARE_ATTRIBUTION_VERSION =
            DatabaseMigrationCompatibilityCustomizer.SESSION_SHARE_ATTRIBUTION_MIGRATION_VERSION;
    private static final String SESSION_SHARE_FORWARD_VERSION =
            DatabaseMigrationCompatibilityCustomizer.SESSION_SHARE_FORWARD_MIGRATION_VERSION;
    private static final String SESSION_SHARE_ATTRIBUTION_FORWARD_VERSION =
            DatabaseMigrationCompatibilityCustomizer.SESSION_SHARE_ATTRIBUTION_FORWARD_MIGRATION_VERSION;
    private static final String SESSION_SHARE_MAIN_RESOURCE =
            "db/migration/V20260809170000__session_shares_create_collaboration_share.sql";
    private static final String SESSION_SHARE_ATTRIBUTION_MAIN_RESOURCE =
            "db/migration/V20260809170001__session_messages_add_delegated_attribution.sql";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            // 全历史迁移断言较重，允许 Docker Desktop 冷启动时完成镜像初始化。
            .withStartupTimeout(Duration.ofMinutes(3));

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

        // 合并后的完整主链允许 QA Memory 与会话分享低版本按顺序共存，第二次启动不能误切到任一补偿链。
        runBootFlyway(dataSource, flyway -> {
            assertThat(locationDescriptors(flyway)).contains(QA_MEMORY_APPLIED_LOCATION);
            assertThat(locationDescriptors(flyway)).doesNotContain(
                    QA_MEMORY_EXTENDED_LOCATION,
                    QA_MEMORY_AFTER_SESSION_SHARE_LOCATION);
            assertThat(applied(dataSource, QA_MEMORY_APPLIED_VERSION)).isTrue();
            assertThat(applied(dataSource, QA_MEMORY_IDENTITY_VERSION)).isTrue();
            assertThat(applied(dataSource, SESSION_SHARE_ATTRIBUTION_VERSION)).isTrue();
            assertThat(applied(dataSource, SESSION_SHARE_FORWARD_VERSION)).isFalse();
            assertThat(applied(dataSource, QA_MEMORY_AFTER_SESSION_SHARE_FORWARD_VERSION)).isFalse();
            assertThat(qaMemorySchemaTableCount(dataSource)).isEqualTo(8L);
            assertSessionShareSchema(dataSource);
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

    @Test
    void deployedEnterpriseCommitUsesForwardCompatibilityMigration() {
        DataSource dataSource = dataSource("deployed_enterprise_0352efa");
        prepareDeployedEnterpriseBaseline(dataSource);

        assertThat(applied(
                dataSource,
                DatabaseMigrationCompatibilityCustomizer.LOBEHUB_MODEL_GATEWAY_MIGRATION_VERSION))
                .isFalse();
        assertThat(applied(
                dataSource,
                DatabaseMigrationCompatibilityCustomizer.LOBEHUB_SPLIT_MARKER_VERSION))
                .isTrue();
        assertThat(applied(dataSource, DEPLOYED_ENTERPRISE_BASELINE_MAX_VERSION))
                .as("enterprise baseline %s must be reproduced before upgrading", DEPLOYED_ENTERPRISE_BASELINE_COMMIT)
                .isTrue();
        assertLobehubTablesAndParameters(dataSource, 0L);

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).contains(LOBEHUB_COMPATIBILITY_LOCATION);
            assertThat(locationDescriptors(flyway)).doesNotContain(LOBEHUB_RELEASE_COMPATIBILITY_LOCATION);
            assertThat(applied(
                    dataSource,
                    DatabaseMigrationCompatibilityCustomizer.LOBEHUB_MODEL_GATEWAY_MIGRATION_VERSION))
                    .isFalse();
            assertThat(applied(
                    dataSource,
                    DatabaseMigrationCompatibilityCustomizer.LOBEHUB_FORWARD_COMPATIBILITY_VERSION))
                    .isTrue();
            assertThat(applied(
                    dataSource,
                    DatabaseMigrationCompatibilityCustomizer.RELEASE_ROLLOUT_MIGRATION_VERSION))
                    .isTrue();
            assertLobehubTablesAndParameters(dataSource, 3L);
        });
    }

    @Test
    void deployedCecBaselineMigratesToCurrentHeadInOrder() {
        DataSource dataSource = dataSource("deployed_enterprise_cec4ccf");
        prepareDeployedEnterpriseBaseline(dataSource);

        // 先以真实兼容装配停在已部署 cec4ccf 的最高版本，再模拟新包的第二次启动升级。
        runBootFlywayTo(dataSource, DEPLOYED_CEC_BASELINE_MAX_VERSION, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(applied(
                    dataSource,
                    DatabaseMigrationCompatibilityCustomizer.LOBEHUB_FORWARD_COMPATIBILITY_VERSION))
                    .isTrue();
            assertThat(applied(dataSource, DEPLOYED_CEC_BASELINE_MAX_VERSION)).isTrue();
            assertThat(applied(dataSource, SUPPORT_ACCESS_VERSION)).isFalse();
        });

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(applied(dataSource, SUPPORT_ACCESS_VERSION)).isTrue();
            assertThat(applied(dataSource, SKILL_HUB_CLASSIFICATION_VERSION)).isTrue();
            assertThat(applied(dataSource, PUBLIC_SKILL_HUB_SNAPSHOT_VERSION)).isTrue();
            assertThat(applied(dataSource, PUBLIC_SKILL_HUB_CLASSIFICATION_VERSION)).isTrue();
            assertThat(applied(dataSource, EXTERNAL_API_CREDENTIALS_VERSION)).isTrue();
            assertThat(applied(dataSource, EXTERNAL_API_FORWARD_VERSION)).isFalse();
            assertCurrentReleaseTables(dataSource);
            assertExternalApiCredentialTables(dataSource);
        });
    }

    @Test
    void currentLocalBaselineAppliesIntegratedInternalModelMigrationsInOrder() {
        DataSource dataSource = dataSource("current_local_before_internal_model_observability");
        // 构造合并前已执行到批量会话版本、但从未执行可观测候选 migration 的真实本地历史。
        migrateWithoutResourceTo(
                dataSource,
                CURRENT_LOCAL_APPLIED_MAX_VERSION,
                INTERNAL_MODEL_OBSERVABILITY_OLD_RESOURCE,
                INTERNAL_MODEL_FIRST_TOKEN_OLD_RESOURCE,
                INTERNAL_MODEL_STREAM_COMPLETE_OLD_RESOURCE);

        assertThat(applied(dataSource, CURRENT_LOCAL_APPLIED_MAX_VERSION)).isTrue();
        assertThat(applied(dataSource, INTERNAL_MODEL_OBSERVABILITY_VERSION)).isFalse();
        assertThat(internalModelObservabilitySchemaObjectCount(dataSource)).isZero();

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(applied(dataSource, INTERNAL_MODEL_OBSERVABILITY_VERSION)).isTrue();
            assertThat(applied(dataSource, INTERNAL_MODEL_FIRST_TOKEN_VERSION)).isTrue();
            assertThat(applied(dataSource, INTERNAL_MODEL_STREAM_COMPLETE_VERSION)).isTrue();
            assertThat(applied(dataSource, INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_VERSION)).isTrue();
            assertThat(internalModelObservabilitySchemaObjectCount(dataSource)).isEqualTo(7L);
        });
    }

    @Test
    void currentInternalModelHistoryResumesAfterObservabilityMigration() {
        assertCurrentInternalModelHistoryResumes(
                "current_internal_model_after_observability",
                INTERNAL_MODEL_OBSERVABILITY_VERSION,
                false);
    }

    @Test
    void currentInternalModelHistoryResumesAfterFirstTokenMigration() {
        assertCurrentInternalModelHistoryResumes(
                "current_internal_model_after_first_token",
                INTERNAL_MODEL_FIRST_TOKEN_VERSION,
                true);
    }

    @Test
    void appliedLegacyInternalModelHistoryUsesCompatibilityAndForwardRunResendMigration() {
        DataSource dataSource = dataSource("legacy_internal_model_history");
        // 复现并行 worktree 已执行旧可观测版本、但撤销重发候选版本尚未执行的本地 history。
        migrateWithoutResourceTo(
                dataSource,
                INTERNAL_MODEL_STREAM_COMPLETE_LEGACY_VERSION,
                new String[] {MAIN_LOCATION, INTERNAL_MODEL_LEGACY_LOCATION},
                RUN_RESEND_MAIN_RESOURCE);

        assertThat(applied(dataSource, INTERNAL_MODEL_STREAM_COMPLETE_LEGACY_VERSION)).isTrue();
        assertThat(applied(dataSource, RUN_RESEND_MIGRATION_VERSION)).isFalse();
        assertThat(internalModelObservabilitySchemaObjectCount(dataSource)).isEqualTo(5L);

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).contains(
                    INTERNAL_MODEL_LEGACY_LOCATION,
                    RUN_RESEND_FORWARD_BEFORE_BATCH_LOCATION);
            assertThat(applied(dataSource, RUN_RESEND_FORWARD_BEFORE_BATCH_VERSION)).isTrue();
            assertThat(applied(dataSource, BATCH_SESSION_ATTRIBUTION_VERSION)).isTrue();
            assertThat(applied(dataSource, INTERNAL_MODEL_OBSERVABILITY_VERSION)).isFalse();
            assertThat(applied(dataSource, INTERNAL_MODEL_FIRST_TOKEN_VERSION)).isFalse();
            assertThat(applied(dataSource, INTERNAL_MODEL_STREAM_COMPLETE_VERSION)).isFalse();
            assertThat(applied(dataSource, INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_VERSION)).isTrue();
            assertThat(runResendSchemaObjectCount(dataSource)).isEqualTo(2L);
            assertThat(internalModelObservabilitySchemaObjectCount(dataSource)).isEqualTo(7L);
        });

        // 第二次启动必须继续解析已经落库的“批量前”补偿版本，不能因批量版本已存在误切到另一条路径。
        runBootFlyway(dataSource, flyway -> {
            assertThat(locationDescriptors(flyway)).contains(RUN_RESEND_FORWARD_BEFORE_BATCH_LOCATION);
            assertThat(locationDescriptors(flyway)).doesNotContain(RUN_RESEND_FORWARD_AFTER_BATCH_LOCATION);
            assertThat(applied(dataSource, RUN_RESEND_FORWARD_BEFORE_BATCH_VERSION)).isTrue();
            assertThat(runResendSchemaObjectCount(dataSource)).isEqualTo(2L);
        });
    }

    @Test
    void missingRunResendAfterBatchUsesHigherForwardMigrationAndRemainsResolvable() {
        DataSource dataSource = dataSource("run_resend_missing_after_batch");
        migrateWithoutResourceTo(
                dataSource,
                BATCH_SESSION_ATTRIBUTION_VERSION,
                RUN_RESEND_MAIN_RESOURCE);

        assertThat(applied(dataSource, BATCH_SESSION_ATTRIBUTION_VERSION)).isTrue();
        assertThat(applied(dataSource, RUN_RESEND_MIGRATION_VERSION)).isFalse();
        assertThat(runResendSchemaObjectCount(dataSource)).isZero();

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).contains(RUN_RESEND_FORWARD_AFTER_BATCH_LOCATION);
            assertThat(locationDescriptors(flyway)).doesNotContain(RUN_RESEND_FORWARD_BEFORE_BATCH_LOCATION);
            assertThat(applied(dataSource, RUN_RESEND_FORWARD_AFTER_BATCH_VERSION)).isTrue();
            assertThat(applied(dataSource, RUN_RESEND_MIGRATION_VERSION)).isFalse();
            assertThat(runResendSchemaObjectCount(dataSource)).isEqualTo(2L);
        });

        runBootFlyway(dataSource, flyway -> {
            assertThat(locationDescriptors(flyway)).contains(RUN_RESEND_FORWARD_AFTER_BATCH_LOCATION);
            assertThat(locationDescriptors(flyway)).doesNotContain(RUN_RESEND_FORWARD_BEFORE_BATCH_LOCATION);
            assertThat(applied(dataSource, RUN_RESEND_FORWARD_AFTER_BATCH_VERSION)).isTrue();
            assertThat(runResendSchemaObjectCount(dataSource)).isEqualTo(2L);
        });
    }

    @Test
    void missingLobehubMigrationAfterReleaseRolloutUsesHigherCompatibilityMigration() {
        DataSource dataSource = dataSource("lobehub_missing_after_release_rollout");
        migrateTo(dataSource, "20260728210000", MAIN_LOCATION);
        migrateWithoutResourceTo(
                dataSource,
                DatabaseMigrationCompatibilityCustomizer.RELEASE_ROLLOUT_MIGRATION_VERSION,
                LOBEHUB_MAIN_RESOURCE);

        assertThat(applied(
                dataSource,
                DatabaseMigrationCompatibilityCustomizer.LOBEHUB_MODEL_GATEWAY_MIGRATION_VERSION))
                .isFalse();
        assertThat(applied(
                dataSource,
                DatabaseMigrationCompatibilityCustomizer.RELEASE_ROLLOUT_MIGRATION_VERSION))
                .isTrue();
        assertThat(applied(
                dataSource,
                DatabaseMigrationCompatibilityCustomizer.LOBEHUB_FORWARD_COMPATIBILITY_VERSION))
                .isFalse();
        assertLobehubTablesAndParameters(dataSource, 0L);

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).contains(LOBEHUB_RELEASE_COMPATIBILITY_LOCATION);
            assertThat(locationDescriptors(flyway)).doesNotContain(LOBEHUB_COMPATIBILITY_LOCATION);
            assertThat(applied(
                    dataSource,
                    DatabaseMigrationCompatibilityCustomizer.LOBEHUB_RELEASE_FORWARD_COMPATIBILITY_VERSION))
                    .isTrue();
            assertLobehubTablesAndParameters(dataSource, 3L);
        });
    }

    @Test
    void appliedReleaseSessionShareHistoryUsesQaMemoryForwardMigration() {
        DataSource dataSource = dataSource("release_session_share_before_qa_memory");
        // 复现 release 分支已执行外部 API 与会话分享、但从未执行较低版本 QA Memory 的真实历史。
        migrateWithoutResourceTo(
                dataSource,
                SESSION_SHARE_ATTRIBUTION_VERSION,
                QA_MEMORY_APPLIED_MAIN_RESOURCE,
                QA_MEMORY_GENERALIZE_MAIN_RESOURCE,
                QA_MEMORY_IDENTITY_MAIN_RESOURCE);

        assertThat(applied(dataSource, SESSION_SHARE_ATTRIBUTION_VERSION)).isTrue();
        assertThat(applied(dataSource, QA_MEMORY_APPLIED_VERSION)).isFalse();
        assertThat(qaMemorySchemaTableCount(dataSource)).isZero();

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).contains(QA_MEMORY_AFTER_SESSION_SHARE_LOCATION);
            assertThat(locationDescriptors(flyway)).doesNotContain(
                    QA_MEMORY_APPLIED_LOCATION,
                    QA_MEMORY_EXTENDED_LOCATION);
            assertThat(applied(dataSource, QA_MEMORY_APPLIED_VERSION)).isFalse();
            assertThat(applied(dataSource, QA_MEMORY_GENERALIZE_VERSION)).isFalse();
            assertThat(applied(dataSource, QA_MEMORY_IDENTITY_VERSION)).isFalse();
            assertThat(applied(dataSource, QA_MEMORY_AFTER_SESSION_SHARE_FORWARD_VERSION)).isTrue();
            assertThat(qaMemorySchemaTableCount(dataSource)).isEqualTo(8L);
            assertSessionShareSchema(dataSource);
        });

        // 已落库的补偿版本必须在后续启动继续由同一隔离 location 解析。
        runBootFlyway(dataSource, flyway -> {
            assertThat(locationDescriptors(flyway)).contains(QA_MEMORY_AFTER_SESSION_SHARE_LOCATION);
            assertThat(applied(dataSource, QA_MEMORY_AFTER_SESSION_SHARE_FORWARD_VERSION)).isTrue();
            assertThat(qaMemorySchemaTableCount(dataSource)).isEqualTo(8L);
        });
    }

    @Test
    void appliedLatestReleaseHistoryUsesHigherQaMemoryForwardMigration() {
        DataSource dataSource = dataSource("release_token_latency_before_qa_memory");
        // 复现 release 已执行通知中心与当前最高迁移、但尚未合入较低版本 QA Memory 的历史。
        migrateWithoutResourceTo(
                dataSource,
                INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_VERSION,
                QA_MEMORY_APPLIED_MAIN_RESOURCE,
                QA_MEMORY_GENERALIZE_MAIN_RESOURCE,
                QA_MEMORY_IDENTITY_MAIN_RESOURCE);

        assertThat(applied(dataSource, SESSION_SHARE_ATTRIBUTION_VERSION)).isTrue();
        assertThat(applied(dataSource, INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_VERSION)).isTrue();
        assertThat(applied(dataSource, QA_MEMORY_APPLIED_VERSION)).isFalse();
        assertThat(applied(dataSource, QA_MEMORY_AFTER_SESSION_SHARE_FORWARD_VERSION)).isFalse();
        assertThat(qaMemorySchemaTableCount(dataSource)).isZero();
        assertUserNotificationSchema(dataSource);

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).contains(QA_MEMORY_AFTER_TOKEN_LATENCY_LOCATION);
            assertThat(locationDescriptors(flyway)).doesNotContain(
                    QA_MEMORY_AFTER_SESSION_SHARE_LOCATION,
                    QA_MEMORY_APPLIED_LOCATION,
                    QA_MEMORY_EXTENDED_LOCATION);
            assertThat(applied(dataSource, QA_MEMORY_APPLIED_VERSION)).isFalse();
            assertThat(applied(dataSource, QA_MEMORY_GENERALIZE_VERSION)).isFalse();
            assertThat(applied(dataSource, QA_MEMORY_IDENTITY_VERSION)).isFalse();
            assertThat(applied(dataSource, QA_MEMORY_AFTER_SESSION_SHARE_FORWARD_VERSION)).isFalse();
            assertThat(applied(dataSource, QA_MEMORY_AFTER_TOKEN_LATENCY_FORWARD_VERSION)).isTrue();
            assertThat(qaMemorySchemaTableCount(dataSource)).isEqualTo(8L);
            assertSessionShareSchema(dataSource);
        });

        // 后续启动必须继续解析新隔离 location，不能回退到较低的旧补偿版本。
        runBootFlyway(dataSource, flyway -> {
            assertThat(locationDescriptors(flyway)).contains(QA_MEMORY_AFTER_TOKEN_LATENCY_LOCATION);
            assertThat(locationDescriptors(flyway)).doesNotContain(
                    QA_MEMORY_AFTER_SESSION_SHARE_LOCATION);
            assertThat(applied(dataSource, QA_MEMORY_AFTER_TOKEN_LATENCY_FORWARD_VERSION)).isTrue();
            assertThat(qaMemorySchemaTableCount(dataSource)).isEqualTo(8L);
        });
    }

    @Test
    void appliedQaMemoryHistoryUsesByteExactCompatibilityAndExternalApiForwardMigration() {
        DataSource dataSource = dataSource("qa_memory_applied_before_external_api");
        // 复现个人持久库已执行 QA Memory 同号候选、但从未出现外部 API 主 migration 的真实历史。
        migrateWithoutResourceTo(
                dataSource,
                QA_MEMORY_APPLIED_VERSION,
                new String[] {MAIN_LOCATION, QA_MEMORY_APPLIED_LOCATION},
                EXTERNAL_API_CREDENTIALS_MAIN_RESOURCE);

        assertThat(appliedChecksum(dataSource, QA_MEMORY_APPLIED_VERSION))
                .isEqualTo(DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_APPLIED_MIGRATION_CHECKSUM);
        assertThat(applied(dataSource, EXTERNAL_API_CREDENTIALS_VERSION)).isFalse();
        assertThat(applied(dataSource, EXTERNAL_API_FORWARD_VERSION)).isFalse();
        assertThat(qaMemorySchemaTableCount(dataSource)).isEqualTo(8L);

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).contains(
                    QA_MEMORY_APPLIED_LOCATION,
                    EXTERNAL_API_FORWARD_LOCATION);
            assertThat(applied(dataSource, EXTERNAL_API_CREDENTIALS_VERSION)).isFalse();
            assertThat(applied(dataSource, EXTERNAL_API_FORWARD_VERSION)).isTrue();
            assertExternalApiCredentialTables(dataSource);
        });

        // 第二次启动继续解析相同分叉，不能回头加载更低版本的主 migration。
        runBootFlyway(dataSource, flyway -> {
            assertThat(locationDescriptors(flyway)).contains(
                    QA_MEMORY_APPLIED_LOCATION,
                    EXTERNAL_API_FORWARD_LOCATION);
            assertThat(applied(dataSource, EXTERNAL_API_FORWARD_VERSION)).isTrue();
            assertExternalApiCredentialTables(dataSource);
        });
    }

    @Test
    void appliedExtendedQaMemoryHistoryUsesByteExactCompatibilityAndAllForwardMigrations() {
        DataSource dataSource = dataSource("qa_memory_extended_before_release_migrations");
        // 复现当前 .env.test 保留库：三条 QA Memory history 已执行，外部 API 与会话分享均未执行。
        migrateWithoutResourceTo(
                dataSource,
                QA_MEMORY_IDENTITY_VERSION,
                new String[] {MAIN_LOCATION, QA_MEMORY_APPLIED_LOCATION, QA_MEMORY_EXTENDED_LOCATION},
                EXTERNAL_API_CREDENTIALS_MAIN_RESOURCE,
                SESSION_SHARE_MAIN_RESOURCE,
                SESSION_SHARE_ATTRIBUTION_MAIN_RESOURCE);

        assertThat(appliedChecksum(dataSource, QA_MEMORY_GENERALIZE_VERSION))
                .isEqualTo(DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_GENERALIZE_MIGRATION_CHECKSUM);
        assertThat(appliedChecksum(dataSource, QA_MEMORY_IDENTITY_VERSION))
                .isEqualTo(DatabaseMigrationCompatibilityCustomizer.QA_MEMORY_IDENTITY_MIGRATION_CHECKSUM);
        assertThat(applied(dataSource, EXTERNAL_API_CREDENTIALS_VERSION)).isFalse();
        assertThat(applied(dataSource, SESSION_SHARE_VERSION)).isFalse();
        assertThat(applied(dataSource, SESSION_SHARE_ATTRIBUTION_VERSION)).isFalse();

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).contains(
                    QA_MEMORY_APPLIED_LOCATION,
                    QA_MEMORY_EXTENDED_LOCATION,
                    EXTERNAL_API_FORWARD_LOCATION);
            assertThat(applied(dataSource, EXTERNAL_API_FORWARD_VERSION)).isTrue();
            assertThat(applied(dataSource, SESSION_SHARE_FORWARD_VERSION)).isTrue();
            assertThat(applied(dataSource, SESSION_SHARE_ATTRIBUTION_FORWARD_VERSION)).isTrue();
            assertThat(applied(dataSource, EXTERNAL_API_CREDENTIALS_VERSION)).isFalse();
            assertThat(applied(dataSource, SESSION_SHARE_VERSION)).isFalse();
            assertThat(applied(dataSource, SESSION_SHARE_ATTRIBUTION_VERSION)).isFalse();
            assertExternalApiCredentialTables(dataSource);
            assertSessionShareSchema(dataSource);
        });

        // 第二次启动必须继续解析相同的高版本补偿资源，不能重新暴露低版本主 migration。
        runBootFlyway(dataSource, flyway -> {
            assertThat(locationDescriptors(flyway)).contains(
                    QA_MEMORY_APPLIED_LOCATION,
                    QA_MEMORY_EXTENDED_LOCATION,
                    EXTERNAL_API_FORWARD_LOCATION);
            assertThat(applied(dataSource, SESSION_SHARE_ATTRIBUTION_FORWARD_VERSION)).isTrue();
            assertSessionShareSchema(dataSource);
        });
    }

    @Test
    void partialExtendedQaMemoryHistoryResumesBeforeApplyingForwardMigrations() {
        DataSource dataSource = dataSource("qa_memory_extended_after_generalize");
        migrateWithoutResourceTo(
                dataSource,
                QA_MEMORY_GENERALIZE_VERSION,
                new String[] {MAIN_LOCATION, QA_MEMORY_APPLIED_LOCATION, QA_MEMORY_EXTENDED_LOCATION},
                EXTERNAL_API_CREDENTIALS_MAIN_RESOURCE,
                SESSION_SHARE_MAIN_RESOURCE,
                SESSION_SHARE_ATTRIBUTION_MAIN_RESOURCE);

        assertThat(applied(dataSource, QA_MEMORY_GENERALIZE_VERSION)).isTrue();
        assertThat(applied(dataSource, QA_MEMORY_IDENTITY_VERSION)).isFalse();

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(applied(dataSource, QA_MEMORY_IDENTITY_VERSION)).isTrue();
            assertThat(applied(dataSource, EXTERNAL_API_FORWARD_VERSION)).isTrue();
            assertThat(applied(dataSource, SESSION_SHARE_ATTRIBUTION_FORWARD_VERSION)).isTrue();
            assertSessionShareSchema(dataSource);
        });
    }

    @Test
    void unknownExtendedQaMemoryChecksumStillFailsClosed() {
        DataSource dataSource = dataSource("qa_memory_extended_unknown_checksum");
        migrateWithoutResourceTo(
                dataSource,
                QA_MEMORY_IDENTITY_VERSION,
                new String[] {MAIN_LOCATION, QA_MEMORY_APPLIED_LOCATION, QA_MEMORY_EXTENDED_LOCATION},
                EXTERNAL_API_CREDENTIALS_MAIN_RESOURCE,
                SESSION_SHARE_MAIN_RESOURCE,
                SESSION_SHARE_ATTRIBUTION_MAIN_RESOURCE);
        overwriteAppliedChecksum(dataSource, QA_MEMORY_GENERALIZE_VERSION, 987654321);

        bootFlywayRunner(dataSource).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("检测到未知的 QA Memory 扩展 migration checksum");
        });
    }

    @Test
    void unknownQaMemoryChecksumStillFailsClosed() {
        DataSource dataSource = dataSource("qa_memory_unknown_checksum");
        migrateWithoutResourceTo(
                dataSource,
                QA_MEMORY_APPLIED_VERSION,
                new String[] {MAIN_LOCATION, QA_MEMORY_APPLIED_LOCATION},
                EXTERNAL_API_CREDENTIALS_MAIN_RESOURCE);
        // 只在测试 schema 中伪造未知 checksum，证明兼容程序不会静默接受其它同号 SQL。
        overwriteAppliedChecksum(dataSource, QA_MEMORY_APPLIED_VERSION, 987654321);

        bootFlywayRunner(dataSource).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("检测到未知的 QA Memory migration checksum");
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

    /** 仅在测试库构造“后续版本已执行但目标 migration 缺失”的真实 Flyway history。 */
    private static void migrateWithoutResourceTo(
            DataSource dataSource,
            String target,
            String... excludedResources) {
        migrateWithoutResourceTo(dataSource, target, new String[] {MAIN_LOCATION}, excludedResources);
    }

    private static void migrateWithoutResourceTo(
            DataSource dataSource,
            String target,
            String[] locations,
            String... excludedResources) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .target(target);
        ResourceProvider defaultProvider = new Scanner<>(
                JavaMigration.class, configuration, configuration.getLocations());
        configuration.resourceProvider(new ResourceProvider() {
            @Override
            public LoadableResource getResource(String name) {
                LoadableResource resource = defaultProvider.getResource(name);
                return resource != null && matchesAny(resource, excludedResources) ? null : resource;
            }

            @Override
            public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
                return defaultProvider.getResources(prefix, suffixes).stream()
                        .filter(resource -> !matchesAny(resource, excludedResources))
                        .toList();
            }
        });
        configuration.load().migrate();
    }

    private static boolean matchesAny(LoadableResource resource, String[] excludedResources) {
        return Arrays.stream(excludedResources).anyMatch(excluded -> matches(resource, excluded));
    }

    /** 构造已部署 0352efa 的字节级历史，供旧基线与当前现网基线升级用例共同复用。 */
    private static void prepareDeployedEnterpriseBaseline(DataSource dataSource) {
        migrateTo(dataSource, "20260728210000", MAIN_LOCATION);
        // 0352efa 的主 migration 最高为 V20260801104000，且不包含后来合入的 LobeHub 低版本 migration。
        migrateWithoutResourceTo(
                dataSource,
                DEPLOYED_ENTERPRISE_BASELINE_MAX_VERSION,
                LOBEHUB_MAIN_RESOURCE);
    }

    private static void runBootFlyway(
            DataSource dataSource,
            java.util.function.Consumer<Flyway> assertions) {
        bootFlywayRunner(dataSource).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(Flyway.class);
            assertions.accept(context.getBean(Flyway.class));
            // 每套已知已部署 history 升级到当前 HEAD 后都必须具备通知中心结构。
            assertUserNotificationSchema(dataSource);
        });
    }

    /** 模拟当前 migration 链在进程中断后重启，剩余版本必须按默认顺序继续执行。 */
    private static void assertCurrentInternalModelHistoryResumes(
            String schema,
            String appliedThroughVersion,
            boolean firstTokenAlreadyApplied) {
        DataSource dataSource = dataSource(schema);
        migrateTo(dataSource, appliedThroughVersion, MAIN_LOCATION);

        assertThat(applied(dataSource, INTERNAL_MODEL_OBSERVABILITY_VERSION)).isTrue();
        assertThat(applied(dataSource, INTERNAL_MODEL_FIRST_TOKEN_VERSION))
                .isEqualTo(firstTokenAlreadyApplied);
        assertThat(applied(dataSource, INTERNAL_MODEL_STREAM_COMPLETE_VERSION)).isFalse();

        runBootFlyway(dataSource, flyway -> {
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(locationDescriptors(flyway)).doesNotContain(INTERNAL_MODEL_LEGACY_LOCATION);
            assertThat(applied(dataSource, INTERNAL_MODEL_OBSERVABILITY_VERSION)).isTrue();
            assertThat(applied(dataSource, INTERNAL_MODEL_FIRST_TOKEN_VERSION)).isTrue();
            assertThat(applied(dataSource, INTERNAL_MODEL_STREAM_COMPLETE_VERSION)).isTrue();
            assertThat(applied(dataSource, INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_VERSION)).isTrue();
            assertThat(internalModelObservabilitySchemaObjectCount(dataSource)).isEqualTo(7L);
        });
    }

    /** 用生产相同的兼容装配升级到指定已部署版本，避免只测试空库直达当前 HEAD。 */
    private static void runBootFlywayTo(
            DataSource dataSource,
            String target,
            java.util.function.Consumer<Flyway> assertions) {
        bootFlywayRunner(dataSource)
                .withPropertyValues("spring.flyway.target=" + target)
                .run(context -> {
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

    private static void assertLobehubTablesAndParameters(DataSource dataSource, long expectedTableCount) {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        Long tableCount = jdbc.sql("""
                        select count(*)
                        from information_schema.tables
                        where table_schema = current_schema()
                          and table_name in (
                              'internal_model_provider_models',
                              'internal_model_provider_model_probes',
                              'model_gateway_usage_daily'
                          )
                        """)
                .query(Long.class)
                .single();
        Long parameterCount = jdbc.sql("""
                        select count(*)
                        from common_parameters
                        where parameter_id in (
                            'param_lobehub_enabled_all',
                            'param_lobehub_base_url_all',
                            'param_lobehub_email_domain_all',
                            'param_lobehub_owner_auth_all'
                        )
                        """)
                .query(Long.class)
                .single();
        assertThat(tableCount).isEqualTo(expectedTableCount);
        assertThat(parameterCount).isEqualTo(expectedTableCount == 0L ? 0L : 4L);
    }

    private static void assertCurrentReleaseTables(DataSource dataSource) {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        Long tableCount = jdbc.sql("""
                        select count(*)
                        from information_schema.tables
                        where table_schema = current_schema()
                          and table_name in (
                              'support_access_grants',
                              'support_access_audit_events',
                              'agent_skill_hub_builtin_revisions',
                              'agent_skill_hub_builtin_state',
                              'agent_skill_hub_builtin_classifications'
                          )
                        """)
                .query(Long.class)
                .single();
        Long classificationColumnCount = jdbc.sql("""
                        select count(*)
                        from information_schema.columns
                        where table_schema = current_schema()
                          and table_name = 'agent_skill_hub_assets'
                          and column_name in (
                              'skill_category',
                              'skill_subcategory',
                              'classified_by_user_id',
                              'classified_at'
                          )
                        """)
                .query(Long.class)
                .single();
        assertThat(tableCount).isEqualTo(5L);
        assertThat(classificationColumnCount).isEqualTo(4L);
    }

    private static void assertExternalApiCredentialTables(DataSource dataSource) {
        Long tableCount = JdbcClient.create(dataSource)
                .sql("""
                        select count(*)
                        from information_schema.tables
                        where table_schema = current_schema()
                          and table_name in (
                              'external_api_credentials',
                              'external_api_credential_scopes'
                          )
                        """)
                .query(Long.class)
                .single();
        assertThat(tableCount).isEqualTo(2L);
    }

    private static void assertSessionShareSchema(DataSource dataSource) {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        Long tableCount = jdbc.sql("""
                        select count(*)
                        from information_schema.tables
                        where table_schema = current_schema()
                          and table_name in (
                              'session_shares',
                              'session_share_memberships',
                              'session_share_audit_events'
                          )
                        """)
                .query(Long.class)
                .single();
        Long attributionColumnCount = jdbc.sql("""
                        select count(*)
                        from information_schema.columns
                        where table_schema = current_schema()
                          and (
                              (table_name = 'session_messages'
                                  and column_name in ('sender_unified_auth_id', 'sent_by_shared_user'))
                              or (table_name = 'runs'
                                  and column_name in ('message_sender_user_id', 'active_session_id'))
                          )
                        """)
                .query(Long.class)
                .single();
        assertThat(tableCount).isEqualTo(3L);
        assertThat(attributionColumnCount).isEqualTo(4L);
    }

    private static void assertUserNotificationSchema(DataSource dataSource) {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        Long tableCount = jdbc.sql("""
                        select count(*)
                        from information_schema.tables
                        where table_schema = current_schema()
                          and table_name = 'user_notifications'
                        """)
                .query(Long.class)
                .single();
        Long actionColumnCount = jdbc.sql("""
                        select count(*)
                        from information_schema.columns
                        where table_schema = current_schema()
                          and table_name = 'user_notifications'
                          and column_name in (
                              'notification_id',
                              'recipient_user_id',
                              'action_type',
                              'action_target_id',
                              'status',
                              'read_at',
                              'expires_at',
                              'trace_id'
                          )
                        """)
                .query(Long.class)
                .single();
        assertThat(tableCount).isEqualTo(1L);
        assertThat(actionColumnCount).isEqualTo(8L);
    }

    private static long qaMemorySchemaTableCount(DataSource dataSource) {
        return JdbcClient.create(dataSource)
                .sql("""
                        select count(*)
                        from information_schema.tables
                        where table_schema = current_schema()
                          and table_name in (
                              'qa_memories',
                              'qa_memory_evidence',
                              'qa_memory_reviews',
                              'qa_memory_learning_outbox',
                              'qa_memory_run_usage',
                              'qa_memory_whitelist',
                              'qa_memory_skill_proposals',
                              'qa_memory_settings'
                          )
                        """)
                .query(Long.class)
                .single();
    }

    /** 三张可观测表及首 Token、流完成、ITL/TPOT 原始量必须在同一正常 Flyway 链中落地。 */
    private static long internalModelObservabilitySchemaObjectCount(DataSource dataSource) {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        Long tableCount = jdbc.sql("""
                        select count(*)
                        from information_schema.tables
                        where table_schema = current_schema()
                          and table_name in (
                              'internal_model_call_records',
                              'internal_model_call_stats_hourly',
                              'internal_model_probe_status'
                          )
                        """)
                .query(Long.class)
                .single();
        Long columnCount = jdbc.sql("""
                        select count(*)
                        from information_schema.columns
                        where table_schema = current_schema()
                          and table_name = 'internal_model_call_records'
                          and column_name in (
                              'first_token_ms',
                              'stream_complete_ms',
                              'last_token_ms',
                              'output_token_count'
                          )
                        """)
                .query(Long.class)
                .single();
        return tableCount + columnCount;
    }

    private static long runResendSchemaObjectCount(DataSource dataSource) {
        return JdbcClient.create(dataSource)
                .sql("""
                        select count(*)
                        from information_schema.tables
                        where table_schema = current_schema()
                          and table_name in ('run_resends', 'run_resend_session_locks')
                        """)
                .query(Long.class)
                .single();
    }

    private static boolean matches(LoadableResource resource, String expectedResource) {
        String relativePath = normalize(resource.getRelativePath());
        String absolutePath = normalize(resource.getAbsolutePath());
        String absolutePathOnDisk = normalize(resource.getAbsolutePathOnDisk());
        return expectedResource.equals(relativePath)
                || absolutePath.endsWith("/" + expectedResource)
                || absolutePath.contains("!/" + expectedResource)
                || absolutePathOnDisk.endsWith("/" + expectedResource)
                || absolutePathOnDisk.contains("!/" + expectedResource);
    }

    private static String normalize(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DatabaseMigrationCompatibilityCustomizer.class)
    static class CompatibilityConfiguration {
    }
}
