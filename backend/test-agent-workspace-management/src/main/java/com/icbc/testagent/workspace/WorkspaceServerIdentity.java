package com.icbc.testagent.workspace;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 当前 Java 后端实例的工作空间服务器身份；workspace-management 不依赖 opencode-runtime，只读取稳定配置值。
 */
@Component
public class WorkspaceServerIdentity {

    private final String linuxServerId;
    private final String defaultDirectory;

    /**
     * 从配置解析当前后端所在 Linux 服务器 ID；默认目录使用当前 Java 进程运行目录。
     */
    public WorkspaceServerIdentity(
            @Value("${test-agent.workspace.server-id:${test-agent.opencode.manager-control.linux-server-id:127.0.0.1}}")
            String linuxServerId) {
        this(linuxServerId, Path.of("").toAbsolutePath().normalize().toString());
    }

    /**
     * 测试构造器允许固定服务器 ID 和默认目录。
     */
    public WorkspaceServerIdentity(String linuxServerId, String defaultDirectory) {
        if (linuxServerId == null || linuxServerId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "工作空间服务器 ID 不能为空", Map.of());
        }
        this.linuxServerId = linuxServerId.trim();
        this.defaultDirectory = defaultDirectory == null || defaultDirectory.isBlank()
                ? Path.of("").toAbsolutePath().normalize().toString()
                : defaultDirectory.trim();
    }

    /**
     * 当前后端绑定的 Linux 服务器 ID。
     */
    public String linuxServerId() {
        return linuxServerId;
    }

    /**
     * 当前 Java 进程运行目录，用作服务器目录选择器的默认入口。
     */
    public String defaultDirectory() {
        return defaultDirectory;
    }
}
