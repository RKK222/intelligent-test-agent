package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FlywayMigrationNamingTest {

    private static final Pattern MIGRATION_FILE = Pattern.compile("^V([^_]+)__(.+)\\.sql$");
    private static final Pattern TIMESTAMP_VERSION = Pattern.compile("^\\d{14}$");
    private static final String APPLIED_TOOLBOX_COMPATIBILITY_MIGRATION =
            "V20260727203500__create_toolbox_click_tracking.sql";
    private static final String APPLIED_TOOLBOX_COMPATIBILITY_SHA256 =
            "1bb00e2aec40e1eaf286e5351e474413fc5dba860b2bbe3a9b5b48c8ef615ec6";
    private static final String APPLIED_TOOLBOX_ENTERPRISE_MIGRATION =
            "V20260728160800__create_toolbox_click_tracking.sql";
    private static final String APPLIED_TOOLBOX_ENTERPRISE_SHA256 =
            "777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2";
    private static final String APPLIED_TOOLBOX_IDEMPOTENT_SHA256 =
            "e8b21da5fb7a8c286b86ded9c0d12691c7e8b6e5d170dc16c5ff76a044bdcdc8";
    private static final String APPLIED_UI_TEST_PLATFORM_SEED_MIGRATION =
            "V20260801093854__seed_ui_test_platform_base_url.sql";
    private static final String APPLIED_UI_TEST_PLATFORM_SEED_SHA256 =
            "aa08c1cedc64bd0b8dd230227f9dcb7a0ef6a33572473d8a14795f5f6b93e6e5";
    private static final String APPLIED_UI_TEST_PLATFORM_RENAME_MIGRATION =
            "V20260801104000__rename_ui_test_platform_parameter.sql";
    private static final String APPLIED_UI_TEST_PLATFORM_RENAME_SHA256 =
            "0e306671eda36a9bb8881cf3d85b4e87b5373e00770dcd6503693b11d008e45c";
    private static final String APPLIED_LOBEHUB_MODEL_GATEWAY_MIGRATION =
            "V20260730090000__add_lobehub_model_gateway.sql";
    private static final String APPLIED_LOBEHUB_MODEL_GATEWAY_SHA256 =
            "0f16f1b2f3108e60580cfeb00102e10ac21e20220be255fae77bad9871f0bcb7";
    private static final String APPLIED_LOBEHUB_FORWARD_COMPATIBILITY_MIGRATION =
            "V20260802173416__backfill_lobehub_model_gateway.sql";
    private static final String APPLIED_LOBEHUB_FORWARD_COMPATIBILITY_SHA256 =
            "4f773e35e55380592f094c03cc5a66fb69b7dee4a2799e1c9ab5d0b9d8f1634a";
    private static final String RELEASE_LOBEHUB_FORWARD_COMPATIBILITY_MIGRATION =
            "V20260803141754__backfill_lobehub_model_gateway_after_rollout.sql";
    private static final String RELEASE_LOBEHUB_FORWARD_COMPATIBILITY_SHA256 =
            "b73b06fb14f407979646df32a8342603ab957c2f4812a4013ab9635cdfdcce64";
    private static final String RELEASE_ROLLOUT_SUPERSEDE_MIGRATION =
            "V20260803133000__support_public_agent_config_rollout_supersede.sql";
    private static final String RELEASE_ROLLOUT_SUPERSEDE_SHA256 =
            "8b3cbad538f856d5daa06d15f118554ecefb2380a249287cdfe291eb71199022";
    private static final String RELEASE_PERSONAL_WORKSPACE_RELOCATION_MIGRATION =
            "V20260804123000__create_personal_workspace_relocations.sql";
    private static final String RELEASE_PERSONAL_WORKSPACE_RELOCATION_SHA256 =
            "f41a9aaab637f4b196f63cb7d37ef58cf0b15c9521abd1050c9929c6ce27b212";
    private static final String EXTERNAL_API_CREDENTIALS_MIGRATION =
            "V20260809110000__create_external_api_credentials.sql";
    private static final String EXTERNAL_API_CREDENTIALS_FORWARD_MIGRATION =
            "V20260810110000__create_external_api_credentials_after_qa_memory.sql";
    private static final String EXTERNAL_API_CREDENTIALS_SHA256 =
            "356f2cf9127fb514c614ccb8fc77473e6269f6e1e5cd373b0750d2f207009d53";
    private static final String APPLIED_QA_MEMORY_MIGRATION =
            "V20260809120000__create_qa_memory_governance.sql";
    private static final String APPLIED_QA_MEMORY_SHA256 =
            "b2ae5639284208be8bc09952d9143c3dd0d8a2bf649b6601aed4225e586af18a";
    private static final String APPLIED_QA_MEMORY_GENERALIZE_MIGRATION =
            "V20260809230000__generalize_memory_and_embedding_profiles.sql";
    private static final String APPLIED_QA_MEMORY_GENERALIZE_SHA256 =
            "2740ff6d4a97c5b8a4c438586f55d58078c3cfce93b06e4efeb6b77b039c66c3";
    private static final String APPLIED_QA_MEMORY_IDENTITY_MIGRATION =
            "V20260810090000__enforce_qa_memory_identity.sql";
    private static final String APPLIED_QA_MEMORY_IDENTITY_SHA256 =
            "619f886b093c80c1e1f71569c5c44309fa4f8184dd2791c0cf1955beb77c9af3";
    private static final String QA_MEMORY_AFTER_SESSION_SHARE_MIGRATION =
            "V20260810173117__qa_memories_create_governance_after_session_share.sql";
    private static final String QA_MEMORY_AFTER_TOKEN_LATENCY_MIGRATION =
            "V20260811170050__qa_memories_create_governance_after_token_latency_inputs.sql";
    private static final String QA_MEMORY_RELEASE_FORWARD_SHA256 =
            "44ea89c0ea5b9edb7fc5cbfb682e540b251f0c106b1d3c2762576d04ade6f984";
    private static final String SESSION_SHARE_MAIN_MIGRATION =
            "V20260809170000__session_shares_create_collaboration_share.sql";
    private static final String SESSION_SHARE_FORWARD_MIGRATION =
            "V20260810110001__session_shares_create_collaboration_share_after_qa_memory.sql";
    private static final String SESSION_SHARE_SHA256 =
            "b0b04355fcfe64f3d22d8a8ff297fa62a30db9d97bf6bf82968588f5da72d0c9";
    private static final String SESSION_SHARE_ATTRIBUTION_MAIN_MIGRATION =
            "V20260809170001__session_messages_add_delegated_attribution.sql";
    private static final String SESSION_SHARE_ATTRIBUTION_FORWARD_MIGRATION =
            "V20260810110002__session_messages_add_delegated_attribution_after_qa_memory.sql";
    private static final String SESSION_SHARE_ATTRIBUTION_SHA256 =
            "dfb5d65b474416c28ec6131e95c7b9e7f744f9d2903c0bc4fcd0065632a4eee5";
    private static final String USER_NOTIFICATION_MIGRATION =
            "V20260810170000__user_notifications_create_notification_center.sql";
    private static final String USER_NOTIFICATION_SHA256 =
            "4592eb72a69179ca91febe43278ce8ed70fe02979f7b5c0f7366004048510ca9";
    private static final String USER_NOTIFICATION_DISPOSE_MIGRATION =
            "V20260811213000__user_notifications_expand_dispose_types.sql";
    private static final String USER_NOTIFICATION_DISPOSE_SHA256 =
            "00bd72f2efe1916d8a33fc5310d59936c6950d3fd81e8fce91eda529ffb5096c";
    private static final String INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_MIGRATION =
            "V20260810234154__internal_model_call_records_add_token_latency_inputs.sql";
    private static final String INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_SHA256 =
            "f684bd5d323d3816fc7ae982eff7b45256763f467f540020af41753c04fb837b";
    private static final String APPLIED_EXPERIENCE_WORKSPACE_MIGRATION =
            "V20260809210000__common_parameters_add_experience_workspace.sql";
    private static final String APPLIED_EXPERIENCE_WORKSPACE_SHA256 =
            "c093695aac4305aed3caeb8fcec58f0731f1519527031f1775adaf8be86cf24a";
    private static final String EXPERIENCE_WORKSPACE_FORWARD_MIGRATION =
            "V20260812104911__common_parameters_add_experience_workspace_after_release.sql";
    private static final String EXPERIENCE_WORKSPACE_FORWARD_SHA256 =
            "a613f77fd42aea5f404dfb51bad5fe93c1f478d73bf131de8c9dc9931a27e5ea";
    private static final String EXPERIENCE_WORKSPACE_DEFAULT_MIGRATION =
            "V20260812144051__common_parameters_default_experience_workspace.sql";
    private static final String EXPERIENCE_WORKSPACE_DEFAULT_SHA256 =
            "e07d560ac0652860ed8e8788b002df0881eface861998a20e4e83da85276bfcf";
    private static final String LOCAL_CLIENT_RUNTIME_FORWARD_MIGRATION =
            "V20260812202425__local_client_credentials_create_runtime_after_release.sql";
    private static final String LOCAL_CLIENT_RUNTIME_FORWARD_SHA256 =
            "168cbf7bf3c1a062c8fd38057cd32726804ab8bf00ced1dff39d5c2837c53026";
    private static final List<String> APPLIED_LEGACY_SEED_MIGRATIONS = List.of(
            "V10__seed_fcoss_application.sql",
            "V13__seed_fcoss_more_workspaces.sql");

    @Test
    void migrationVersionsAreUniqueAndTimestampedAfterLegacySequence() throws IOException {
        List<String> fileNames = migrationFileNames();

        Map<String, List<String>> byVersion = fileNames.stream()
                .collect(Collectors.groupingBy(FlywayMigrationNamingTest::versionOf));
        Map<String, List<String>> duplicates = byVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(duplicates)
                .as("Flyway migration version must be unique; use timestamp versions for parallel work")
                .isEmpty();

        List<String> invalidNewVersions = fileNames.stream()
                .filter(name -> !isLegacyVersion(versionOf(name)))
                .filter(name -> !TIMESTAMP_VERSION.matcher(versionOf(name)).matches())
                .toList();
        assertThat(invalidNewVersions)
                .as("Migrations after legacy V18 must use VyyyyMMddHHmmss__description.sql")
                .isEmpty();
    }

    @Test
    void appliedLegacySeedMigrationsRemainResolved() throws IOException {
        List<String> fileNames = migrationFileNames();

        assertThat(fileNames)
                .as("Applied legacy seed migrations must remain in source to keep Flyway validate compatible")
                .containsAll(APPLIED_LEGACY_SEED_MIGRATIONS);
        assertThat(fileNames)
                .as("V10 is already occupied by F-COSS seed; SSH key schema changes must use a timestamp version")
                .doesNotContain("V10__add_encrypted_aes_key_to_user_ssh_keys.sql");
    }

    @Test
    void appliedToolboxCompatibilityMigrationRemainsByteExact()
            throws IOException, NoSuchAlgorithmException {
        assertMigrationSha256(
                "db/migration-compat/toolbox",
                APPLIED_TOOLBOX_COMPATIBILITY_MIGRATION,
                APPLIED_TOOLBOX_COMPATIBILITY_SHA256);
    }

    @Test
    void appliedToolboxCurrentMigrationVariantsRemainByteExact()
            throws IOException, NoSuchAlgorithmException {
        assertMigrationSha256(
                "db/migration",
                APPLIED_TOOLBOX_ENTERPRISE_MIGRATION,
                APPLIED_TOOLBOX_ENTERPRISE_SHA256);
        assertMigrationSha256(
                "db/migration-compat/toolbox-current-idempotent",
                APPLIED_TOOLBOX_ENTERPRISE_MIGRATION,
                APPLIED_TOOLBOX_IDEMPOTENT_SHA256);
    }

    @Test
    void appliedUiTestPlatformMigrationsRemainByteExact()
            throws IOException, NoSuchAlgorithmException {
        assertMigrationSha256(
                "db/migration",
                APPLIED_UI_TEST_PLATFORM_SEED_MIGRATION,
                APPLIED_UI_TEST_PLATFORM_SEED_SHA256);
        assertMigrationSha256(
                "db/migration",
                APPLIED_UI_TEST_PLATFORM_RENAME_MIGRATION,
                APPLIED_UI_TEST_PLATFORM_RENAME_SHA256);
    }

    @Test
    void appliedLobehubMigrationPathsRemainByteExact()
            throws IOException, NoSuchAlgorithmException {
        assertMigrationSha256(
                "db/migration",
                APPLIED_LOBEHUB_MODEL_GATEWAY_MIGRATION,
                APPLIED_LOBEHUB_MODEL_GATEWAY_SHA256);
        assertMigrationSha256(
                "db/migration-compat/lobehub-missing",
                APPLIED_LOBEHUB_FORWARD_COMPATIBILITY_MIGRATION,
                APPLIED_LOBEHUB_FORWARD_COMPATIBILITY_SHA256);
        assertMigrationSha256(
                "db/migration-compat/lobehub-missing-after-rollout",
                RELEASE_LOBEHUB_FORWARD_COMPATIBILITY_MIGRATION,
                RELEASE_LOBEHUB_FORWARD_COMPATIBILITY_SHA256);
    }

    @Test
    void releaseRolloutSupersedeMigrationRemainsByteExact()
            throws IOException, NoSuchAlgorithmException {
        assertMigrationSha256(
                "db/migration",
                RELEASE_ROLLOUT_SUPERSEDE_MIGRATION,
                RELEASE_ROLLOUT_SUPERSEDE_SHA256);
    }

    @Test
    void releasePersonalWorkspaceRelocationMigrationRemainsByteExact()
            throws IOException, NoSuchAlgorithmException {
        assertMigrationSha256(
                "db/migration",
                RELEASE_PERSONAL_WORKSPACE_RELOCATION_MIGRATION,
                RELEASE_PERSONAL_WORKSPACE_RELOCATION_SHA256);
    }

    @Test
    void appliedQaMemoryAndExternalApiCompatibilityPathsRemainByteExact()
            throws IOException, NoSuchAlgorithmException {
        assertMigrationSha256(
                "db/migration",
                APPLIED_QA_MEMORY_MIGRATION,
                APPLIED_QA_MEMORY_SHA256);
        assertMigrationSha256(
                "db/migration-compat/qa-memory-applied",
                APPLIED_QA_MEMORY_MIGRATION,
                APPLIED_QA_MEMORY_SHA256);
        assertMigrationSha256(
                "db/migration",
                EXTERNAL_API_CREDENTIALS_MIGRATION,
                EXTERNAL_API_CREDENTIALS_SHA256);
        assertMigrationSha256(
                "db/migration-compat/external-api-after-qa-memory",
                EXTERNAL_API_CREDENTIALS_FORWARD_MIGRATION,
                EXTERNAL_API_CREDENTIALS_SHA256);
        assertMigrationSha256(
                "db/migration-compat/qa-memory-extended",
                APPLIED_QA_MEMORY_GENERALIZE_MIGRATION,
                APPLIED_QA_MEMORY_GENERALIZE_SHA256);
        assertMigrationSha256(
                "db/migration",
                APPLIED_QA_MEMORY_GENERALIZE_MIGRATION,
                APPLIED_QA_MEMORY_GENERALIZE_SHA256);
        assertMigrationSha256(
                "db/migration-compat/qa-memory-extended",
                APPLIED_QA_MEMORY_IDENTITY_MIGRATION,
                APPLIED_QA_MEMORY_IDENTITY_SHA256);
        assertMigrationSha256(
                "db/migration",
                APPLIED_QA_MEMORY_IDENTITY_MIGRATION,
                APPLIED_QA_MEMORY_IDENTITY_SHA256);
        assertMigrationSha256(
                "db/migration-compat/qa-memory-after-session-share",
                QA_MEMORY_AFTER_SESSION_SHARE_MIGRATION,
                QA_MEMORY_RELEASE_FORWARD_SHA256);
        assertMigrationSha256(
                "db/migration-compat/qa-memory-after-token-latency-inputs",
                QA_MEMORY_AFTER_TOKEN_LATENCY_MIGRATION,
                QA_MEMORY_RELEASE_FORWARD_SHA256);
        assertMigrationSha256(
                "db/migration",
                SESSION_SHARE_MAIN_MIGRATION,
                SESSION_SHARE_SHA256);
        assertMigrationSha256(
                "db/migration-compat/qa-memory-extended",
                SESSION_SHARE_FORWARD_MIGRATION,
                SESSION_SHARE_SHA256);
        assertMigrationSha256(
                "db/migration",
                SESSION_SHARE_ATTRIBUTION_MAIN_MIGRATION,
                SESSION_SHARE_ATTRIBUTION_SHA256);
        assertMigrationSha256(
                "db/migration-compat/qa-memory-extended",
                SESSION_SHARE_ATTRIBUTION_FORWARD_MIGRATION,
                SESSION_SHARE_ATTRIBUTION_SHA256);
    }

    @Test
    void appliedExperienceWorkspaceMigrationRemainsByteExactInCompatibilityLocation()
            throws IOException, NoSuchAlgorithmException {
        assertMigrationSha256(
                "db/migration-compat/experience-workspace-applied",
                APPLIED_EXPERIENCE_WORKSPACE_MIGRATION,
                APPLIED_EXPERIENCE_WORKSPACE_SHA256);
        assertMigrationSha256(
                "db/migration",
                EXPERIENCE_WORKSPACE_FORWARD_MIGRATION,
                EXPERIENCE_WORKSPACE_FORWARD_SHA256);
        assertMigrationSha256(
                "db/migration",
                EXPERIENCE_WORKSPACE_DEFAULT_MIGRATION,
                EXPERIENCE_WORKSPACE_DEFAULT_SHA256);
    }

    @Test
    void appliedLocalClientRuntimeMigrationRemainsByteExactInCompatibilityLocation()
            throws IOException, NoSuchAlgorithmException {
        assertMigrationSha256(
                "db/migration-compat/local-client-runtime-applied",
                LOCAL_CLIENT_RUNTIME_FORWARD_MIGRATION,
                LOCAL_CLIENT_RUNTIME_FORWARD_SHA256);
    }

    @Test
    void appliedNotificationAndTokenLatencyMigrationsRemainByteExact()
            throws IOException, NoSuchAlgorithmException {
        assertMigrationSha256(
                "db/migration",
                USER_NOTIFICATION_MIGRATION,
                USER_NOTIFICATION_SHA256);
        assertMigrationSha256(
                "db/migration",
                USER_NOTIFICATION_DISPOSE_MIGRATION,
                USER_NOTIFICATION_DISPOSE_SHA256);
        assertMigrationSha256(
                "db/migration",
                INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_MIGRATION,
                INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_SHA256);
    }

    private static void assertMigrationSha256(
            String relativeDirectory,
            String migrationFile,
            String expectedSha256) throws IOException, NoSuchAlgorithmException {
        Path migration = locateResourceDir(relativeDirectory).resolve(migrationFile);
        assertThat(migration).exists();
        String sha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(migration)));
        assertThat(sha256)
                .as("Applied migration must keep its original Flyway checksum content: %s", migration)
                .isEqualTo(expectedSha256);
    }

    private static List<String> migrationFileNames() throws IOException {
        List<String> fileNames = new ArrayList<>();
        fileNames.addAll(migrationFileNames(locateResourceDir("db/migration")));
        return fileNames.stream().sorted().toList();
    }

    private static List<String> migrationFileNames(Path migrationDir) throws IOException {
        try (var stream = Files.list(migrationDir)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V") && name.endsWith(".sql"))
                    .toList();
        }
    }

    /**
     * Maven 可能从 backend 根目录或模块目录执行测试；这里按两种入口定位源码 migration。
     */
    private static Path locateResourceDir(String relativePath) {
        Path cwd = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                cwd.resolve("src/main/resources").resolve(relativePath),
                cwd.resolve("test-agent-persistence/src/main/resources").resolve(relativePath),
                cwd.resolve("backend/test-agent-persistence/src/main/resources").resolve(relativePath));
        return candidates.stream()
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot locate Flyway migration resource directory from " + cwd + ": " + relativePath));
    }

    private static String versionOf(String fileName) {
        Matcher matcher = MIGRATION_FILE.matcher(fileName);
        assertThat(matcher.matches())
                .as("Flyway migration file must use V<version>__<description>.sql: %s", fileName)
                .isTrue();
        return matcher.group(1);
    }

    private static boolean isLegacyVersion(String version) {
        if (version.length() > 2 || !version.chars().allMatch(Character::isDigit)) {
            return false;
        }
        // V18 已在历史分支中发布过，不能删除或改名；后续新增仍必须使用时间戳版本。
        return Integer.parseInt(version) <= 18;
    }
}
