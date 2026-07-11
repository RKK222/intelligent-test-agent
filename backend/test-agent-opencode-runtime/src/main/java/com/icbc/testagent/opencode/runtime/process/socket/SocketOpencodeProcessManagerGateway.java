package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.id.RuntimeIdGenerator;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessControlCommand;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessControlResult;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessHealthCommand;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessHealthResult;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessManagerGateway;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStartCommand;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStartResult;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

/**
 * 基于 manager WebSocket 长连接的生产网关，实现用户进程启动和健康检测命令分发。
 */
@Service
public class SocketOpencodeProcessManagerGateway implements OpencodeProcessManagerGateway {

    private static final String OPENCODE_UNAVAILABLE_ERROR_CODE = "OPENCODE_UNAVAILABLE";

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
                .orElseThrow(() -> new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "TestAgent 进程不存在"));
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
        throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程健康检测响应异常");
    }

    @Override
    public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) {
        ManagerControlMessage result = send(command.containerId(), ManagerControlMessage.command(
                RuntimeIdGenerator.managerCommandId(),
                "start",
                command.port(),
                command.sessionPath(),
                command.environment(),
                settings.commandTimeout().toMillis(),
                command.traceId()));
        if (!"STARTED".equals(result.status())) {
            if (OPENCODE_UNAVAILABLE_ERROR_CODE.equals(result.errorCode())) {
                throw new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        safeMessage(result.message(), "TestAgent 服务不可用"));
            }
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, safeMessage(result.message(), "TestAgent 管理进程启动失败"));
        }
        return new OpencodeProcessStartResult(result.pid(), safeMessage(result.message(), "started"));
    }

    @Override
    public OpencodeProcessControlResult restartProcess(OpencodeProcessControlCommand command) {
        return controlManagedProcess(command, "restart", "STARTED", "TestAgent 管理进程重启失败");
    }

    @Override
    public OpencodeProcessControlResult stopProcess(OpencodeProcessControlCommand command) {
        return controlManagedProcess(command, "stop", "STOPPED", "TestAgent 管理进程停止失败");
    }

    private OpencodeProcessControlResult controlManagedProcess(
            OpencodeProcessControlCommand command,
            String managerCommand,
            String expectedStatus,
            String failureMessage) {
        ManagerControlMessage result = send(command.containerId(), ManagerControlMessage.command(
                RuntimeIdGenerator.managerCommandId(),
                managerCommand,
                command.port(),
                settings.commandTimeout().toMillis(),
                command.traceId()));
        if (!expectedStatus.equals(result.status())) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, safeMessage(result.message(), failureMessage));
        }
        return new OpencodeProcessControlResult(
                managerCommand,
                result.status(),
                result.port() == null ? command.port() : result.port(),
                result.pid(),
                result.baseUrl(),
                result.sessionPath(),
                result.configPath(),
                result.healthy(),
                safeMessage(result.message(), expectedStatus.toLowerCase()),
                result.traceId());
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
