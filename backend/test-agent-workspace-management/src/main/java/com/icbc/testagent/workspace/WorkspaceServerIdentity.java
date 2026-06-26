package com.icbc.testagent.workspace;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 当前 Java 后端实例的工作空间服务器身份；workspace-management 不依赖 opencode-runtime，只读取稳定配置值。
 */
@Component
public class WorkspaceServerIdentity implements InitializingBean {

    @Value("${test-agent.workspace.server-id:${test-agent.opencode.manager-control.linux-server-id:127.0.0.1}}")
    private String linuxServerId;

    private String defaultDirectory;

    /**
     * Spring 容器无参构造，通过 @Value 注入 linuxServerId，通过 afterPropertiesSet 完成校验和默认目录设置。
     */
    public WorkspaceServerIdentity() {
    }

    /**
     * 单参数构造器，用于测试时指定服务器 ID，默认目录使用当前 Java 进程运行目录。
     */
    public WorkspaceServerIdentity(String linuxServerId) {
        this(linuxServerId, Path.of("").toAbsolutePath().normalize().toString());
    }

    /**
     * 双参数构造器，用于测试时同时固定服务器 ID 和默认目录。
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
     * Spring 容器注入后校验 server-id 非空并补充默认目录。
     */
    @Override
    public void afterPropertiesSet() {
        if (linuxServerId == null || linuxServerId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "工作空间服务器 ID 不能为空", Map.of());
        }
        this.linuxServerId = linuxServerId.trim();
        if (defaultDirectory == null || defaultDirectory.isBlank()) {
            this.defaultDirectory = Path.of("").toAbsolutePath().normalize().toString();
        } else {
            this.defaultDirectory = defaultDirectory.trim();
        }
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
