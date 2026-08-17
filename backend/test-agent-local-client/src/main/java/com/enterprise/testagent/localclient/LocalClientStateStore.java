package com.enterprise.testagent.localclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** 使用用户私有权限和原子 rename 保存稳定实例 ID、进程身份与工作区根映射。 */
final class LocalClientStateStore {

    private final Path stateDirectory;
    private final Path stateFile;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    LocalClientStateStore() throws IOException {
        this(LocalClientPaths.stateDirectory());
    }

    LocalClientStateStore(Path stateDirectory) throws IOException {
        this.stateDirectory = stateDirectory.toAbsolutePath().normalize();
        this.stateFile = this.stateDirectory.resolve("state.json");
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
