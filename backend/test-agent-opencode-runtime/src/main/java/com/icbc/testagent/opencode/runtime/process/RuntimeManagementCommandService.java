package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 运行管理页面的管理进程命令服务，只按容器和端口转发控制命令。
 */
@Service
public class RuntimeManagementCommandService {

    private final OpencodeProcessManagerGateway gateway;

    /**
     * 注入管理进程控制面网关；服务层不直接持有 WebSocket 连接细节。
     */
    public RuntimeManagementCommandService(OpencodeProcessManagerGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
    }

    /**
     * 重启指定容器端口上的 opencode server。
     */
    public OpencodeProcessControlResult restartManagedProcess(OpencodeContainerId containerId, int port, String traceId) {
        return gateway.restartProcess(new OpencodeProcessControlCommand(containerId, port, traceId));
    }

    /**
     * 停止指定容器端口上的 opencode server。
     */
    public OpencodeProcessControlResult stopManagedProcess(OpencodeContainerId containerId, int port, String traceId) {
        return gateway.stopProcess(new OpencodeProcessControlCommand(containerId, port, traceId));
    }
}
