package com.enterprise.testagent.app.config;

import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 将当前后端实例解析出的稳定服务器身份和网络地址写入 Go manager 可读取的约定文件。
 */
final class ServerIdentityFileWriter {

    private final Supplier<Path> serverIdFileResolver;
    private final Supplier<Path> serverHostFileResolver;

    ServerIdentityFileWriter(Path serverIdFile, Path serverHostFile) {
        this(fixedPath(serverIdFile), fixedPath(serverHostFile));
    }

    ServerIdentityFileWriter(ServerIdentityFilePathResolver pathResolver) {
        this(pathResolver::serverIdFile, pathResolver::serverHostFile);
    }

    private ServerIdentityFileWriter(Supplier<Path> serverIdFileResolver, Supplier<Path> serverHostFileResolver) {
        this.serverIdFileResolver = Objects.requireNonNull(serverIdFileResolver, "serverIdFileResolver must not be null");
        this.serverHostFileResolver = Objects.requireNonNull(serverHostFileResolver, "serverHostFileResolver must not be null");
    }

    private static Supplier<Path> fixedPath(Path path) {
        Path fixed = Objects.requireNonNull(path, "path must not be null");
        return () -> fixed;
    }

    /**
     * 覆盖写入单行身份和地址；父目录不存在时会先创建。
     */
    void write(String linuxServerId, String advertisedHost) {
        writeLine(serverIdFileResolver.get(), new LinuxServerId(linuxServerId).value(), "服务器身份文件");
        writeLine(serverHostFileResolver.get(), ServerAdvertisedHostResolver.normalizeHost(advertisedHost), "服务器地址文件");
    }

    private void writeLine(Path file, String value, String label) {
        Path resolved = Objects.requireNonNull(file, label + "路径 must not be null");
        try {
            Path parent = resolved.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    resolved,
                    value + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("写入" + label + "失败: " + resolved, e);
        }
    }
}
