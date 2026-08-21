package com.enterprise.testagent.localclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** 使用用户私有权限和原子 rename 保存稳定实例 ID、进程身份与工作区根映射。 */
final class LocalClientStateStore {

    private final Path stateDirectory;
    private final Path stateFile;
    private final Path reEnrollmentRequiredFile;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    LocalClientStateStore() throws IOException {
        this(LocalClientPaths.stateDirectory());
    }

    LocalClientStateStore(Path stateDirectory) throws IOException {
        this.stateDirectory = stateDirectory.toAbsolutePath().normalize();
        this.stateFile = this.stateDirectory.resolve("state.json");
        this.reEnrollmentRequiredFile = this.stateDirectory.resolve("re-enrollment-required");
        createPrivateDirectory(this.stateDirectory);
        if (!Files.exists(stateFile)) {
            write(new LocalClientPersistentState(newInstanceId(), null, Map.of()));
        }
    }

    synchronized LocalClientPersistentState read() {
        try {
            LocalClientPersistentState state = objectMapper.readValue(stateFile.toFile(), LocalClientPersistentState.class);
            if (state.clientInstanceId() == null || !state.clientInstanceId().startsWith("lci_")) {
                throw new IllegalStateException("state.json clientInstanceId is invalid");
            }
            return state;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read local client state", exception);
        }
    }

    synchronized LocalClientPersistentState update(UnaryOperator<LocalClientPersistentState> updater) {
        LocalClientPersistentState updated = updater.apply(read());
        write(updated);
        return updated;
    }

    synchronized void requireReEnrollment() {
        try {
            Files.writeString(
                    reEnrollmentRequiredFile,
                    "本地客户端凭据已失效，请手动重新接入。\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.setPosixFilePermissions(
                        reEnrollmentRequiredFile, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // 本期麒麟 ARM 使用 POSIX；未来平台由用户目录 ACL 收紧权限。
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to persist re-enrollment state", exception);
        }
    }

    synchronized boolean reEnrollmentRequired() {
        return Files.isRegularFile(reEnrollmentRequiredFile);
    }

    synchronized void clearReEnrollmentRequirement() {
        try {
            Files.deleteIfExists(reEnrollmentRequiredFile);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to clear re-enrollment state", exception);
        }
    }

    /** 更新 marker 必须位于发布目录之外，与稳定实例状态共享同一用户私有根。 */
    Path stateDirectory() {
        return stateDirectory;
    }

    private void write(LocalClientPersistentState state) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(stateDirectory, ".state-", ".json");
            try {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // 目标平台支持 POSIX；不支持时仍依赖用户目录 ACL。
            }
            objectMapper.writeValue(temporary.toFile(), state);
            try {
                Files.move(temporary, stateFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("failed to persist local client state", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 最终 rename 已成功或临时文件由下次启动清理。
                }
            }
        }
    }

    private static void createPrivateDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        try {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // 目标平台支持 POSIX；不支持时仍依赖用户目录 ACL。
        }
    }

    private static String newInstanceId() {
        return "lci_" + UUID.randomUUID().toString().replace("-", "");
    }
}
