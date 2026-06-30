package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 公共 opencode server 停止服务，统一执行 manager stop、停止后 health 确认和进程快照回写。
 *
 * <p>所有停止入口都应调用本服务，避免只信任 manager `STOPPED` 回包而没有确认
 * opencode server 已不可达。
 */
@Service
public class OpencodeProcessStopService {

    private final OpencodeProcessManagerGateway gateway;
    private final OpencodeProcessManagementRepository repository;
    private final Clock clock;

    /**
     * Spring 生产构造器使用系统 UTC 时钟。
     */
    @Autowired
    public OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository) {
        this(gateway, repository, Clock.systemUTC());
    }

    /**
     * 无平台进程记录时仍可复用本服务向 manager 下发 stop。
     */
    public OpencodeProcessStopService(OpencodeProcessManagerGateway gateway) {
        this(gateway, null, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟，保证停止后快照时间稳定。
     */
    OpencodeProcessStopService(
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessManagementRepository repository,
            Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.repository = repository;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 停止指定 opencode server；tracked 请求必须确认 health 不健康才返回成功。
     */
    public OpencodeProcessControlResult stopAndVerify(OpencodeProcessStopRequest request) {
        OpencodeProcessControlResult stopped = gateway.stopProcess(new OpencodeProcessControlCommand(
                request.containerId(),
                request.port(),
                request.traceId()));
        if (stopped == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "opencode 管理进程停止未返回结果");
        }
        if (!"STOPPED".equals(stopped.status())) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "opencode 管理进程停止响应异常");
        }
        if (!request.tracked()) {
            return stopped;
        }
        OpencodeServerProcess process = trackedProcess(request);
        OpencodeProcessHealthResult health = gateway.checkHealth(new OpencodeProcessHealthCommand(
                request.processId(),
                request.baseUrl(),
                request.traceId()));
        if (health == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "opencode 停止后健康检测未返回结果");
        }
        if (health.healthy()) {
            throw new PlatformException(
                    ErrorCode.OPENCODE_BAD_GATEWAY,
                    "opencode 进程停止后仍然健康",
                    Map.of("processId", process.processId().value(), "port", process.port()));
        }
        OpencodeServerProcess stoppedProcess = refreshStoppedProcess(
                process,
                health.message(),
                Instant.now(clock),
                request.traceId());
        repository.saveOpencodeServerProcess(stoppedProcess);
        return new OpencodeProcessControlResult(
                "stop",
                "STOPPED",
                stoppedProcess.port(),
                null,
                stoppedProcess.baseUrl(),
                stoppedProcess.sessionPath(),
                stoppedProcess.configPath(),
                false,
                health.message(),
                request.traceId());
    }

    private OpencodeServerProcess trackedProcess(OpencodeProcessStopRequest request) {
        if (repository == null) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "opencode 停止缺少进程仓储");
        }
        return repository.findOpencodeServerProcessById(request.processId())
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.OPENCODE_UNAVAILABLE,
                        "opencode 进程不存在",
                        Map.of("processId", request.processId().value(), "port", request.port())));
    }

    private OpencodeServerProcess refreshStoppedProcess(
            OpencodeServerProcess process,
            String healthMessage,
            Instant now,
            String traceId) {
        return new OpencodeServerProcess(
                process.processId(),
                process.userId(),
                process.linuxServerId(),
                process.containerId(),
                process.port(),
                null,
                process.baseUrl(),
                OpencodeServerProcessStatus.STOPPED,
                process.sessionPath(),
                process.configPath(),
                process.startedAt(),
                now,
                healthMessage,
                process.createdAt(),
                now,
                traceId);
    }
}
