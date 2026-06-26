package com.icbc.testagent.workspace;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.net.LinuxServerIpResolver;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 当前 Java 后端实例的工作空间服务器身份；workspace-management 不依赖 opencode-runtime，
 * 通过 {@link LinuxServerIpResolver} 自动探测当前服务器真实内网 IP 作为服务器 ID。
 */
@Component
public class WorkspaceServerIdentity {

    private final String linuxServerId;
    private final String defaultDirectory;

    /**
     * 生产构造器：从 {@link LinuxServerIpResolver} 探测真实内网 IP；默认目录使用当前 Java 进程运行目录。
     */
    @Autowired
    public WorkspaceServerIdentity(LinuxServerIpResolver linuxServerIpResolver) {
        this(linuxServerIpResolver.resolve(), Path.of("").toAbsolutePath().normalize().toString());
    }

    /**
     * 测试/便捷构造器，允许固定服务器 ID；默认目录使用当前 Java 进程运行目录。
     */
    public WorkspaceServerIdentity(String linuxServerId) {
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
