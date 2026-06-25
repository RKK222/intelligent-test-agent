package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendProcessId;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 当前后端实例内存中的 manager WebSocket 连接注册表，按 containerId 路由控制命令。
 */
@Component
public class ManagerConnectionRegistry {

    private final ConcurrentMap<OpencodeContainerId, ManagerConnection> connections = new ConcurrentHashMap<>();

    /**
     * 注册或覆盖当前容器的活动 manager 连接。
     */
    public void register(
            ContainerManagerId managerId,
            OpencodeContainerId containerId,
            BackendProcessId backendProcessId,
            ManagerCommandSender sender) {
        connections.put(
                Objects.requireNonNull(containerId, "containerId must not be null"),
                new ManagerConnection(managerId, containerId, backendProcessId, sender));
    }

    /**
     * 移除当前容器连接，断线后后续命令会返回不可用。
     */
    public void disconnect(OpencodeContainerId containerId) {
        connections.remove(containerId);
    }

    /**
     * 向指定容器发送命令；无连接时转换为 opencode 不可用。
     */
    public void send(OpencodeContainerId containerId, ManagerControlMessage message) {
        ManagerConnection connection = connections.get(containerId);
        if (connection == null) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "opencode 管理进程未连接");
        }
        connection.sender().send(message);
    }

    /**
     * 返回指定容器是否已有当前实例可用连接。
     */
    public boolean isConnected(OpencodeContainerId containerId) {
        return connections.containsKey(containerId);
    }

    private record ManagerConnection(
            ContainerManagerId managerId,
            OpencodeContainerId containerId,
            BackendProcessId backendProcessId,
            ManagerCommandSender sender) {

        private ManagerConnection {
            Objects.requireNonNull(managerId, "managerId must not be null");
            Objects.requireNonNull(containerId, "containerId must not be null");
            Objects.requireNonNull(backendProcessId, "backendProcessId must not be null");
            Objects.requireNonNull(sender, "sender must not be null");
        }
    }
}
