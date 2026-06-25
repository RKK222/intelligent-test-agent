package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessHealthCommand;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessHealthResult;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessManagerGateway;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStartCommand;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStartResult;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 基于 manager WebSocket 长连接的生产网关，实现用户进程启动和健康检测命令分发。
 *
 * <p>仅在 {@code test-agent.opencode.manager-control.gateway-mode=socket}（默认）时启用；
 * 本地开发可改用 {@link LocalOpencodeProcessManagerGateway} 让控制面命令直接打 opencode
 * server 的 baseUrl，绕开 manager WebSocket 依赖。
 */
@Service
@ConditionalOnProperty(name = "test-agent.opencode.manager-control.gateway-mode", havingValue = "socket", matchIfMissing = true)
public class SocketOpencodeProcessManagerGateway implements OpencodeProcessManagerGateway {

    private final OpencodeProcessManagementRepository repository;
    private final ManagerConnectionRegistry connections;
    private final ManagerPendingCommandRegistry pendingCommands;
    private final ManagerControlSettings settings;

    /**
     * 注入进程管理 repository、连接注册表和控制面配置。
     */
    public SocketOpencodeProcessManagerGateway(
            OpencodeProcessManagementRepository repository,
            ManagerConnectionRegistry connections,
            ManagerPendingCommandRegistry pendingCommands,
            ManagerControlSettings settings) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.connections = Objects.requireNonNull(connections, "connections must not be null");
        this.pendingCommands = Objects.requireNonNull(pendingCommands, "pendingCommands must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    @Override
    public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
        OpencodeServerProcess process = repository.findOpencodeServerProcessById(command.processId())
                .orElseThrow(() -> new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "opencode 进程不存在"));
        ManagerControlMessage result = send(process.containerId(), ManagerControlMessage.command(
                RuntimeIdGenerator.managerCommandId(),
                "health",
                process.port(),
                settings.commandTimeout().toMillis(),
                command.traceId()));
        if ("HEALTHY".equals(result.status())) {
            return OpencodeProcessHealthResult.healthy(result.message());
        }
        if ("UNHEALTHY".equals(result.status()) || "FAILED".equals(result.status())) {
            return OpencodeProcessHealthResult.unhealthy(result.message());
        }
        throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "opencode 管理进程健康检测响应异常");
    }

    @Override
    public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) {
        ManagerControlMessage result = send(command.containerId(), ManagerControlMessage.command(
                RuntimeIdGenerator.managerCommandId(),
                "start",
                command.port(),
                settings.commandTimeout().toMillis(),
                command.traceId()));
        if (!"STARTED".equals(result.status())) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, safeMessage(result.message(), "opencode 管理进程启动失败"));
        }
        return new OpencodeProcessStartResult(result.pid(), safeMessage(result.message(), "started"));
    }

    private ManagerControlMessage send(
            com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId containerId,
            ManagerControlMessage message) {
        CompletableFuture<ManagerControlMessage> pending = pendingCommands.create(message.commandId());
        try {
            connections.send(containerId, message);
            return pendingCommands.await(message.commandId(), pending, settings.commandTimeout());
        } catch (RuntimeException exception) {
            pendingCommands.cancel(message.commandId());
            throw exception;
        }
    }

    private String safeMessage(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
