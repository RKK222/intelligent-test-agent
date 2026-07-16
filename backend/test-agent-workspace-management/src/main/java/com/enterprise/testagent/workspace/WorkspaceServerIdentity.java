package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 当前 Java 后端实例的工作空间服务器身份；workspace-management 不依赖 opencode-runtime，
 * 与运行管理一致使用稳定 linuxServerId 绑定本机工作空间和文件 WebSocket 路由。
 */
@Component
public class WorkspaceServerIdentity {

    private static final String SERVER_ID_ENV = "TEST_AGENT_LINUX_SERVER_ID";

    private final String linuxServerId;
    private final String defaultDirectory;

    /**
     * 生产构造器：环境变量优先，缺失时读取当前 Java 主机名；默认目录使用当前 Java 进程运行目录。
     */
    @Autowired
    public WorkspaceServerIdentity() {
        this(resolveLinuxServerId(), Path.of("").toAbsolutePath().normalize().toString());
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
        this.linuxServerId = new LinuxServerId(linuxServerId).value();
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

    private static String resolveLinuxServerId() {
        String configured = System.getenv(SERVER_ID_ENV);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            throw new PlatformException(
                    ErrorCode.INTERNAL_ERROR,
                    "未配置 " + SERVER_ID_ENV + "，且无法读取机器名称",
                    Map.of(),
                    exception);
        }
    }
}
