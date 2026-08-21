package com.enterprise.testagent.app.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.sql.Connection;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.scanner.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.stereotype.Component;

/**
 * 在 Spring Boot 创建唯一 Flyway Bean 时解析历史迁移分叉。
 *
 * <p>工具盒子、LobeHub、内部模型可观测、撤销重发、QA Memory、体验工作区、本地客户端和运营 outbox
 * 迁移均形成过已知历史分叉。
 * 兼容程序只按已应用版本与 checksum 选择原始字节或更高版本补偿资源；未知 checksum、不完整历史和混合路径
 * 必须继续拒绝启动。
 */
@Component
public final class DatabaseMigrationCompatibilityCustomizer implements FlywayConfigurationCustomizer {

    private static final String POSTGRESQL_MIGRATION_LOCATION =
            "classpath:db/migration-postgresql";

    static final String LEGACY_TOOLBOX_MIGRATION_VERSION = "20260727203500";
    static final String LEGACY_TOOLBOX_MIGRATION_LOCATION =
            "classpath:db/migration-compat/toolbox";
    static final String CURRENT_TOOLBOX_MIGRATION_VERSION = "20260728160800";
    static final int CURRENT_TOOLBOX_ENTERPRISE_CHECKSUM = -1966404877;
    static final int CURRENT_TOOLBOX_IDEMPOTENT_CHECKSUM = -74327385;
    static final String CURRENT_TOOLBOX_IDEMPOTENT_LOCATION =
            "classpath:db/migration-compat/toolbox-current-idempotent";
    static final String LOBEHUB_MODEL_GATEWAY_MIGRATION_VERSION = "20260730090000";
    static final String LOBEHUB_SPLIT_MARKER_VERSION = "20260801093854";
    static final String LOBEHUB_FORWARD_COMPATIBILITY_VERSION = "20260802173416";
    static final String LOBEHUB_FORWARD_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/lobehub-missing";
    static final String RELEASE_ROLLOUT_MIGRATION_VERSION = "20260803133000";
    static final String LOBEHUB_RELEASE_FORWARD_COMPATIBILITY_VERSION = "20260803141754";
    static final String LOBEHUB_RELEASE_FORWARD_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/lobehub-missing-after-rollout";
    static final String INTERNAL_MODEL_OBSERVABILITY_LEGACY_VERSION = "20260807130134";
    static final String INTERNAL_MODEL_FIRST_TOKEN_LEGACY_VERSION = "20260807203000";
    static final String INTERNAL_MODEL_STREAM_COMPLETE_LEGACY_VERSION = "20260807222227";
    static final String INTERNAL_MODEL_OBSERVABILITY_VERSION = "20260808143300";
    static final String INTERNAL_MODEL_FIRST_TOKEN_VERSION = "20260808143301";
    static final String INTERNAL_MODEL_STREAM_COMPLETE_VERSION = "20260808143302";
    static final String INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_VERSION = "20260810234154";
    static final String INTERNAL_MODEL_LEGACY_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/internal-model-observability-legacy";
    static final String RUN_RESEND_MIGRATION_VERSION = "20260807190000";
    static final String RUN_RESEND_FORWARD_BEFORE_BATCH_VERSION = "20260807229999";
    static final String RUN_RESEND_FORWARD_AFTER_BATCH_VERSION = "20260808143303";
    static final String BATCH_SESSION_ATTRIBUTION_MIGRATION_VERSION = "20260807230000";
    static final String RUN_RESEND_FORWARD_BEFORE_BATCH_LOCATION =
            "classpath:db/migration-compat/run-resend-after-internal-model-before-batch";
    static final String RUN_RESEND_FORWARD_AFTER_BATCH_LOCATION =
            "classpath:db/migration-compat/run-resend-after-internal-model-after-batch";
    static final String EXTERNAL_API_CREDENTIALS_MIGRATION_VERSION = "20260809110000";
    static final String QA_MEMORY_APPLIED_MIGRATION_VERSION = "20260809120000";
    static final int QA_MEMORY_APPLIED_MIGRATION_CHECKSUM = 311175224;
    static final String QA_MEMORY_APPLIED_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/qa-memory-applied";
    static final String QA_MEMORY_GENERALIZE_MIGRATION_VERSION = "20260809230000";
    static final int QA_MEMORY_GENERALIZE_MIGRATION_CHECKSUM = -433275068;
    static final String QA_MEMORY_IDENTITY_MIGRATION_VERSION = "20260810090000";
    static final int QA_MEMORY_IDENTITY_MIGRATION_CHECKSUM = 572596329;
    static final String QA_MEMORY_EXTENDED_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/qa-memory-extended";
    static final String QA_MEMORY_AFTER_SESSION_SHARE_FORWARD_VERSION = "20260810173117";
    static final String QA_MEMORY_AFTER_SESSION_SHARE_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/qa-memory-after-session-share";
    static final String QA_MEMORY_AFTER_TOKEN_LATENCY_FORWARD_VERSION = "20260811170050";
    static final String QA_MEMORY_AFTER_TOKEN_LATENCY_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/qa-memory-after-token-latency-inputs";
    static final String EXTERNAL_API_FORWARD_MIGRATION_VERSION = "20260810110000";
    static final String EXTERNAL_API_FORWARD_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/external-api-after-qa-memory";
    static final String SESSION_SHARE_MIGRATION_VERSION = "20260809170000";
    static final String SESSION_SHARE_ATTRIBUTION_MIGRATION_VERSION = "20260809170001";
    static final String SESSION_SHARE_FORWARD_MIGRATION_VERSION = "20260810110001";
    static final String SESSION_SHARE_ATTRIBUTION_FORWARD_MIGRATION_VERSION = "20260810110002";
    static final String EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_VERSION = "20260809210000";
    static final int EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_CHECKSUM = -1300860043;
    static final String EXPERIENCE_WORKSPACE_APPLIED_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/experience-workspace-applied";
    static final String EXPERIENCE_WORKSPACE_FORWARD_MIGRATION_VERSION = "20260812104911";
    static final String LOCAL_CLIENT_RUNTIME_MIGRATION_VERSION = "20260811210453";
    static final String LOCAL_CLIENT_RUNTIME_FORWARD_MIGRATION_VERSION = "20260812202425";
    static final String LOCAL_CLIENT_RUNTIME_FORWARD_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/local-client-runtime-after-release";
    static final String LOCAL_CLIENT_RUNTIME_APPLIED_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/local-client-runtime-applied";
    static final String LOCAL_CLIENT_RUNTIME_ENTERPRISE_FORWARD_MIGRATION_VERSION = "20260818094330";
    static final String LOCAL_CLIENT_RUNTIME_ENTERPRISE_FORWARD_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/local-client-runtime-after-enterprise-release";
    static final String LOCAL_CLIENT_VERSION_MANAGEMENT_MIGRATION_VERSION = "20260820182024";
    static final String LOCAL_CLIENT_CREDENTIAL_REVEAL_MIGRATION_VERSION = "20260820202529";
    static final String LOCAL_CLIENT_VERSION_MANAGEMENT_FORWARD_MIGRATION_VERSION = "20260822013000";
    static final String LOCAL_CLIENT_VERSION_MANAGEMENT_FORWARD_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/local-client-version-management-after-automation";
    static final String ANALYTICS_OUTBOX_MIGRATION_VERSION = "20260813143000";
    static final String ANALYTICS_TRIGGER_MIGRATION_VERSION = "20260813143001";
    static final String ANALYTICS_OUTBOX_FORWARD_MIGRATION_VERSION = "20260814165300";
    static final String ANALYTICS_TRIGGER_FORWARD_MIGRATION_VERSION = "20260814165301";
    static final String ANALYTICS_AFTER_RELEASE_COMPATIBILITY_LOCATION =
            "classpath:db/migration-compat/analytics-after-release";

