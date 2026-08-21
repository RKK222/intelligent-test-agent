package com.enterprise.testagent.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientTarGzExtractorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldExtractOnlyRegularFilesAndDirectoriesBelowExpectedRoot() throws Exception {
        Path archive = temporaryDirectory.resolve("jdk.tar.gz");
        Files.write(archive, tarGz(
                entry("jdk/", new byte[0], '5', 0755),
                entry("jdk/bin/", new byte[0], '5', 0755),
                entry("jdk/bin/java", "java".getBytes(StandardCharsets.UTF_8), '0', 0755)));
        Path destination = temporaryDirectory.resolve("release");
        Files.createDirectory(destination);

        new LocalClientTarGzExtractor().extract(archive, destination);

        assertThat(destination.resolve("jdk/bin/java")).hasContent("java").isExecutable();
    }

    @Test
    void shouldRejectTraversalAndSymbolicLinks() throws Exception {
        Path traversal = temporaryDirectory.resolve("jdk.tar.gz");
        Files.write(traversal, tarGz(entry("../escape", new byte[] {1}, '0', 0644)));
        Path traversalDestination = temporaryDirectory.resolve("traversal-release");
        Files.createDirectory(traversalDestination);

        assertThatThrownBy(() -> new LocalClientTarGzExtractor().extract(traversal, traversalDestination))
                .isInstanceOf(SecurityException.class);
        assertThat(temporaryDirectory.resolve("escape")).doesNotExist();

        Path symbolicLink = temporaryDirectory.resolve("opencode.tar.gz");
        Files.write(symbolicLink, tarGz(entry("opencode/link", new byte[0], '2', 0777)));
        Path linkDestination = temporaryDirectory.resolve("link-release");
        Files.createDirectory(linkDestination);
        assertThatThrownBy(() -> new LocalClientTarGzExtractor().extract(symbolicLink, linkDestination))
                .isInstanceOf(SecurityException.class);
    }

    private static byte[] tarGz(TarEntry... entries) throws Exception {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            for (TarEntry entry : entries) {
                byte[] header = new byte[512];
                writeAscii(header, 0, 100, entry.name());
                writeOctal(header, 100, 8, entry.mode());
                writeOctal(header, 108, 8, 0);
                writeOctal(header, 116, 8, 0);
                writeOctal(header, 124, 12, entry.content().length);
                writeOctal(header, 136, 12, 0);
                java.util.Arrays.fill(header, 148, 156, (byte) ' ');
                header[156] = (byte) entry.type();
                writeAscii(header, 257, 6, "ustar");
                long checksum = 0;
                for (byte value : header) {
                    checksum += Byte.toUnsignedInt(value);
                }
                writeOctal(header, 148, 8, checksum);
                gzip.write(header);
                gzip.write(entry.content());
                int padding = (512 - entry.content().length % 512) % 512;
                gzip.write(new byte[padding]);
            }
            gzip.write(new byte[1024]);
        }
        return compressed.toByteArray();
    }

    private static void writeAscii(byte[] target, int offset, int length, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, target, offset, Math.min(length, encoded.length));
    }

    private static void writeOctal(byte[] target, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        String padded = "0".repeat(Math.max(0, length - octal.length() - 1)) + octal;
        writeAscii(target, offset, length - 1, padded);
        target[offset + length - 1] = 0;
    }

    private record TarEntry(String name, byte[] content, char type, int mode) {
    }

    private static TarEntry entry(String name, byte[] content, char type, int mode) {
        return new TarEntry(name, content, type, mode);
    }
}
