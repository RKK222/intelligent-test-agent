package com.enterprise.testagent.localclient;

import com.enterprise.testagent.common.localclient.LocalClientReleaseVersion;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/** Java 与稳定启动 Shell 之间的私有、原子更新交接标记。 */
final class LocalClientUpdateMarkerStore {

    static final String PENDING_FILE_NAME = "pending-update.properties";
    static final String RESULT_FILE_NAME = "update-result.properties";
    static final String ACTIVATION_FILE_NAME = "update-activation.properties";
    private static final String SCHEMA_VERSION = "1";
    private static final List<String> RESULT_STATUSES = List.of("SUCCEEDED", "AUTO_ROLLED_BACK", "FAILED");

    private final Path stateDirectory;
    private final Path pendingPath;
    private final Path resultPath;
    private final Path activationPath;

    LocalClientUpdateMarkerStore(Path stateDirectory) throws IOException {
        this.stateDirectory = stateDirectory.toAbsolutePath().normalize();
        Files.createDirectories(this.stateDirectory);
        if (Files.isSymbolicLink(this.stateDirectory)
                || !Files.isDirectory(this.stateDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("local client state directory is unsafe");
        }
        setPrivateDirectoryPermissions(this.stateDirectory);
        this.pendingPath = this.stateDirectory.resolve(PENDING_FILE_NAME);
        this.resultPath = this.stateDirectory.resolve(RESULT_FILE_NAME);
        this.activationPath = this.stateDirectory.resolve(ACTIVATION_FILE_NAME);
    }

    synchronized PendingUpdate writePending(
            LocalClientPayloads.UpdateCommand command,
            String currentVersion,
            String releaseDigest,
            Instant preparedAt) {
        Objects.requireNonNull(command, "update command must not be null");
        PendingUpdate pending = new PendingUpdate(
                command.commandId(),
                command.clientInstanceId(),
                command.connectionGeneration(),
                command.policyRevision(),
                currentVersion,
                command.targetVersion(),
                command.direction(),
                releaseDigest,
                preparedAt);
        Optional<PendingUpdate> existing = readPending();
        if (existing.isPresent()) {
            if (!existing.get().equals(pending)) {
                throw new IllegalStateException("another local client update is already pending");
            }
            return existing.get();
        }
        writeAtomically(pendingPath, pendingLines(pending));
        return pending;
    }

    synchronized Optional<PendingUpdate> readPending() {
        return readProperties(pendingPath).map(LocalClientUpdateMarkerStore::pendingFrom);
    }

    synchronized void writeResult(UpdateResult result) {
        Objects.requireNonNull(result, "update result must not be null");
        // record 构造器已经执行完整字段和版本方向校验。
        Optional<UpdateResult> existing = readResult();
        if (existing.isPresent() && !existing.get().equals(result)) {
            throw new IllegalStateException("a different local client update result already exists");
        }
        if (existing.isEmpty()) {
            StringBuilder lines = new StringBuilder(pendingLines(result.pending()));
            append(lines, "status", result.status());
            append(lines, "errorCode", result.errorCode() == null ? "" : result.errorCode());
            append(lines, "actualVersion", result.actualVersion());
            append(lines, "observedAt", result.observedAt().toString());
            writeAtomically(resultPath, lines.toString());
        }
    }

    synchronized Optional<UpdateResult> readResult() {
        return readProperties(resultPath).map(properties -> new UpdateResult(
                pendingFrom(properties),
                required(properties, "status"),
                optional(properties, "errorCode"),
                required(properties, "actualVersion"),
                Instant.parse(required(properties, "observedAt"))));
    }

    synchronized void writeActivation(ActivationResult result) {
        Objects.requireNonNull(result, "activation result must not be null");
        Optional<ActivationResult> existing = readActivation();
        if (existing.isPresent() && !existing.get().equals(result)) {
            throw new IllegalStateException("a different local client activation result already exists");
        }
        if (existing.isEmpty()) {
            StringBuilder lines = new StringBuilder(pendingLines(result.pending()));
            append(lines, "status", result.status());
            append(lines, "errorCode", result.errorCode() == null ? "" : result.errorCode());
            append(lines, "actualVersion", result.actualVersion());
            append(lines, "observedAt", result.observedAt().toString());
            writeAtomically(activationPath, lines.toString());
        }
    }

    synchronized Optional<ActivationResult> readActivation() {
        return readProperties(activationPath).map(properties -> new ActivationResult(
                pendingFrom(properties),
                required(properties, "status"),
                optional(properties, "errorCode"),
                required(properties, "actualVersion"),
                Instant.parse(required(properties, "observedAt"))));
    }

    synchronized void clearPending() {
        delete(pendingPath);
    }

    synchronized void clearResult() {
        delete(resultPath);
    }

    synchronized void clearActivation() {
        delete(activationPath);
    }

    Path pendingPath() {
        return pendingPath;
    }

    Path resultPath() {
        return resultPath;
    }

    Path activationPath() {
        return activationPath;
    }

    private static PendingUpdate pendingFrom(Properties properties) {
        if (!SCHEMA_VERSION.equals(required(properties, "schemaVersion"))) {
            throw new IllegalStateException("local client update marker schema is unsupported");
        }
        return new PendingUpdate(
                required(properties, "commandId"),
                required(properties, "clientInstanceId"),
                positiveLong(properties, "connectionGeneration"),
                positiveLong(properties, "policyRevision"),
                required(properties, "currentVersion"),
                required(properties, "targetVersion"),
                required(properties, "direction"),
                required(properties, "releaseDigest"),
                Instant.parse(required(properties, "preparedAt")));
    }

    private static String pendingLines(PendingUpdate pending) {
        StringBuilder lines = new StringBuilder();
        append(lines, "schemaVersion", SCHEMA_VERSION);
        append(lines, "commandId", pending.commandId());
        append(lines, "clientInstanceId", pending.clientInstanceId());
        append(lines, "connectionGeneration", Long.toString(pending.connectionGeneration()));
        append(lines, "policyRevision", Long.toString(pending.policyRevision()));
        append(lines, "currentVersion", pending.currentVersion());
        append(lines, "targetVersion", pending.targetVersion());
        append(lines, "direction", pending.direction());
        append(lines, "releaseDigest", pending.releaseDigest());
        append(lines, "preparedAt", pending.preparedAt().toString());
        return lines.toString();
    }

    private static void append(StringBuilder output, String key, String value) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('=') >= 0) {
            throw new IllegalArgumentException("local client update marker value is unsafe");
        }
        output.append(key).append('=').append(value).append('\n');
    }

    private Optional<Properties> readProperties(Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new SecurityException("local client update marker is not a regular file");
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return Optional.of(properties);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read local client update marker", exception);
        }
    }

    private void writeAtomically(Path target, String content) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(stateDirectory, ".update-marker-", ".tmp");
            Files.writeString(
                    temporary,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            setPrivateFilePermissions(temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            setPrivateFilePermissions(target);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to persist local client update marker", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 原子切换成功后临时路径已不存在；失败残留仍是 0600 且不参与启动器选择。
                }
            }
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to clear local client update marker", exception);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("local client update marker field is missing");
        }
        return value.trim();
    }

    private static String optional(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static long positiveLong(Properties properties, String key) {
        try {
            long value = Long.parseLong(required(properties, key));
            if (value < 1) {
                throw new NumberFormatException("not positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("local client update marker number is invalid", exception);
        }
    }

    private static void setPrivateDirectoryPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // 麒麟使用 POSIX；其它开发平台依赖用户目录 ACL。
        }
    }

    private static void setPrivateFilePermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // 麒麟使用 POSIX；其它开发平台依赖用户目录 ACL。
        }
    }

    record PendingUpdate(
            String commandId,
            String clientInstanceId,
            long connectionGeneration,
            long policyRevision,
            String currentVersion,
            String targetVersion,
            String direction,
            String releaseDigest,
            Instant preparedAt) {

        PendingUpdate {
            if (commandId == null || !commandId.matches("[A-Za-z0-9_]{1,128}")
                    || clientInstanceId == null || !clientInstanceId.matches("lci_[A-Za-z0-9_-]{1,124}")
                    || connectionGeneration < 1
                    || policyRevision < 1
                    || releaseDigest == null
                    || !releaseDigest.matches("[0-9a-f]{64}")
                    || preparedAt == null) {
                throw new IllegalArgumentException("pending local client update coordinates are invalid");
            }
            String current = LocalClientReleaseVersion.parse(currentVersion).value();
            String target = LocalClientReleaseVersion.parse(targetVersion).value();
            String expectedDirection = LocalClientReleaseVersion.parse(current)
                    .directionTo(LocalClientReleaseVersion.parse(target))
                    .name();
            if ("SAME".equals(expectedDirection) || !expectedDirection.equals(direction)) {
                throw new IllegalArgumentException("pending local client update direction is invalid");
            }
            currentVersion = current;
            targetVersion = target;
        }
    }

    record UpdateResult(
            PendingUpdate pending,
            String status,
            String errorCode,
            String actualVersion,
            Instant observedAt) {

        UpdateResult {
            Objects.requireNonNull(pending, "pending update must not be null");
            if (!RESULT_STATUSES.contains(status)
                    || observedAt == null
                    || (errorCode != null && !errorCode.matches("[A-Z0-9_]{1,128}"))) {
                throw new IllegalArgumentException("local client update result is invalid");
            }
            actualVersion = LocalClientReleaseVersion.parse(actualVersion).value();
            if (("SUCCEEDED".equals(status) && !actualVersion.equals(pending.targetVersion()))
                    || ("AUTO_ROLLED_BACK".equals(status) && !actualVersion.equals(pending.currentVersion()))
                    || ("SUCCEEDED".equals(status) && errorCode != null)
                    || (!"SUCCEEDED".equals(status) && errorCode == null)) {
                throw new IllegalArgumentException("local client update result does not match its version transition");
            }
        }
    }

    record ActivationResult(
            PendingUpdate pending,
            String status,
            String errorCode,
            String actualVersion,
            Instant observedAt) {

        ActivationResult {
            Objects.requireNonNull(pending, "pending update must not be null");
            if (!("READY".equals(status) || "FAILED".equals(status))
                    || observedAt == null
                    || (errorCode != null && !errorCode.matches("[A-Z0-9_]{1,128}"))) {
                throw new IllegalArgumentException("local client activation result is invalid");
            }
            actualVersion = LocalClientReleaseVersion.parse(actualVersion).value();
            if (!actualVersion.equals(pending.targetVersion())
                    || ("READY".equals(status) && errorCode != null)
                    || ("FAILED".equals(status) && errorCode == null)) {
                throw new IllegalArgumentException("local client activation result does not match target release");
            }
        }
    }
}
