package com.enterprise.testagent.localclient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 导出受限客户端日志；不读取密钥、配置、OpenCode 日志或工作区。 */
final class LocalClientLogExporter {

    private static final int MAX_FILES = 20;
    private static final long MAX_BYTES_PER_FILE = 10L * 1024 * 1024;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private LocalClientLogExporter() {
    }

    static Path export(Path logsDirectory, Path downloadsDirectory) throws IOException {
        return export(logsDirectory, downloadsDirectory, Clock.systemDefaultZone());
    }

    static Path export(Path logsDirectory, Path downloadsDirectory, Clock clock) throws IOException {
        List<Path> logs = clientLogs(logsDirectory);
        if (logs.isEmpty()) {
            throw new IOException("暂时没有可下载的客户端日志");
        }
        Files.createDirectories(downloadsDirectory);
        String timestamp = FILE_TIME.format(LocalDateTime.now(clock));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Path archive = downloadsDirectory.resolve("TestAgent-client-logs-" + timestamp + "-" + suffix + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(
                archive, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            for (Path log : logs) {
                writeTail(output, log);
            }
        } catch (IOException exception) {
            Files.deleteIfExists(archive);
            throw exception;
        }
        return archive;
    }

    private static List<Path> clientLogs(Path logsDirectory) throws IOException {
        if (!Files.isDirectory(logsDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsDirectory)) {
            for (Path candidate : stream) {
                String name = candidate.getFileName().toString();
                if (isClientLogName(name) && Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    result.add(candidate);
                }
            }
        }
        result.sort(Comparator.comparingLong(LocalClientLogExporter::lastModified).reversed());
        return result.stream().limit(MAX_FILES).toList();
    }

    private static boolean isClientLogName(String name) {
        if ("client.log".equals(name) || "client-error.log".equals(name)) {
            return true;
        }
        return (name.startsWith("client-") || name.startsWith("client-error-"))
                && (name.endsWith(".log") || name.endsWith(".log.gz"));
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static void writeTail(ZipOutputStream output, Path log) throws IOException {
        long size = Files.size(log);
        long bytesToCopy = Math.min(size, MAX_BYTES_PER_FILE);
        ZipEntry entry = new ZipEntry(log.getFileName().toString());
        entry.setTime(Files.getLastModifiedTime(log, LinkOption.NOFOLLOW_LINKS).toMillis());
        output.putNextEntry(entry);
        try (InputStream input = Files.newInputStream(log)) {
            input.skipNBytes(size - bytesToCopy);
            input.transferTo(new BoundedOutputStream(output, bytesToCopy));
        }
        output.closeEntry();
    }

    /** 防止日志在读取期间继续增长而突破单文件导出上限。 */
    private static final class BoundedOutputStream extends java.io.OutputStream {
        private final ZipOutputStream delegate;
        private long remaining;

        private BoundedOutputStream(ZipOutputStream delegate, long remaining) {
            this.delegate = delegate;
            this.remaining = remaining;
        }

        @Override
        public void write(int value) throws IOException {
            if (remaining > 0) {
                delegate.write(value);
                remaining--;
            }
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            int accepted = (int) Math.min(remaining, length);
            if (accepted > 0) {
                delegate.write(buffer, offset, accepted);
                remaining -= accepted;
            }
        }
    }
}
