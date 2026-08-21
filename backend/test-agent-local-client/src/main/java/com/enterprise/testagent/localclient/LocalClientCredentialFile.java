package com.enterprise.testagent.localclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** 原子保存统一认证号和 client key；禁止命令行、环境变量及宽权限文件。 */
final class LocalClientCredentialFile {

    private static final String FILE_NAME = "credentials.properties";
    private static final String LEGACY_KEY_FILE_NAME = "client.key";

    private LocalClientCredentialFile() {
    }

    static Credentials read() throws IOException {
        return read(LocalClientPaths.configDirectory());
    }

    static Credentials read(Path configDirectory) throws IOException {
        Path normalizedDirectory = configDirectory.toAbsolutePath().normalize();
        requirePrivatePermissions(normalizedDirectory, "credential directory permissions must be 0700");
        Path path = normalizedDirectory.resolve(FILE_NAME);
        if (!Files.exists(path)) {
            return readLegacy(normalizedDirectory);
        }
        requirePrivatePermissions(path, "credentials.properties permissions must be 0600");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new Credentials(
                requireUnifiedAuthId(properties.getProperty("unifiedAuthId")),
                requireClientKey(properties.getProperty("clientKey")));
    }

    static void write(String unifiedAuthId, String clientKey) throws IOException {
        write(LocalClientPaths.configDirectory(), unifiedAuthId, clientKey);
    }

    static void write(Path configDirectory, String unifiedAuthId, String clientKey) throws IOException {
        String normalizedUnifiedAuthId = requireUnifiedAuthId(unifiedAuthId);
        String normalizedClientKey = requireClientKey(clientKey);
        Path normalizedDirectory = configDirectory.toAbsolutePath().normalize();
        Files.createDirectories(normalizedDirectory);
        setPermissions(normalizedDirectory, "rwx------");

        Properties properties = new Properties();
        properties.setProperty("unifiedAuthId", normalizedUnifiedAuthId);
        properties.setProperty("clientKey", normalizedClientKey);
        Path temporary = Files.createTempFile(normalizedDirectory, ".credentials-", ".tmp");
        try {
            setPermissions(temporary, "rw-------");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "TestAgent local client credentials - keep private");
            }
            moveAtomically(temporary, normalizedDirectory.resolve(FILE_NAME));
            setPermissions(normalizedDirectory.resolve(FILE_NAME), "rw-------");
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Credentials credentials(String unifiedAuthId, String clientKey) {
        return new Credentials(requireUnifiedAuthId(unifiedAuthId), requireClientKey(clientKey));
    }

    private static Credentials readLegacy(Path configDirectory) throws IOException {
        Path legacyKey = configDirectory.resolve(LEGACY_KEY_FILE_NAME);
        requirePrivatePermissions(legacyKey, "client.key permissions must be 0600");
        return new Credentials(null, requireClientKey(Files.readString(legacyKey)));
    }

    private static String requireUnifiedAuthId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("unifiedAuthId is required");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()
                || normalized.length() > 255
                || normalized.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new IllegalArgumentException("unifiedAuthId is invalid");
        }
        return normalized;
    }

    private static String requireClientKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("client key is required");
        }
        String normalized = value.trim();
        if (!normalized.startsWith("tack_v1_") || normalized.length() > 128 || normalized.length() <= 8) {
            throw new IllegalArgumentException("client key is invalid");
        }
        return normalized;
    }

    private static void requirePrivatePermissions(Path path, String message) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException(path.getFileName() + " does not exist");
        }
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            if (permissions.contains(PosixFilePermission.GROUP_READ)
                    || permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.GROUP_EXECUTE)
                    || permissions.contains(PosixFilePermission.OTHERS_READ)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
                throw new IllegalStateException(message);
            }
        } catch (UnsupportedOperationException ignored) {
            // 本期麒麟 ARM 使用 POSIX；保留未来平台通过目录 ACL 收紧权限的兼容入口。
        }
    }

    private static void setPermissions(Path path, String permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        } catch (UnsupportedOperationException ignored) {
            // 本期麒麟 ARM 使用 POSIX；不支持 POSIX 时由平台 ACL 负责。
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static final class Credentials {
        private final String unifiedAuthId;
        private final String clientKey;

        private Credentials(String unifiedAuthId, String clientKey) {
            this.unifiedAuthId = unifiedAuthId;
            this.clientKey = Objects.requireNonNull(clientKey, "clientKey must not be null");
        }

        String unifiedAuthId() {
            return unifiedAuthId;
        }

        String clientKey() {
            return clientKey;
        }

        @Override
        public String toString() {
            return "Credentials[unifiedAuthId=" + unifiedAuthId + ", clientKey=***]";
        }
    }
}
