package com.icbc.testagent.app.config;

import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * 将当前后端实例解析出的服务器 IPv4 写入 Go manager 可读取的约定文件。
 */
final class ServerIpFileWriter {

    private final Path serverIpFile;

    ServerIpFileWriter(Path serverIpFile) {
        this.serverIpFile = Objects.requireNonNull(serverIpFile, "serverIpFile must not be null");
    }

    /**
     * 写入单行 IPv4，并覆盖旧内容；父目录不存在时会先创建。
     */
    void write(String serverIp) {
        String normalized = new LinuxServerId(serverIp).value();
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
