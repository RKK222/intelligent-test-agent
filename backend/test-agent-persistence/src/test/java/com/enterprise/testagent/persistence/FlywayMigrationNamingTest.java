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
        Path migration = locateResourceDir("db/migration-compat/toolbox")
                .resolve(APPLIED_TOOLBOX_COMPATIBILITY_MIGRATION);

        assertThat(migration).exists();
        String sha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(migration)));
        assertThat(sha256)
                .as("Applied toolbox compatibility migration must keep its original Flyway checksum content")
                .isEqualTo(APPLIED_TOOLBOX_COMPATIBILITY_SHA256);
    }

    private static List<String> migrationFileNames() throws IOException {
        List<String> fileNames = new ArrayList<>();
        fileNames.addAll(migrationFileNames(locateResourceDir("db/migration")));
        fileNames.addAll(migrationFileNames(locateResourceDir("db/migration-compat/toolbox")));
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