    private static final String CURRENT_TOOLBOX_MIGRATION_FILE =
            "V20260728160800__create_toolbox_click_tracking.sql";
    private static final String CURRENT_TOOLBOX_MAIN_RESOURCE =
            "db/migration/" + CURRENT_TOOLBOX_MIGRATION_FILE;
    private static final String LOBEHUB_MODEL_GATEWAY_MAIN_RESOURCE =
            "db/migration/V20260730090000__add_lobehub_model_gateway.sql";
    private static final String INTERNAL_MODEL_OBSERVABILITY_MAIN_RESOURCE =
            "db/migration/V20260808143300__create_internal_model_observability.sql";
    private static final String INTERNAL_MODEL_FIRST_TOKEN_MAIN_RESOURCE =
            "db/migration/V20260808143301__add_internal_model_first_token_metrics.sql";
    private static final String INTERNAL_MODEL_STREAM_COMPLETE_MAIN_RESOURCE =
            "db/migration/V20260808143302__add_internal_model_stream_complete_metrics.sql";
    private static final String RUN_RESEND_MAIN_RESOURCE =
            "db/migration/V20260807190000__create_run_resends.sql";
    private static final String EXTERNAL_API_CREDENTIALS_MAIN_RESOURCE =
            "db/migration/V20260809110000__create_external_api_credentials.sql";
    private static final String QA_MEMORY_APPLIED_MAIN_RESOURCE =
            "db/migration/V20260809120000__create_qa_memory_governance.sql";
    private static final String SESSION_SHARE_MAIN_RESOURCE =
            "db/migration/V20260809170000__session_shares_create_collaboration_share.sql";
    private static final String SESSION_SHARE_ATTRIBUTION_MAIN_RESOURCE =
            "db/migration/V20260809170001__session_messages_add_delegated_attribution.sql";
    private static final String QA_MEMORY_GENERALIZE_MAIN_RESOURCE =
            "db/migration/V20260809230000__generalize_memory_and_embedding_profiles.sql";
    private static final String QA_MEMORY_IDENTITY_MAIN_RESOURCE =
            "db/migration/V20260810090000__enforce_qa_memory_identity.sql";
    private static final String EXPERIENCE_WORKSPACE_APPLIED_MAIN_RESOURCE =
            "db/migration/V20260809210000__common_parameters_add_experience_workspace.sql";
    private static final String LOCAL_CLIENT_RUNTIME_MAIN_RESOURCE =
            "db/migration/V20260811210453__local_client_credentials_create_runtime.sql";
    private static final String LOCAL_CLIENT_VERSION_MANAGEMENT_MAIN_RESOURCE =
            "db/migration/V20260820182024__local_client_releases_create_version_management.sql";
    private static final String LOCAL_CLIENT_CREDENTIAL_REVEAL_MAIN_RESOURCE =
            "db/migration/V20260820202529__local_client_credentials_add_revealed_at.sql";
    private static final String ANALYTICS_OUTBOX_MAIN_RESOURCE =
            "db/migration/V20260813143000__analytics_event_outbox_create_pipeline.sql";
    private static final String ANALYTICS_TRIGGER_MAIN_RESOURCE =
            "db/migration-postgresql/V20260813143001__analytics_event_outbox_install_triggers.sql";

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DatabaseMigrationCompatibilityCustomizer.class);

    /**
     * 在 Flyway validate/migrate 之前按已应用版本追加隔离兼容路径。
     */
    @Override
    public void customize(FluentConfiguration configuration) {
        MigrationInfo[] appliedMigrations = appliedMigrations(configuration);
        boolean postgresql = isPostgresql(configuration);
        boolean legacyMigrationApplied = isMigrationApplied(
                appliedMigrations, LEGACY_TOOLBOX_MIGRATION_VERSION);
        Integer currentMigrationChecksum = appliedChecksum(
                appliedMigrations, CURRENT_TOOLBOX_MIGRATION_VERSION);
        boolean lobehubModelGatewayMigrationApplied = isMigrationApplied(
                appliedMigrations, LOBEHUB_MODEL_GATEWAY_MIGRATION_VERSION);
        boolean lobehubSplitMarkerApplied = isMigrationApplied(
                appliedMigrations, LOBEHUB_SPLIT_MARKER_VERSION);
        boolean lobehubForwardCompatibilityApplied = isMigrationApplied(
                appliedMigrations, LOBEHUB_FORWARD_COMPATIBILITY_VERSION);
        boolean releaseRolloutMigrationApplied = isMigrationApplied(
                appliedMigrations, RELEASE_ROLLOUT_MIGRATION_VERSION);
        boolean missingLobehubMigrationHistory = !lobehubModelGatewayMigrationApplied
                && lobehubSplitMarkerApplied;
        boolean needsReleaseForwardCompatibility = missingLobehubMigrationHistory
                && !lobehubForwardCompatibilityApplied
                && releaseRolloutMigrationApplied;
        boolean legacyInternalModelObservabilityApplied = isMigrationApplied(
                appliedMigrations, INTERNAL_MODEL_OBSERVABILITY_LEGACY_VERSION);
        boolean legacyInternalModelFirstTokenApplied = isMigrationApplied(
                appliedMigrations, INTERNAL_MODEL_FIRST_TOKEN_LEGACY_VERSION);
        boolean legacyInternalModelStreamCompleteApplied = isMigrationApplied(
                appliedMigrations, INTERNAL_MODEL_STREAM_COMPLETE_LEGACY_VERSION);
        boolean anyLegacyInternalModelMigrationApplied = legacyInternalModelObservabilityApplied
                || legacyInternalModelFirstTokenApplied
                || legacyInternalModelStreamCompleteApplied;
        boolean allLegacyInternalModelMigrationsApplied = legacyInternalModelObservabilityApplied
                && legacyInternalModelFirstTokenApplied
                && legacyInternalModelStreamCompleteApplied;
        if (anyLegacyInternalModelMigrationApplied && !allLegacyInternalModelMigrationsApplied) {
            throw new IllegalStateException(
                    "检测到不完整的内部模型可观测旧 migration history，拒绝自动兼容；请核对 flyway_schema_history");
        }

        boolean currentInternalModelObservabilityApplied = isMigrationApplied(
                appliedMigrations, INTERNAL_MODEL_OBSERVABILITY_VERSION);
        boolean currentInternalModelFirstTokenApplied = isMigrationApplied(
                appliedMigrations, INTERNAL_MODEL_FIRST_TOKEN_VERSION);
        boolean currentInternalModelStreamCompleteApplied = isMigrationApplied(
                appliedMigrations, INTERNAL_MODEL_STREAM_COMPLETE_VERSION);
        boolean internalModelTokenLatencyInputsApplied = isMigrationApplied(
                appliedMigrations, INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_VERSION);
        boolean anyCurrentInternalModelMigrationApplied = currentInternalModelObservabilityApplied
                || currentInternalModelFirstTokenApplied
                || currentInternalModelStreamCompleteApplied;
        if (allLegacyInternalModelMigrationsApplied && anyCurrentInternalModelMigrationApplied) {
            throw new IllegalStateException(
                    "检测到内部模型可观测新旧 migration 同时执行，拒绝自动兼容；请核对 flyway_schema_history");
        }

        boolean runResendMigrationApplied = isMigrationApplied(
                appliedMigrations, RUN_RESEND_MIGRATION_VERSION);
        boolean runResendForwardBeforeBatchApplied = isMigrationApplied(
                appliedMigrations, RUN_RESEND_FORWARD_BEFORE_BATCH_VERSION);
        boolean runResendForwardAfterBatchApplied = isMigrationApplied(
                appliedMigrations, RUN_RESEND_FORWARD_AFTER_BATCH_VERSION);
        if (runResendForwardBeforeBatchApplied && runResendForwardAfterBatchApplied) {
            throw new IllegalStateException(
                    "检测到撤销重发顺序补偿 migration 新旧路径同时执行，拒绝自动兼容；请核对 flyway_schema_history");
        }
        boolean batchSessionAttributionMigrationApplied = isMigrationApplied(
                appliedMigrations, BATCH_SESSION_ATTRIBUTION_MIGRATION_VERSION);
        boolean laterMigrationApplied = isMigrationApplied(
                appliedMigrations, INTERNAL_MODEL_STREAM_COMPLETE_LEGACY_VERSION)
                || batchSessionAttributionMigrationApplied
                || anyCurrentInternalModelMigrationApplied;
        boolean needsRunResendForwardCompatibility = !runResendMigrationApplied && laterMigrationApplied;
        if (runResendMigrationApplied
                && (runResendForwardBeforeBatchApplied || runResendForwardAfterBatchApplied)) {
            throw new IllegalStateException(
                    "检测到撤销重发主 migration 与顺序补偿 migration 同时执行，拒绝自动兼容；请核对 flyway_schema_history");
        }
        String runResendForwardLocation = null;
        String runResendForwardVersion = null;
        if (runResendForwardBeforeBatchApplied) {
            runResendForwardLocation = RUN_RESEND_FORWARD_BEFORE_BATCH_LOCATION;
            runResendForwardVersion = RUN_RESEND_FORWARD_BEFORE_BATCH_VERSION;
        } else if (runResendForwardAfterBatchApplied) {
            runResendForwardLocation = RUN_RESEND_FORWARD_AFTER_BATCH_LOCATION;
            runResendForwardVersion = RUN_RESEND_FORWARD_AFTER_BATCH_VERSION;
        } else if (needsRunResendForwardCompatibility) {
            boolean forwardAfterBatch = batchSessionAttributionMigrationApplied
                    || anyCurrentInternalModelMigrationApplied;
            runResendForwardLocation = forwardAfterBatch
                    ? RUN_RESEND_FORWARD_AFTER_BATCH_LOCATION
                    : RUN_RESEND_FORWARD_BEFORE_BATCH_LOCATION;
            runResendForwardVersion = forwardAfterBatch
                    ? RUN_RESEND_FORWARD_AFTER_BATCH_VERSION
                    : RUN_RESEND_FORWARD_BEFORE_BATCH_VERSION;
        }

        boolean externalApiCredentialsMigrationApplied = isMigrationApplied(
                appliedMigrations, EXTERNAL_API_CREDENTIALS_MIGRATION_VERSION);
        boolean qaMemoryMigrationApplied = isMigrationApplied(
                appliedMigrations, QA_MEMORY_APPLIED_MIGRATION_VERSION);
        Integer qaMemoryMigrationChecksum = appliedChecksum(
                appliedMigrations, QA_MEMORY_APPLIED_MIGRATION_VERSION);
        boolean externalApiForwardMigrationApplied = isMigrationApplied(
                appliedMigrations, EXTERNAL_API_FORWARD_MIGRATION_VERSION);
        boolean qaMemoryGeneralizeMigrationApplied = isMigrationApplied(
                appliedMigrations, QA_MEMORY_GENERALIZE_MIGRATION_VERSION);
        Integer qaMemoryGeneralizeMigrationChecksum = appliedChecksum(
                appliedMigrations, QA_MEMORY_GENERALIZE_MIGRATION_VERSION);
        boolean qaMemoryIdentityMigrationApplied = isMigrationApplied(
                appliedMigrations, QA_MEMORY_IDENTITY_MIGRATION_VERSION);
        Integer qaMemoryIdentityMigrationChecksum = appliedChecksum(
                appliedMigrations, QA_MEMORY_IDENTITY_MIGRATION_VERSION);
        if (qaMemoryMigrationApplied
                && !Objects.equals(qaMemoryMigrationChecksum, QA_MEMORY_APPLIED_MIGRATION_CHECKSUM)) {
            throw new IllegalStateException(
                    "检测到未知的 QA Memory migration checksum，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (qaMemoryGeneralizeMigrationApplied && !qaMemoryMigrationApplied) {
            throw new IllegalStateException(
                    "检测到 QA Memory 扩展 migration 缺少基础 history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (qaMemoryIdentityMigrationApplied && !qaMemoryGeneralizeMigrationApplied) {
            throw new IllegalStateException(
                    "检测到不完整的 QA Memory 扩展 migration history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (qaMemoryGeneralizeMigrationApplied
                && !Objects.equals(
                        qaMemoryGeneralizeMigrationChecksum,
                        QA_MEMORY_GENERALIZE_MIGRATION_CHECKSUM)) {
            throw new IllegalStateException(
                    "检测到未知的 QA Memory 扩展 migration checksum，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (qaMemoryIdentityMigrationApplied
                && !Objects.equals(
                        qaMemoryIdentityMigrationChecksum,
                        QA_MEMORY_IDENTITY_MIGRATION_CHECKSUM)) {
            throw new IllegalStateException(
                    "检测到未知的 QA Memory 身份 migration checksum，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (externalApiForwardMigrationApplied && !qaMemoryMigrationApplied) {
            throw new IllegalStateException(
                    "检测到外部 API 凭据顺序补偿 migration 缺少对应 QA Memory history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (externalApiCredentialsMigrationApplied && externalApiForwardMigrationApplied) {
            throw new IllegalStateException(
                    "检测到外部 API 凭据主 migration 与顺序补偿 migration 同时执行，拒绝自动兼容；请核对 flyway_schema_history");
        }
        boolean needsExternalApiForwardCompatibility =
                qaMemoryMigrationApplied && !externalApiCredentialsMigrationApplied;
        boolean sessionShareMigrationApplied = isMigrationApplied(
                appliedMigrations, SESSION_SHARE_MIGRATION_VERSION);
        boolean sessionShareAttributionMigrationApplied = isMigrationApplied(
                appliedMigrations, SESSION_SHARE_ATTRIBUTION_MIGRATION_VERSION);
        boolean sessionShareForwardMigrationApplied = isMigrationApplied(
                appliedMigrations, SESSION_SHARE_FORWARD_MIGRATION_VERSION);
        boolean sessionShareAttributionForwardMigrationApplied = isMigrationApplied(
                appliedMigrations, SESSION_SHARE_ATTRIBUTION_FORWARD_MIGRATION_VERSION);
        boolean experienceWorkspaceMigrationApplied = isMigrationApplied(
                appliedMigrations, EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_VERSION);
        Integer experienceWorkspaceMigrationChecksum = appliedChecksum(
                appliedMigrations, EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_VERSION);
        if (experienceWorkspaceMigrationApplied
                && !Objects.equals(
                        experienceWorkspaceMigrationChecksum,
                        EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_CHECKSUM)) {
            throw new IllegalStateException(
                    "检测到未知的体验工作区 migration checksum，拒绝自动兼容；请核对 flyway_schema_history");
        }
        boolean localClientRuntimeMigrationApplied = isMigrationApplied(
                appliedMigrations, LOCAL_CLIENT_RUNTIME_MIGRATION_VERSION);
        boolean localClientRuntimeForwardMigrationApplied = isMigrationApplied(
                appliedMigrations, LOCAL_CLIENT_RUNTIME_FORWARD_MIGRATION_VERSION);
        boolean localClientRuntimeEnterpriseForwardMigrationApplied = isMigrationApplied(
                appliedMigrations, LOCAL_CLIENT_RUNTIME_ENTERPRISE_FORWARD_MIGRATION_VERSION);
        int appliedLocalClientRuntimePaths = (localClientRuntimeMigrationApplied ? 1 : 0)
                + (localClientRuntimeForwardMigrationApplied ? 1 : 0)
                + (localClientRuntimeEnterpriseForwardMigrationApplied ? 1 : 0);
        if (appliedLocalClientRuntimePaths > 1) {
            throw new IllegalStateException(
                    "检测到本地客户端主 migration 与顺序补偿 migration 存在多条执行路径，拒绝自动兼容；请核对 flyway_schema_history");
        }
        boolean needsLocalClientRuntimeForwardCompatibility =
                !localClientRuntimeMigrationApplied
                        && !localClientRuntimeForwardMigrationApplied
                        && !localClientRuntimeEnterpriseForwardMigrationApplied
                        && hasAppliedMigrationAfter(
                                appliedMigrations, LOCAL_CLIENT_RUNTIME_MIGRATION_VERSION);
        // 旧前向版本只适用于最高 history 仍低于它的 release 基线；现场已经越过该版本时必须选择新的高版本路径。
        boolean needsLocalClientRuntimeEnterpriseForwardCompatibility =
                needsLocalClientRuntimeForwardCompatibility
                        && hasAppliedMigrationAfter(
                                appliedMigrations, LOCAL_CLIENT_RUNTIME_FORWARD_MIGRATION_VERSION);
        boolean localClientVersionManagementMigrationApplied = isMigrationApplied(
                appliedMigrations, LOCAL_CLIENT_VERSION_MANAGEMENT_MIGRATION_VERSION);
        boolean localClientCredentialRevealMigrationApplied = isMigrationApplied(
                appliedMigrations, LOCAL_CLIENT_CREDENTIAL_REVEAL_MIGRATION_VERSION);
        boolean localClientVersionManagementForwardMigrationApplied = isMigrationApplied(
                appliedMigrations, LOCAL_CLIENT_VERSION_MANAGEMENT_FORWARD_MIGRATION_VERSION);
        if (localClientCredentialRevealMigrationApplied && !localClientVersionManagementMigrationApplied) {
            throw new IllegalStateException(
                    "检测到本地客户端凭据展示 migration 缺少版本管理基础 history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (localClientVersionManagementMigrationApplied
                && !localClientCredentialRevealMigrationApplied
                && hasAppliedMigrationAfter(
                        appliedMigrations, LOCAL_CLIENT_CREDENTIAL_REVEAL_MIGRATION_VERSION)) {
            throw new IllegalStateException(
                    "检测到不完整的本地客户端版本管理 migration history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (localClientVersionManagementForwardMigrationApplied
                && (localClientVersionManagementMigrationApplied
                        || localClientCredentialRevealMigrationApplied)) {
            throw new IllegalStateException(
                    "检测到本地客户端版本管理主链与顺序补偿 migration 同时执行，拒绝自动兼容；请核对 flyway_schema_history");
        }
        boolean needsLocalClientVersionManagementForwardCompatibility =
                !localClientVersionManagementMigrationApplied
                        && !localClientCredentialRevealMigrationApplied
                        && !localClientVersionManagementForwardMigrationApplied
                        && hasAppliedMigrationAfter(
                                appliedMigrations, LOCAL_CLIENT_CREDENTIAL_REVEAL_MIGRATION_VERSION);
        boolean analyticsOutboxMigrationApplied = isMigrationApplied(
                appliedMigrations, ANALYTICS_OUTBOX_MIGRATION_VERSION);
        boolean analyticsTriggerMigrationApplied = isMigrationApplied(
                appliedMigrations, ANALYTICS_TRIGGER_MIGRATION_VERSION);
        boolean analyticsOutboxForwardMigrationApplied = isMigrationApplied(
                appliedMigrations, ANALYTICS_OUTBOX_FORWARD_MIGRATION_VERSION);
        boolean analyticsTriggerForwardMigrationApplied = isMigrationApplied(
                appliedMigrations, ANALYTICS_TRIGGER_FORWARD_MIGRATION_VERSION);
        boolean anyAnalyticsMainMigrationApplied =
                analyticsOutboxMigrationApplied || analyticsTriggerMigrationApplied;
        boolean anyAnalyticsForwardMigrationApplied =
                analyticsOutboxForwardMigrationApplied || analyticsTriggerForwardMigrationApplied;
        if (postgresql && analyticsTriggerMigrationApplied && !analyticsOutboxMigrationApplied) {
            throw new IllegalStateException(
                    "检测到运营 outbox 触发器 migration 缺少基础 history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (postgresql
                && analyticsOutboxMigrationApplied
                && !analyticsTriggerMigrationApplied
                && hasAppliedMigrationAfter(appliedMigrations, ANALYTICS_TRIGGER_MIGRATION_VERSION)) {
            throw new IllegalStateException(
                    "检测到不完整的运营 outbox 主 migration history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (postgresql
                && analyticsTriggerForwardMigrationApplied
                && !analyticsOutboxForwardMigrationApplied) {
            throw new IllegalStateException(
                    "检测到运营 outbox 触发器前向 migration 缺少基础 history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (postgresql
                && analyticsOutboxForwardMigrationApplied
                && !analyticsTriggerForwardMigrationApplied
                && hasAppliedMigrationAfter(
                        appliedMigrations, ANALYTICS_TRIGGER_FORWARD_MIGRATION_VERSION)) {
            throw new IllegalStateException(
                    "检测到不完整的运营 outbox 前向 migration history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (postgresql && anyAnalyticsMainMigrationApplied && anyAnalyticsForwardMigrationApplied) {
            throw new IllegalStateException(
                    "检测到运营 outbox 主 migration 与前向 migration 同时执行，拒绝自动兼容；请核对 flyway_schema_history");
        }
        boolean needsAnalyticsForwardCompatibility = postgresql
                && !anyAnalyticsMainMigrationApplied
                && !anyAnalyticsForwardMigrationApplied
                && hasAppliedMigrationAfter(appliedMigrations, ANALYTICS_TRIGGER_MIGRATION_VERSION);
        boolean qaMemoryAfterSessionShareForwardApplied = isMigrationApplied(
                appliedMigrations, QA_MEMORY_AFTER_SESSION_SHARE_FORWARD_VERSION);
        boolean qaMemoryAfterTokenLatencyForwardApplied = isMigrationApplied(
                appliedMigrations, QA_MEMORY_AFTER_TOKEN_LATENCY_FORWARD_VERSION);
        boolean qaMemoryExtendedHistory = qaMemoryGeneralizeMigrationApplied;
        if (sessionShareAttributionMigrationApplied && !sessionShareMigrationApplied) {
            throw new IllegalStateException(
                    "检测到不完整的会话分享主 migration history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (sessionShareAttributionForwardMigrationApplied && !sessionShareForwardMigrationApplied) {
            throw new IllegalStateException(
                    "检测到不完整的会话分享顺序补偿 migration history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        boolean anySessionShareMainMigrationApplied =
                sessionShareMigrationApplied || sessionShareAttributionMigrationApplied;
        boolean anySessionShareForwardMigrationApplied =
                sessionShareForwardMigrationApplied || sessionShareAttributionForwardMigrationApplied;
        if (anySessionShareForwardMigrationApplied && !qaMemoryExtendedHistory) {
            throw new IllegalStateException(
                    "检测到会话分享顺序补偿 migration 缺少对应 QA Memory 扩展 history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (anySessionShareMainMigrationApplied && anySessionShareForwardMigrationApplied) {
            throw new IllegalStateException(
                    "检测到会话分享主 migration 与顺序补偿 migration 同时执行，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (qaMemoryExtendedHistory
                && sessionShareMigrationApplied
                && !sessionShareAttributionMigrationApplied) {
            throw new IllegalStateException(
                    "检测到 QA Memory 扩展 history 与不完整的会话分享主 migration 混用，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (qaMemoryAfterSessionShareForwardApplied && qaMemoryAfterTokenLatencyForwardApplied) {
            throw new IllegalStateException(
                    "检测到 QA Memory release 顺序补偿 migration 新旧路径同时执行，拒绝自动兼容；请核对 flyway_schema_history");
        }
        boolean anyQaMemoryAfterReleaseForwardApplied =
                qaMemoryAfterSessionShareForwardApplied || qaMemoryAfterTokenLatencyForwardApplied;
        if (anyQaMemoryAfterReleaseForwardApplied && qaMemoryMigrationApplied) {
            throw new IllegalStateException(
                    "检测到 QA Memory 低版本主 migration 与 release 顺序补偿 migration 同时执行，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (anyQaMemoryAfterReleaseForwardApplied && !sessionShareMigrationApplied) {
            throw new IllegalStateException(
                    "检测到 QA Memory release 顺序补偿 migration 缺少会话分享主 history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        if (qaMemoryAfterTokenLatencyForwardApplied && !internalModelTokenLatencyInputsApplied) {
            throw new IllegalStateException(
                    "检测到 QA Memory 最新 release 顺序补偿 migration 缺少内部模型时延输入 history，拒绝自动兼容；请核对 flyway_schema_history");
        }
        boolean needsSessionShareForwardCompatibility =
                qaMemoryExtendedHistory && !anySessionShareMainMigrationApplied;
        boolean needsQaMemoryAfterSessionShareForwardCompatibility =
                !qaMemoryMigrationApplied && sessionShareMigrationApplied;
        String qaMemoryAfterReleaseForwardLocation = null;
        String qaMemoryAfterReleaseForwardVersion = null;
        if (qaMemoryAfterSessionShareForwardApplied) {
            qaMemoryAfterReleaseForwardLocation =
                    QA_MEMORY_AFTER_SESSION_SHARE_COMPATIBILITY_LOCATION;
            qaMemoryAfterReleaseForwardVersion = QA_MEMORY_AFTER_SESSION_SHARE_FORWARD_VERSION;
        } else if (qaMemoryAfterTokenLatencyForwardApplied) {
            qaMemoryAfterReleaseForwardLocation =
                    QA_MEMORY_AFTER_TOKEN_LATENCY_COMPATIBILITY_LOCATION;
            qaMemoryAfterReleaseForwardVersion = QA_MEMORY_AFTER_TOKEN_LATENCY_FORWARD_VERSION;
        } else if (needsQaMemoryAfterSessionShareForwardCompatibility) {
            boolean forwardAfterTokenLatency = internalModelTokenLatencyInputsApplied;
            qaMemoryAfterReleaseForwardLocation = forwardAfterTokenLatency
                    ? QA_MEMORY_AFTER_TOKEN_LATENCY_COMPATIBILITY_LOCATION
                    : QA_MEMORY_AFTER_SESSION_SHARE_COMPATIBILITY_LOCATION;
            qaMemoryAfterReleaseForwardVersion = forwardAfterTokenLatency
                    ? QA_MEMORY_AFTER_TOKEN_LATENCY_FORWARD_VERSION
                    : QA_MEMORY_AFTER_SESSION_SHARE_FORWARD_VERSION;
        }

        List<String> locations = new ArrayList<>(Arrays.stream(configuration.getLocations())
                .map(location -> location.getDescriptor())
                .toList());
        if (postgresql) {
            addLocationIfAbsent(locations, POSTGRESQL_MIGRATION_LOCATION);
        }
        if (legacyMigrationApplied) {
            addLocationIfAbsent(locations, LEGACY_TOOLBOX_MIGRATION_LOCATION);
        }
        boolean idempotentCurrentMigrationApplied = Objects.equals(
                currentMigrationChecksum, CURRENT_TOOLBOX_IDEMPOTENT_CHECKSUM);
        if (idempotentCurrentMigrationApplied) {
            addLocationIfAbsent(locations, CURRENT_TOOLBOX_IDEMPOTENT_LOCATION);
        }
        if (missingLobehubMigrationHistory) {
            addLocationIfAbsent(
                    locations,
                    needsReleaseForwardCompatibility
                            ? LOBEHUB_RELEASE_FORWARD_COMPATIBILITY_LOCATION
                            : LOBEHUB_FORWARD_COMPATIBILITY_LOCATION);
        }
        if (allLegacyInternalModelMigrationsApplied) {
            addLocationIfAbsent(locations, INTERNAL_MODEL_LEGACY_COMPATIBILITY_LOCATION);
        }
        if (runResendForwardLocation != null) {
            addLocationIfAbsent(locations, runResendForwardLocation);
        }
        if (qaMemoryMigrationApplied) {
            addLocationIfAbsent(locations, QA_MEMORY_APPLIED_COMPATIBILITY_LOCATION);
        }
        if (needsSessionShareForwardCompatibility) {
            addLocationIfAbsent(locations, QA_MEMORY_EXTENDED_COMPATIBILITY_LOCATION);
        }
        if (needsExternalApiForwardCompatibility) {
            addLocationIfAbsent(locations, EXTERNAL_API_FORWARD_COMPATIBILITY_LOCATION);
        }
        if (qaMemoryAfterReleaseForwardLocation != null) {
            addLocationIfAbsent(locations, qaMemoryAfterReleaseForwardLocation);
        }
        if (experienceWorkspaceMigrationApplied) {
            addLocationIfAbsent(
                    locations, EXPERIENCE_WORKSPACE_APPLIED_COMPATIBILITY_LOCATION);
        }
        if (needsLocalClientRuntimeEnterpriseForwardCompatibility
                || localClientRuntimeEnterpriseForwardMigrationApplied) {
            addLocationIfAbsent(
                    locations, LOCAL_CLIENT_RUNTIME_ENTERPRISE_FORWARD_COMPATIBILITY_LOCATION);
        } else if (needsLocalClientRuntimeForwardCompatibility) {
            addLocationIfAbsent(
                    locations, LOCAL_CLIENT_RUNTIME_FORWARD_COMPATIBILITY_LOCATION);
        }
        if (localClientRuntimeForwardMigrationApplied) {
            // 已执行旧前向版本的历史必须继续从冻结 location 解析，不能同时暴露新的企业前向版本。
            addLocationIfAbsent(
                    locations, LOCAL_CLIENT_RUNTIME_APPLIED_COMPATIBILITY_LOCATION);
        }
        if (needsLocalClientVersionManagementForwardCompatibility
                || localClientVersionManagementForwardMigrationApplied) {
            addLocationIfAbsent(
                    locations, LOCAL_CLIENT_VERSION_MANAGEMENT_FORWARD_COMPATIBILITY_LOCATION);
        }
        if (needsAnalyticsForwardCompatibility || anyAnalyticsForwardMigrationApplied) {
            addLocationIfAbsent(locations, ANALYTICS_AFTER_RELEASE_COMPATIBILITY_LOCATION);
        }
        configuration.locations(locations.toArray(String[]::new));

        boolean legacyWithoutCurrentMigration = legacyMigrationApplied
                && currentMigrationChecksum == null;
        List<String> filteredMainResources = new ArrayList<>();
        if (legacyWithoutCurrentMigration || idempotentCurrentMigrationApplied) {
            filteredMainResources.add(CURRENT_TOOLBOX_MAIN_RESOURCE);
        }
        if (missingLobehubMigrationHistory) {
            filteredMainResources.add(LOBEHUB_MODEL_GATEWAY_MAIN_RESOURCE);
        }
        if (allLegacyInternalModelMigrationsApplied) {
            filteredMainResources.add(INTERNAL_MODEL_OBSERVABILITY_MAIN_RESOURCE);
            filteredMainResources.add(INTERNAL_MODEL_FIRST_TOKEN_MAIN_RESOURCE);
            filteredMainResources.add(INTERNAL_MODEL_STREAM_COMPLETE_MAIN_RESOURCE);
        }
        if (needsRunResendForwardCompatibility) {
            filteredMainResources.add(RUN_RESEND_MAIN_RESOURCE);
        }
        if (needsExternalApiForwardCompatibility) {
            filteredMainResources.add(EXTERNAL_API_CREDENTIALS_MAIN_RESOURCE);
        }
        if (qaMemoryMigrationApplied) {
            filteredMainResources.add(QA_MEMORY_APPLIED_MAIN_RESOURCE);
        }
        if (needsSessionShareForwardCompatibility) {
            filteredMainResources.add(SESSION_SHARE_MAIN_RESOURCE);
            filteredMainResources.add(SESSION_SHARE_ATTRIBUTION_MAIN_RESOURCE);
            filteredMainResources.add(QA_MEMORY_GENERALIZE_MAIN_RESOURCE);
            filteredMainResources.add(QA_MEMORY_IDENTITY_MAIN_RESOURCE);
        }
        if (needsQaMemoryAfterSessionShareForwardCompatibility) {
            filteredMainResources.add(QA_MEMORY_APPLIED_MAIN_RESOURCE);
            filteredMainResources.add(QA_MEMORY_GENERALIZE_MAIN_RESOURCE);
            filteredMainResources.add(QA_MEMORY_IDENTITY_MAIN_RESOURCE);
        }
        // 旧体验工作区候选版本已进入个人持久库，只能从隔离路径按原字节解析；主链统一执行更高前向版本。
        filteredMainResources.add(EXPERIENCE_WORKSPACE_APPLIED_MAIN_RESOURCE);
        if (needsLocalClientRuntimeForwardCompatibility
                || localClientRuntimeForwardMigrationApplied
                || localClientRuntimeEnterpriseForwardMigrationApplied) {
            // release 前向版本已经执行时，dev 的较低主链版本仍然不能重新暴露给 Flyway 校验。
            filteredMainResources.add(LOCAL_CLIENT_RUNTIME_MAIN_RESOURCE);
        }
        if (needsLocalClientVersionManagementForwardCompatibility
                || localClientVersionManagementForwardMigrationApplied) {
            // 自动化配置已越过 dev 客户端版本管理时，只允许执行更高版本的完整前向补偿。
            filteredMainResources.add(LOCAL_CLIENT_VERSION_MANAGEMENT_MAIN_RESOURCE);
            filteredMainResources.add(LOCAL_CLIENT_CREDENTIAL_REVEAL_MAIN_RESOURCE);
        }
        if (needsAnalyticsForwardCompatibility || anyAnalyticsForwardMigrationApplied) {
            filteredMainResources.add(ANALYTICS_OUTBOX_MAIN_RESOURCE);
            filteredMainResources.add(ANALYTICS_TRIGGER_MAIN_RESOURCE);
        }
        if (!filteredMainResources.isEmpty()) {
            // Flyway 没有公开“排除单个 classpath migration”的配置入口；这里包装唯一默认扫描器，
            // 只隐藏与已知历史分叉不匹配的主目录副本，其余 SQL 和 Java migration 保持原样。
            ResourceProvider defaultProvider = new Scanner<>(
                    JavaMigration.class, configuration, configuration.getLocations());
            configuration.resourceProvider(
                    new MigrationFilteringResourceProvider(defaultProvider, filteredMainResources));
        }

        if (legacyMigrationApplied) {
            LOGGER.info("检测到已执行的旧工具盒子迁移，启用兼容解析路径: version={}",
                    LEGACY_TOOLBOX_MIGRATION_VERSION);
        }
        if (idempotentCurrentMigrationApplied) {
            LOGGER.warn("检测到已执行的工具盒子幂等迁移变体，按原 checksum 启用隔离兼容路径: version={}, checksum={}",
                    CURRENT_TOOLBOX_MIGRATION_VERSION, currentMigrationChecksum);
        }
        if (missingLobehubMigrationHistory) {
            String forwardVersion = needsReleaseForwardCompatibility
                    ? LOBEHUB_RELEASE_FORWARD_COMPATIBILITY_VERSION
                    : LOBEHUB_FORWARD_COMPATIBILITY_VERSION;
            LOGGER.warn("检测到 LobeHub 模型网关迁移缺失且后续迁移已执行，启用顺序补偿路径: missingVersion={}, markerVersion={}, forwardVersion={}",
                    LOBEHUB_MODEL_GATEWAY_MIGRATION_VERSION,
                    LOBEHUB_SPLIT_MARKER_VERSION,
                    forwardVersion);
        }
        if (allLegacyInternalModelMigrationsApplied) {
            LOGGER.warn("检测到已执行的内部模型可观测旧 migration，启用原始字节兼容解析并过滤重编号版本: versions={},{},{}",
                    INTERNAL_MODEL_OBSERVABILITY_LEGACY_VERSION,
                    INTERNAL_MODEL_FIRST_TOKEN_LEGACY_VERSION,
                    INTERNAL_MODEL_STREAM_COMPLETE_LEGACY_VERSION);
        }
        if (needsRunResendForwardCompatibility) {
            LOGGER.warn("检测到撤销重发 migration 未执行但后续版本已执行，启用顺序补偿路径: missingVersion={}, forwardVersion={}",
                    RUN_RESEND_MIGRATION_VERSION,
                    runResendForwardVersion);
        }
        if (qaMemoryMigrationApplied) {
            LOGGER.warn("检测到已执行的 QA Memory migration，启用原始字节兼容解析: version={}, checksum={}",
                    QA_MEMORY_APPLIED_MIGRATION_VERSION,
                    qaMemoryMigrationChecksum);
        }
        if (needsExternalApiForwardCompatibility) {
            LOGGER.warn("检测到外部 API 凭据 migration 早于已执行的 QA Memory history，启用顺序补偿路径: missingVersion={}, forwardVersion={}",
                    EXTERNAL_API_CREDENTIALS_MIGRATION_VERSION,
                    EXTERNAL_API_FORWARD_MIGRATION_VERSION);
        }
        if (needsSessionShareForwardCompatibility) {
            LOGGER.warn("检测到已执行的 QA Memory 扩展 history，启用原始字节兼容与会话分享顺序补偿路径: versions={},{}, shareForwardVersions={},{}",
                    QA_MEMORY_GENERALIZE_MIGRATION_VERSION,
                    QA_MEMORY_IDENTITY_MIGRATION_VERSION,
                    SESSION_SHARE_FORWARD_MIGRATION_VERSION,
                    SESSION_SHARE_ATTRIBUTION_FORWARD_MIGRATION_VERSION);
        }
        if (needsQaMemoryAfterSessionShareForwardCompatibility) {
            LOGGER.warn("检测到会话分享主 history 早于 QA Memory，启用 QA Memory 顺序补偿路径: missingVersion={}, forwardVersion={}",
                    QA_MEMORY_APPLIED_MIGRATION_VERSION,
                    qaMemoryAfterReleaseForwardVersion);
        }
        if (experienceWorkspaceMigrationApplied) {
            LOGGER.warn("检测到已执行的体验工作区 migration，启用原始字节兼容解析并继续执行高版本前向迁移: version={}, checksum={}, forwardVersion={}",
                    EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_VERSION,
                    experienceWorkspaceMigrationChecksum,
                    EXPERIENCE_WORKSPACE_FORWARD_MIGRATION_VERSION);
        }
        if (needsLocalClientRuntimeForwardCompatibility) {
            LOGGER.warn("检测到本地客户端 migration 早于已执行的 release history，启用顺序补偿路径: missingVersion={}, forwardVersion={}",
                    LOCAL_CLIENT_RUNTIME_MIGRATION_VERSION,
                    needsLocalClientRuntimeEnterpriseForwardCompatibility
                            ? LOCAL_CLIENT_RUNTIME_ENTERPRISE_FORWARD_MIGRATION_VERSION
                            : LOCAL_CLIENT_RUNTIME_FORWARD_MIGRATION_VERSION);
        }
        if (localClientRuntimeForwardMigrationApplied) {
            LOGGER.warn("检测到已执行的本地客户端顺序补偿 migration，继续启用原始字节兼容解析: version={}",
                    LOCAL_CLIENT_RUNTIME_FORWARD_MIGRATION_VERSION);
        }
        if (localClientRuntimeEnterpriseForwardMigrationApplied) {
            LOGGER.warn("检测到已执行的本地客户端企业基线顺序补偿 migration，继续启用原始字节兼容解析: version={}",
                    LOCAL_CLIENT_RUNTIME_ENTERPRISE_FORWARD_MIGRATION_VERSION);
        }
        if (needsLocalClientVersionManagementForwardCompatibility) {
            LOGGER.warn("检测到自动化配置 history 已越过本地客户端版本管理主链，启用高版本顺序补偿: missingVersions={},{}; forwardVersion={}",
                    LOCAL_CLIENT_VERSION_MANAGEMENT_MIGRATION_VERSION,
                    LOCAL_CLIENT_CREDENTIAL_REVEAL_MIGRATION_VERSION,
                    LOCAL_CLIENT_VERSION_MANAGEMENT_FORWARD_MIGRATION_VERSION);
        } else if (localClientVersionManagementForwardMigrationApplied) {
            LOGGER.warn("检测到已执行的本地客户端版本管理顺序补偿 migration，继续启用原始字节兼容解析: version={}",
                    LOCAL_CLIENT_VERSION_MANAGEMENT_FORWARD_MIGRATION_VERSION);
        }
        if (needsAnalyticsForwardCompatibility) {
            LOGGER.warn("检测到运营 outbox migration 早于已执行的 release history，启用顺序补偿路径: missingVersions={},{}; forwardVersions={},{}",
                    ANALYTICS_OUTBOX_MIGRATION_VERSION,
                    ANALYTICS_TRIGGER_MIGRATION_VERSION,
                    ANALYTICS_OUTBOX_FORWARD_MIGRATION_VERSION,
                    ANALYTICS_TRIGGER_FORWARD_MIGRATION_VERSION);
        } else if (anyAnalyticsForwardMigrationApplied) {
            LOGGER.warn("检测到已执行的运营 outbox 顺序补偿 migration，继续启用原始字节兼容路径: versions={},{}",
                    ANALYTICS_OUTBOX_FORWARD_MIGRATION_VERSION,
                    ANALYTICS_TRIGGER_FORWARD_MIGRATION_VERSION);
        }
    }

    /**
     * 复用 Boot 已组装的数据源、schema、history 表和主 migration 配置读取历史。
     */
    private MigrationInfo[] appliedMigrations(FluentConfiguration configuration) {
        return Flyway.configure(configuration.getClassLoader())
                .configuration(configuration)
                .load()
                .info()
                .applied();
    }

    private boolean isMigrationApplied(MigrationInfo[] appliedMigrations, String version) {
        return Arrays.stream(appliedMigrations)
                .map(MigrationInfo::getVersion)
                .filter(appliedVersion -> appliedVersion != null)
                .anyMatch(appliedVersion -> version.equals(appliedVersion.getVersion()));
    }

    private Integer appliedChecksum(MigrationInfo[] appliedMigrations, String version) {
        return Arrays.stream(appliedMigrations)
                .filter(migration -> migration.getVersion() != null)
                .filter(migration -> version.equals(migration.getVersion().getVersion()))
                .map(MigrationInfo::getAppliedChecksum)
                .findFirst()
                .orElse(null);
    }

    /** 判断目标版本之后是否已有成功 history，用于选择高版本前向补偿而非开启乱序执行。 */
    private boolean hasAppliedMigrationAfter(MigrationInfo[] appliedMigrations, String version) {
        MigrationVersion targetVersion = MigrationVersion.fromVersion(version);
        return Arrays.stream(appliedMigrations)
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .anyMatch(appliedVersion -> appliedVersion.compareTo(targetVersion) > 0);
    }

    private void addLocationIfAbsent(List<String> locations, String location) {
        if (!locations.contains(location)) {
            locations.add(location);
        }
    }

    /** PostgreSQL 专属迁移承载触发器等 H2 无法解析的生产语法。 */
    private boolean isPostgresql(FluentConfiguration configuration) {
        try (Connection connection = configuration.getDataSource().getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("读取 Flyway 数据库类型失败", exception);
        }
    }

    /** 过滤主目录中与已知数据库历史不匹配的迁移，保留按 history 选择的隔离补偿资源。 */
    private static final class MigrationFilteringResourceProvider
            implements ResourceProvider {

        private final ResourceProvider delegate;
        private final List<String> filteredMainResources;

        private MigrationFilteringResourceProvider(
                ResourceProvider delegate,
                List<String> filteredMainResources) {
            this.delegate = delegate;
            this.filteredMainResources = List.copyOf(filteredMainResources);
        }

        @Override
        public LoadableResource getResource(String name) {
            LoadableResource resource = delegate.getResource(name);
            return resource != null && isFilteredMainMigration(resource) ? null : resource;
        }

        @Override
        public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
            return delegate.getResources(prefix, suffixes).stream()
                    .filter(resource -> !isFilteredMainMigration(resource))
                    .toList();
        }

        private boolean isFilteredMainMigration(LoadableResource resource) {
            String relativePath = normalize(resource.getRelativePath());
            String absolutePath = normalize(resource.getAbsolutePath());
            String absolutePathOnDisk = normalize(resource.getAbsolutePathOnDisk());
            return filteredMainResources.stream().anyMatch(filteredResource ->
                    filteredResource.equals(relativePath)
                            || absolutePath.endsWith("/" + filteredResource)
                            || absolutePath.contains("!/" + filteredResource)
                            || absolutePathOnDisk.endsWith("/" + filteredResource)
                            || absolutePathOnDisk.contains("!/" + filteredResource));
        }

        private static String normalize(String path) {
            return path == null ? "" : path.replace('\\', '/');
        }
    }

}
