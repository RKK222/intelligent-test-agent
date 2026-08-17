package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientLogExporterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsOnlyBoundedClientLogs() throws Exception {
        Path logs = temporaryDirectory.resolve("state/logs");
        Path downloads = temporaryDirectory.resolve("downloads");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("client.log"), "client-line\n");
        Files.writeString(logs.resolve("client-error.log"), "error-line\n");
        Files.writeString(logs.resolve("opencode.log"), "workspace prompt must not be exported\n");
        Files.writeString(temporaryDirectory.resolve("client.key"), "tack_v1_secret-value\n");

        Path archive = LocalClientLogExporter.export(
                logs,
                downloads,
                Clock.fixed(Instant.parse("2026-08-15T08:30:00Z"), ZoneOffset.UTC));

        Map<String, String> entries = unzip(archive);
        assertThat(entries).containsOnlyKeys("client.log", "client-error.log");
        assertThat(entries.values()).allMatch(value -> !value.contains("secret-value"));
        assertThat(entries.values()).allMatch(value -> !value.contains("workspace prompt"));
    }

    @Test
    void keepsOnlyLastTenMebibytesWhenLogGrowsBeyondLimit() throws Exception {
        Path logs = temporaryDirectory.resolve("large/logs");
        Files.createDirectories(logs);
        byte[] oversized = new byte[10 * 1024 * 1024 + 32];
        java.util.Arrays.fill(oversized, (byte) 'a');
        byte[] tail = "tail-marker".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(tail, 0, oversized, oversized.length - tail.length, tail.length);
        Files.write(logs.resolve("client.log"), oversized);

        Path archive = LocalClientLogExporter.export(
                logs,
                temporaryDirectory.resolve("large/downloads"),
                Clock.systemUTC());
        byte[] exported = unzipBytes(archive).get("client.log");

        assertThat(exported).hasSize(10 * 1024 * 1024);
        assertThat(new String(exported, exported.length - tail.length, tail.length, StandardCharsets.UTF_8))
                .isEqualTo("tail-marker");
    }

    private static Map<String, String> unzip(Path archive) throws Exception {
        Map<String, String> result = new HashMap<>();
        unzipBytes(archive).forEach((name, value) -> result.put(name, new String(value, StandardCharsets.UTF_8)));
        return result;
    }

    private static Map<String, byte[]> unzipBytes(Path archive) throws Exception {
        Map<String, byte[]> result = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                input.transferTo(output);
                result.put(entry.getName(), output.toByteArray());
            }
        }
        return result;
    }
}
