package com.enterprise.testagent.localclient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 本地工作区根注册表；每次 RPC 重验真实路径摘要和文件系统身份，换根时失败关闭。 */
final class LocalWorkspaceRegistry {

    private final LocalClientStateStore stateStore;

    LocalWorkspaceRegistry(LocalClientStateStore stateStore) {
        this.stateStore = stateStore;
    }

    Registration validate(String absolutePath) {
        try {
            Path requested = Path.of(absolutePath);
            if (!requested.isAbsolute()) {
                throw new IllegalArgumentException("workspace root must be absolute");
            }
            if (Files.isSymbolicLink(requested)) {
                throw new IllegalArgumentException("workspace root must not be a symbolic link");
            }
            Path realRoot = requested.toRealPath();
            if (!Files.isDirectory(realRoot, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(realRoot)
                    || !Files.isWritable(realRoot)
                    || !Files.isExecutable(realRoot)) {
                throw new IllegalArgumentException("workspace root must be a readable and writable directory");
            }
            return new Registration(realRoot.toString(), digest(realRoot.toString()), fileSystemIdentity(realRoot));
        } catch (IOException exception) {
            throw new IllegalArgumentException("workspace root cannot be resolved", exception);
        }
    }

    Registration register(String workspaceId, String absolutePath) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        Registration registration = validate(absolutePath);
        stateStore.update(state -> {
            Map<String, LocalClientPersistentState.WorkspaceRoot> roots = new LinkedHashMap<>(state.workspaces());
            roots.put(workspaceId, new LocalClientPersistentState.WorkspaceRoot(
                    registration.normalizedRootPath(),
                    registration.rootDigest(),
                    registration.fileSystemIdentity()));
            return new LocalClientPersistentState(state.clientInstanceId(), state.process(), roots);
        });
        return registration;
    }

    void unregister(String workspaceId) {
        stateStore.update(state -> {
            Map<String, LocalClientPersistentState.WorkspaceRoot> roots = new LinkedHashMap<>(state.workspaces());
            roots.remove(workspaceId);
            return new LocalClientPersistentState(state.clientInstanceId(), state.process(), roots);
        });
    }

    String requireRoot(String workspaceId, String expectedRootDigest) {
        LocalClientPersistentState.WorkspaceRoot root = stateStore.read().workspaces().get(workspaceId);
        if (root == null) {
            throw new IllegalArgumentException("workspace is not registered on this client");
        }
        if (expectedRootDigest == null || !expectedRootDigest.equals(root.rootDigest())) {
            throw new IllegalStateException("workspace root digest does not match ticket");
        }
        try {
            Path current = Path.of(root.normalizedRootPath()).toRealPath();
            if (!digest(current.toString()).equals(root.rootDigest())
                    || !fileSystemIdentity(current).equals(root.fileSystemIdentity())) {
                throw new IllegalStateException("workspace root identity changed after registration");
            }
            return current.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("workspace root is no longer available", exception);
        }
    }

    List<DirectoryEntry> listAbsolute(String absolutePath, int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("directory entry limit is invalid");
        }
        try {
            Path directory = Path.of(absolutePath);
            if (!directory.isAbsolute()) {
                throw new IllegalArgumentException("directory picker path must be absolute");
            }
            Path realDirectory = directory.toRealPath();
            if (!Files.isDirectory(realDirectory)) {
                throw new IllegalArgumentException("directory picker target is not a directory");
            }
            List<DirectoryEntry> result = new ArrayList<>();
            try (var stream = Files.list(realDirectory)) {
                stream.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                        .limit(limit)
                        .forEach(path -> result.add(new DirectoryEntry(
                                path.getFileName().toString(),
                                path.toAbsolutePath().normalize().toString(),
                                Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS),
                                Files.isSymbolicLink(path),
                                Files.isReadable(path))));
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new IllegalArgumentException("directory picker target cannot be read", exception);
        }
    }

    private static String fileSystemIdentity(Path root) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Object fileKey = attributes.fileKey();
        if (fileKey == null) {
            throw new IOException("workspace filesystem does not expose a stable file identity");
        }
        var fileStore = Files.getFileStore(root);
        return fileStore.name() + "|" + fileStore.type() + "|" + fileKey;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not support SHA-256", exception);
        }
    }

    record Registration(String normalizedRootPath, String rootDigest, String fileSystemIdentity) {
    }

    record DirectoryEntry(String name, String absolutePath, boolean directory, boolean symbolicLink, boolean readable) {
    }
}
