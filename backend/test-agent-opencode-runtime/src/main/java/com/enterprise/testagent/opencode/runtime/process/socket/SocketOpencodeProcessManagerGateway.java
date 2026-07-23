package com.enterprise.testagent.opencode.runtime.process.socket;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessControlCommand;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessControlResult;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessHealthCommand;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessHealthResult;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessManagerGateway;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessOwnedStopCommand;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartCommand;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartRejectionException;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartRejectionReason;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessStartResult;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

/**
 * 基于 manager WebSocket 长连接的生产网关，实现用户进程启动和健康检测命令分发。
 */
@Service
public class SocketOpencodeProcessManagerGateway implements OpencodeProcessManagerGateway {

    private static final String OPENCODE_UNAVAILABLE_ERROR_CODE = "OPENCODE_UNAVAILABLE";
    private static final String PROCESS_NOT_MANAGED_ERROR_CODE = "PROCESS_NOT_MANAGED";

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
        OpencodeContainerId containerId = command.containerId();
        int port;
        if (containerId != null) {
            // 公共 status/stop 携带调用前的权威坐标，不能在外部调用后按 processId 重路由到新 assignment。
            port = command.port();
        } else {
            OpencodeServerProcess process = repository.findOpencodeServerProcessById(command.processId())
                    .orElseThrow(() -> new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "TestAgent 进程不存在"));
            containerId = process.containerId();
            port = process.port();
        }
        ManagerControlMessage result = send(containerId, ManagerControlMessage.command(
                RuntimeIdGenerator.managerCommandId(),
                "health",
                port,
                settings.commandTimeout().toMillis(),
                command.traceId()));
        if ("HEALTHY".equals(result.status())) {
            if (result.pid() == null || result.pid() < 1) {
                throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程健康检测缺少受管 PID");
            }
            return OpencodeProcessHealthResult.healthy(result.pid(), result.message());
        }
        if ("UNHEALTHY".equals(result.status())) {
            if (result.pid() != null && result.pid() > 0) {
                return OpencodeProcessHealthResult.managedUnhealthy(result.pid(), result.message());
            }
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程健康检测缺少受管 PID");
        }
        if ("FAILED".equals(result.status())) {
            if (PROCESS_NOT_MANAGED_ERROR_CODE.equals(result.errorCode())) {
                return OpencodeProcessHealthResult.notRunning(result.message());
            }
            // 旧 manager 无码失败和内部错误都不能冒充“明确未托管”，否则会错误写入 STOPPED。
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程健康检测失败");
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
                command.configPath(),
                command.unifiedAuthId(),
                command.bindingRecovery(),
                command.environment(),
                settings.commandTimeout().toMillis(),
                command.traceId()));
        if (!"STARTED".equals(result.status())) {
            if (OPENCODE_UNAVAILABLE_ERROR_CODE.equals(result.errorCode())) {
                throw new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        safeMessage(result.message(), "TestAgent 服务不可用"));
            }
            var knownRejection = OpencodeProcessStartRejectionReason.fromManagerErrorCode(result.errorCode());
            if (knownRejection.isPresent()) {
                throw new OpencodeProcessStartRejectionException(knownRejection.get());
            }
            // 未知 manager 错误仍归类为 bad gateway，但不透传可能含 UCID/路径的原始消息。
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程启动失败");
        }
        return new OpencodeProcessStartResult(
                result.pid(),
                safeMessage(result.message(), "started"),
                result.processCreated());
    }

    @Override
    public OpencodeProcessControlResult restartProcess(OpencodeProcessControlCommand command) {
        return controlManagedProcess(command, "restart", "STARTED", "TestAgent 管理进程重启失败");
    }

    @Override
    public OpencodeProcessControlResult stopProcess(OpencodeProcessControlCommand command) {
        return controlManagedProcess(command, "stop", "STOPPED", "TestAgent 管理进程停止失败");
    }

    @Override
    public OpencodeProcessControlResult stopOwnedProcess(OpencodeProcessOwnedStopCommand command) {
        ManagerControlMessage result = send(command.containerId(), ManagerControlMessage.ownedStopCommand(
                RuntimeIdGenerator.managerCommandId(),
                command.port(),
                command.expectedUnifiedAuthId(),
                command.expectedPid(),
                settings.commandTimeout().toMillis(),
                command.traceId()));
        if (!"STOPPED".equals(result.status())) {
            // 旧 manager unknown-command 与所有 ownership mismatch 均 fail closed，绝不降级为仅端口 stop。
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程实例校验停止失败");
        }
        return new OpencodeProcessControlResult(
                "stopOwned",
                result.status(),
                result.port() == null ? command.port() : result.port(),
                result.pid(),
                result.baseUrl(),
                result.sessionPath(),
                result.configPath(),
                result.healthy(),
                safeMessage(result.message(), "stopped"),
                result.traceId());
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
            com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId containerId,
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
