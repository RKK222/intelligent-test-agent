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
