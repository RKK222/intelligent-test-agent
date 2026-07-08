package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.domain.opencodeprocess.UserOpencodeProcessBinding;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 运行管理页面的管理进程命令服务，只按容器和端口转发控制命令。
 */
@Service
public class RuntimeManagementCommandService {

    private static final int PROCESS_SCAN_LIMIT = 200;

    private final OpencodeProcessManagerGateway gateway;
    private final OpencodeProcessManagementRepository repository;
    private final OpencodeProcessStartupService startupService;
    private final OpencodeProcessStopService stopService;

    /**
     * 注入管理进程控制面网关；服务层不直接持有 WebSocket 连接细节。
     */
    public RuntimeManagementCommandService(OpencodeProcessManagerGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.repository = null;
        this.startupService = null;
        this.stopService = new OpencodeProcessStopService(gateway);
    }

    /**
     * 注入控制面网关、进程仓储、公共启动服务和公共停止服务。
     */
    @Autowired
    public RuntimeManagementCommandService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            OpencodeProcessStartupService startupService,
            OpencodeProcessStopService stopService) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.startupService = Objects.requireNonNull(startupService, "startupService must not be null");
        this.stopService = Objects.requireNonNull(stopService, "stopService must not be null");
    }

    /**
     * 重启指定容器端口上的 opencode server。
     */
    public OpencodeProcessControlResult restartManagedProcess(OpencodeContainerId containerId, int port, String traceId) {
        Optional<OpencodeServerProcess> process = latestProcess(containerId, port);
        if (process.isPresent() && process.get().status() == OpencodeServerProcessStatus.STOPPED) {
            return startExistingProcess(process.get(), traceId);
        }
        try {
            OpencodeProcessControlResult result = gateway.restartProcess(new OpencodeProcessControlCommand(containerId, port, traceId));
            if (result == null) {
                throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "opencode 管理进程重启未返回结果");
            }
            return process.map(item -> verifyRestartedProcess(item, result, traceId)).orElse(result);
        } catch (PlatformException exception) {
            if (process.isPresent() && notManaged(exception)) {
                return startExistingProcess(process.get(), traceId);
            }
            throw exception;
        }
    }

    /**
     * 停止指定容器端口上的 opencode server。
     */
    public OpencodeProcessControlResult stopManagedProcess(OpencodeContainerId containerId, int port, String traceId) {
        return latestProcess(containerId, port)
                .map(process -> stopService.stopAndVerify(OpencodeProcessStopRequest.tracked(process, traceId)))
                .orElseGet(() -> stopService.stopAndVerify(OpencodeProcessStopRequest.untracked(containerId, port, traceId)));
    }

    private Optional<OpencodeServerProcess> latestProcess(OpencodeContainerId containerId, int port) {
        if (repository == null) {
            return Optional.empty();
        }
        return repository.findOpencodeServerProcesses(
                        new OpencodeServerProcessFilter(null, null, containerId, null),
                        new PageRequest(1, PROCESS_SCAN_LIMIT))
                .items()
                .stream()
                .filter(process -> process.port() == port)
                .max(Comparator.comparing(OpencodeServerProcess::updatedAt));
    }

    private OpencodeProcessControlResult verifyRestartedProcess(
            OpencodeServerProcess process,
            OpencodeProcessControlResult result,
            String traceId) {
        if (startupService == null) {
            return result;
        }
        OpencodeProcessStartupRequest request = startupRequest(process, traceId);
        OpencodeServerProcess running = startupService.markStartedAndVerify(
                request,
                result.pid() == null ? process.pid() : result.pid(),
                result.message());
        return controlResult(running, traceId);
    }

    private OpencodeProcessControlResult startExistingProcess(OpencodeServerProcess process, String traceId) {
        if (startupService == null) {
            return gateway.restartProcess(new OpencodeProcessControlCommand(process.containerId(), process.port(), traceId));
        }
        OpencodeServerProcess running = startupService.startAndVerify(startupRequest(process, traceId));
        return controlResult(running, traceId);
    }

    private OpencodeProcessStartupRequest startupRequest(OpencodeServerProcess process, String traceId) {
        return new OpencodeProcessStartupRequest(
                process.userId(),
                process.processId(),
                process.createdAt(),
                bindingCreatedAt(process).orElse(null),
                process.linuxServerId(),
                process.containerId(),
                process.port(),
                process.baseUrl(),
                process.sessionPath(),
                process.configPath(),
                Map.of(),
                traceId);
    }

    private Optional<Instant> bindingCreatedAt(OpencodeServerProcess process) {
        if (repository == null) {
            return Optional.empty();
        }
        UserOpencodeProcessBinding binding = repository.findUserBindingsByProcessIds(List.of(process.processId()))
                .get(process.processId());
        return binding == null ? Optional.empty() : Optional.of(binding.createdAt());
    }

    private OpencodeProcessControlResult controlResult(OpencodeServerProcess process, String traceId) {
        return new OpencodeProcessControlResult(
                "restart",
                "STARTED",
                process.port(),
                process.pid(),
                process.baseUrl(),
                process.sessionPath(),
                process.configPath(),
                true,
                process.healthMessage(),
                traceId);
    }

    private boolean notManaged(PlatformException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        return message.contains("not managed");
    }
}
