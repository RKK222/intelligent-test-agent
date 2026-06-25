package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FlywayMigrationNamingTest {

    private static final Pattern MIGRATION_FILE = Pattern.compile("^V([^_]+)__(.+)\\.sql$");
    private static final Pattern TIMESTAMP_VERSION = Pattern.compile("^\\d{14}$");

    @Test
    void migrationVersionsAreUniqueAndTimestampedAfterLegacySequence() throws IOException {
        Path migrationDir = locateMigrationDir();
        List<String> fileNames;
        try (var stream = Files.list(migrationDir)) {
            fileNames = stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V") && name.endsWith(".sql"))
                    .sorted()
                    .toList();
        }

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
                .as("Migrations after legacy V17 must use VyyyyMMddHHmmss__description.sql")
                .isEmpty();
    }

    /**
     * Maven 可能从 backend 根目录或模块目录执行测试；这里按两种入口定位源码 migration。
     */
    private static Path locateMigrationDir() {
        Path cwd = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                cwd.resolve("src/main/resources/db/migration"),
                cwd.resolve("test-agent-persistence/src/main/resources/db/migration"),
                cwd.resolve("backend/test-agent-persistence/src/main/resources/db/migration"));
        return candidates.stream()
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot locate Flyway migration directory from " + cwd));
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
        return Integer.parseInt(version) <= 17;
    }
}
