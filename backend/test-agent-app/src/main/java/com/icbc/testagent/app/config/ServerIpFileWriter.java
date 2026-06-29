package com.icbc.testagent.app.config;

import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 将当前后端实例解析出的服务器 IPv4 写入 Go manager 可读取的约定文件。
 */
final class ServerIpFileWriter {

    private final Supplier<Path> serverIpFileResolver;

    ServerIpFileWriter(Path serverIpFile) {
        this(fixedPath(serverIpFile));
    }

    ServerIpFileWriter(ServerIpFilePathResolver serverIpFilePathResolver) {
        this(serverIpFilePathResolver::resolve);
    }

    private ServerIpFileWriter(Supplier<Path> serverIpFileResolver) {
        this.serverIpFileResolver = Objects.requireNonNull(serverIpFileResolver, "serverIpFileResolver must not be null");
    }

    private static Supplier<Path> fixedPath(Path serverIpFile) {
        Path fixed = Objects.requireNonNull(serverIpFile, "serverIpFile must not be null");
        return () -> fixed;
    }

    /**
     * 写入单行 IPv4，并覆盖旧内容；父目录不存在时会先创建。
     */
    void write(String serverIp) {
        String normalized = new LinuxServerId(serverIp).value();
        Path serverIpFile = Objects.requireNonNull(serverIpFileResolver.get(), "serverIpFile must not be null");
        try {
            Path parent = serverIpFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    serverIpFile,
                    normalized + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("写入服务器 IP 文件失败: " + serverIpFile, e);
        }
    }
}
